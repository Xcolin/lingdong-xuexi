import {render,screen,fireEvent,waitFor} from '@testing-library/react';import {it,expect,vi} from 'vitest';import {OrganizationMembersDrawer} from './OrganizationMembersDrawer';import {organizationApi} from '../../api/organization';
vi.mock('../../api/organization',()=>({organizationApi:{members:vi.fn(),associateMembers:vi.fn(),listTree:vi.fn()}}));
it('组织成员管理员回显且设为管理员绑定当前组织',async()=>{vi.mocked(organizationApi.members).mockResolvedValue({items:[{id:'u',username:'user',displayName:'成员用户',status:'ENABLED',administrator:false},{id:'a',username:'admin',displayName:'现有管理员',status:'ENABLED',administrator:true}],page:1,pageSize:20,total:2} as any);render(<OrganizationMembersDrawer permissionCodes={['ORG_MEMBER_READ','ORG_ADMIN_ASSIGN','ORG_ADMIN_REVOKE','ORG_MEMBER_ASSOCIATE','ORG_MEMBER_REMOVE']} organization={{id:'org',name:'学校',status:'ENABLED',effectiveStatus:'ENABLED'} as any} onClose={()=>{}}/>);await screen.findByText('现有管理员');fireEvent.click(screen.getByRole('button',{name:'设为管理员-成员用户'}));await waitFor(()=>expect(organizationApi.associateMembers).toHaveBeenCalledWith('org',['u'],true));expect(screen.getAllByText('组织管理员')).toHaveLength(1);});

vi.mock('antd',async()=>{const actual=await vi.importActual<typeof import('antd')>('antd');return {...actual,message:{...actual.message,success:vi.fn(),error:vi.fn()}};});

it('只授成员读取不显示管理员和关联写按钮',async()=>{
 vi.mocked(organizationApi.members).mockResolvedValue({items:[{id:'u',username:'user',displayName:'只读成员',status:'ENABLED',administrator:false}],page:1,pageSize:20,total:1} as any);
 render(<OrganizationMembersDrawer organization={{id:'read-org',name:'学校',status:'ENABLED',effectiveStatus:'ENABLED'} as any} permissionCodes={['ORG_MEMBER_READ']} onClose={()=>{}}/>);
 await screen.findByText('只读成员');
 expect(screen.queryByRole('button',{name:'设为管理员-只读成员'})).toBeNull();
 expect(screen.queryByRole('button',{name:'关联已有用户'})).toBeNull();
});

it('当前负责人不能取消关联或管理员，转交后的原负责人允许操作',async()=>{
 const old={id:'old',username:'old',displayName:'原负责人',status:'ENABLED',administrator:true,owner:false};
 vi.mocked(organizationApi.members).mockResolvedValue({items:[{id:'new',username:'new',displayName:'当前负责人',status:'ENABLED',administrator:true,owner:true},old],page:1,pageSize:20,total:2} as any);
 render(<OrganizationMembersDrawer permissionCodes={['ORG_MEMBER_READ','ORG_ADMIN_REVOKE','ORG_MEMBER_REMOVE']} organization={{id:'transfer-org',name:'学校',status:'ENABLED',effectiveStatus:'ENABLED'} as any} onClose={()=>{}}/>);
 await screen.findByText('当前负责人');
 expect(screen.getByRole('button',{name:'取消管理员-当前负责人'})).toBeDisabled();
 expect(screen.getByRole('button',{name:'取消关联-当前负责人'})).toBeDisabled();
 expect(screen.getByRole('button',{name:'取消管理员-原负责人'})).toBeEnabled();
 expect(screen.getByRole('button',{name:'取消关联-原负责人'})).toBeEnabled();
 expect(screen.getByText('负责人')).toBeInTheDocument();
});
