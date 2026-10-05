import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {it,expect,vi} from 'vitest';
import {UserPermissionTreeModal} from './UserPermissionTreeModal';
const api=vi.hoisted(()=>({userPermissionTree:vi.fn(),listUserPermissions:vi.fn(),listPermissions:vi.fn(),batchUserPermissions:vi.fn()}));
vi.mock('../../api/iam',()=>({iamApi:api}));
it('固定用户继承只读，额外按钮级联统一提交，DENY保留',async()=>{
 api.userPermissionTree.mockResolvedValue({menus:[{id:'p',name:'页面',type:'PAGE',parentId:null,permissionCode:'READ',grantable:true,sortOrder:0},{id:'b',name:'编辑',type:'BUTTON',parentId:'p',permissionCode:'EDIT',grantable:true,sortOrder:1}],inheritedPermissionIds:['read'],inheritedDeniedPermissionIds:[]});
 api.listPermissions.mockResolvedValue([{id:'read',code:'READ',name:'查看',status:'ENABLED'},{id:'edit',code:'EDIT',name:'编辑',status:'ENABLED'},{id:'deny',code:'DENY',name:'禁止删除',status:'ENABLED'}]);api.listUserPermissions.mockResolvedValue([{permissionId:'deny',effect:'DENY'}]);
 render(<UserPermissionTreeModal open user={{id:'u',username:'test',displayName:'测试用户'} as any} onClose={()=>{}}/>);
 const title=await screen.findByText('页面（角色继承）');fireEvent.click(title.closest('.ant-tree-treenode')!.querySelector('.ant-tree-checkbox')!);
 fireEvent.click(screen.getByRole('button',{name:'保存用户权限'}));await waitFor(()=>expect(api.batchUserPermissions).toHaveBeenCalledWith('u',{permissionIds:['edit'],managedPermissionIds:['edit'],expectedAssignments:[{permissionId:'deny',effect:'DENY'}]}));
});

vi.mock('antd',async()=>{const actual=await vi.importActual<typeof import('antd')>('antd');return {...actual,message:{...actual.message,success:vi.fn(),error:vi.fn()}};});
