vi.mock('../../api/users',()=>({usersApi:{scopeOrganizations:vi.fn(async()=>{const flatten=(nodes:any[]):any[]=>nodes.flatMap(n=>[{...n,parentId:n.parentId??null,disabled:n.status==='DISABLED'||n.effectiveStatus==='DISABLED'},...flatten(n.children??[])]);return flatten(await organizationApi.listTree());})}}));
import {render,screen,fireEvent,waitFor,within} from '@testing-library/react';
import {beforeEach,it,expect,vi} from 'vitest';
import userEvent from '@testing-library/user-event';
import {UserPickerModal} from './UserPickerModal';
import {organizationApi} from '../../api/organization';
import {iamApi} from '../../api/iam';
vi.mock('../../api/organization',()=>({organizationApi:{listTree:vi.fn(),memberPicker:vi.fn()}}));
vi.mock('../../api/iam',()=>({iamApi:{roleUserPicker:vi.fn()}}));
const candidate={id:'new',username:'new',displayName:'待加入老师',status:'ENABLED',organizationNames:['示例学校 / 东校区 / 三年级']};
const linked={id:'old',username:'old',displayName:'已加入老师',status:'ENABLED',organizationNames:['示例学校 / 西校区']};
const page=(items:any[],current=1,total=items.length)=>({items,page:current,pageSize:20,total});
beforeEach(()=>{vi.clearAllMocks();vi.mocked(organizationApi.listTree).mockResolvedValue([]);vi.mocked(organizationApi.memberPicker).mockImplementation(async(_id,q)=>page(q.side==='CANDIDATES'?[candidate]:[linked],q.page,45) as any);});
it('左右名单独立分页，批量加入成功刷新两侧且保留窗口',async()=>{
 const submit=vi.fn().mockResolvedValue(undefined);const close=vi.fn();render(<UserPickerModal open title="关联用户" actionPrefix="organizations.member-picker" targetOrganizationId="org" onClose={close} onSubmit={submit} onRemove={async()=>{}}/>);
 const left=await screen.findByRole('region',{name:'待加入'});const right=screen.getByRole('region',{name:'已加入'});await within(left).findByText('待加入老师');await within(right).findByText('已加入老师');expect(within(left).getByText('三年级')).toBeInTheDocument();
 fireEvent.click(within(left).getAllByRole('checkbox')[1]);fireEvent.click(screen.getByRole('button',{name:'加入所选'}));await waitFor(()=>expect(submit).toHaveBeenCalledWith(['new'],null));expect(close).not.toHaveBeenCalled();
 fireEvent.click(within(left).getByTitle('2'));await waitFor(()=>expect(organizationApi.memberPicker).toHaveBeenCalledWith('org',expect.objectContaining({side:'CANDIDATES',page:2})));expect(within(right).getByTitle('1')).toHaveAttribute('class',expect.stringContaining('active'));
});
it('批量移除失败保留右侧选择和错误',async()=>{
 const remove=vi.fn().mockRejectedValue(new Error('整批失败'));render(<UserPickerModal open title="管理员" actionPrefix="organizations.admin-picker" targetOrganizationId="org" administratorMode onClose={()=>{}} onSubmit={async()=>{}} onRemove={remove}/>);
 const right=await screen.findByRole('region',{name:'已加入'});await within(right).findByText('已加入老师');fireEvent.click(within(right).getAllByRole('checkbox')[1]);fireEvent.click(screen.getByRole('button',{name:'移除所选'}));await screen.findByText('整批失败');expect(within(right).getAllByRole('checkbox')[1]).toBeChecked();expect(remove).toHaveBeenCalledWith([expect.objectContaining({id:'old'})]);
});
it('组织筛选只改变filterOrganizationId并重置分页，授权组织独立',async()=>{
 const actor=userEvent.setup();vi.mocked(organizationApi.listTree).mockResolvedValue([{id:'school',name:'示例学校',status:'ENABLED',effectiveStatus:'ENABLED',sortOrder:1,children:[{id:'campus',name:'东校区',namePath:'示例学校·东校区',status:'ENABLED',effectiveStatus:'ENABLED',sortOrder:1,children:[]}]}] as any);vi.mocked(iamApi.roleUserPicker).mockImplementation(async(_id,q)=>page(q.side==='CANDIDATES'?[candidate]:[],q.page) as any);
 render(<UserPickerModal open title="关联角色" actionPrefix="iam.role-users" roleId="role" requireOrganization onClose={()=>{}} onSubmit={async()=>{}} onRemove={async()=>{}}/>);
 await actor.click(screen.getByRole('combobox',{name:'授权组织'}));await actor.click(await screen.findByText('示例学校'));await screen.findByText('待加入老师');await actor.click(screen.getByRole('combobox',{name:'筛选组织（含下级）'}));await actor.click((await screen.findAllByText('东校区')).at(-1)!);await waitFor(()=>expect(iamApi.roleUserPicker).toHaveBeenCalledWith('role',expect.objectContaining({grantOrganizationId:'school',filterOrganizationId:'campus',page:1})));
});
it('同一用户多个角色授权拆成具体范围，移除仅提交选中的授权',async()=>{
 vi.mocked(iamApi.roleUserPicker).mockImplementation(async(_id,q)=>page(q.side==='LINKED'?[{...linked,assignments:[{userId:'old',organizationId:'s1'},{userId:'old',organizationId:'s2'}]}]:[]) as any);const remove=vi.fn().mockResolvedValue(undefined);
 render(<UserPickerModal open title="角色" actionPrefix="iam.role-users" roleId="role" onClose={()=>{}} onSubmit={async()=>{}} onRemove={remove}/>);const right=screen.getByRole('region',{name:'已加入'});await waitFor(()=>expect(within(right).getAllByText('已加入老师')).toHaveLength(2));fireEvent.click(within(right).getAllByRole('checkbox')[1]);fireEvent.click(screen.getByRole('button',{name:'移除所选'}));await waitFor(()=>expect(remove).toHaveBeenCalledWith([expect.objectContaining({assignments:[{userId:'old',organizationId:'s1'}]})]));
});
it('已关联加载失败时不能执行批量关联，避免使用不完整关系快照',async()=>{
 vi.mocked(organizationApi.memberPicker).mockImplementation(async(_id,q)=>{if(q.side==='LINKED')throw new Error('已加入读取失败');return page([candidate]) as any;});render(<UserPickerModal open title="组织用户" actionPrefix="organizations.member-picker" targetOrganizationId="org" onClose={()=>{}} onSubmit={async()=>{}} onRemove={async()=>{}}/>);await screen.findByText('已加入读取失败');expect(screen.getByRole('button',{name:'加入所选'})).toBeDisabled();expect(screen.getByRole('button',{name:'移除所选'})).toBeDisabled();
});

it('组织用户没有同校或未归属分组条件，查询固定学校分组',async()=>{
 render(<UserPickerModal open title="学校用户" actionPrefix="organizations.member-picker" targetOrganizationId="school" onClose={()=>{}} onSubmit={async()=>{}} onRemove={async()=>{}}/>);
 await screen.findByText('待加入老师');expect(screen.queryByRole('combobox',{name:'候选用户分组'})).not.toBeInTheDocument();
 expect(organizationApi.memberPicker).toHaveBeenCalledWith('school',expect.objectContaining({group:'SCHOOL',side:'CANDIDATES'}));
});

it('范围角色未选择新增授权组织时也可查看并移除已有范围，新增仍禁用',async()=>{
 vi.mocked(iamApi.roleUserPicker).mockImplementation(async(_id,q)=>page(q.side==='LINKED'?[{...linked,assignments:[{userId:'old',organizationId:'original-school'}]}]:[]) as any);const remove=vi.fn().mockResolvedValue(undefined);render(<UserPickerModal open title="范围角色" actionPrefix="iam.role-users" roleId="role" requireOrganization onClose={()=>{}} onSubmit={async()=>{}} onRemove={remove}/>);const right=screen.getByRole('region',{name:'已加入'});await within(right).findByText('已加入老师');expect(screen.getByRole('button',{name:'加入所选'})).toBeDisabled();fireEvent.click(within(right).getAllByRole('checkbox')[1]);fireEvent.click(screen.getByRole('button',{name:'移除所选'}));await waitFor(()=>expect(remove).toHaveBeenCalledWith([expect.objectContaining({assignments:[{userId:'old',organizationId:'original-school'}]})]));expect(iamApi.roleUserPicker).toHaveBeenCalledWith('role',expect.objectContaining({side:'LINKED',grantOrganizationId:undefined}));
});
it('同页超过二十个具体授权范围全部可见，不被用户分页截断',async()=>{
 const assignments=Array.from({length:25},(_,index)=>({userId:'old',organizationId:`scope-${index}`}));vi.mocked(iamApi.roleUserPicker).mockImplementation(async(_id,q)=>page(q.side==='LINKED'?[{...linked,assignments}]:[]) as any);render(<UserPickerModal open title="多范围角色" actionPrefix="iam.role-users" roleId="role" onClose={()=>{}} onSubmit={async()=>{}} onRemove={async()=>{}}/>);const right=screen.getByRole('region',{name:'已加入'});await within(right).findByText('scope-24');expect(within(right).getAllByText('已加入老师')).toHaveLength(25);
});

it('裁剪组织树保留同校已授权校区，排除其他学校校区',async()=>{
 const node=(id:string,name:string,parentId:string)=>({id,name,parentId,typeCode:'CAMPUS',sortOrder:0,status:'ENABLED',effectiveStatus:'ENABLED',children:[]});
 vi.mocked(organizationApi.listTree).mockResolvedValue([node('east','本校东校区','school-a'),node('west','本校西校区','school-a'),node('foreign','外校校区','school-b')] as any);
 render(<UserPickerModal open title="学校用户" actionPrefix="organizations.member-picker" targetOrganizationId="east" onClose={()=>{}} onSubmit={async()=>{}} onRemove={async()=>{}}/>);
 await screen.findByText('待加入老师');const actor=userEvent.setup();await actor.click(screen.getByRole('combobox',{name:'筛选组织（含下级）'}));expect(await screen.findByText('本校西校区')).toBeInTheDocument();expect(screen.queryByText('外校校区')).not.toBeInTheDocument();
});
