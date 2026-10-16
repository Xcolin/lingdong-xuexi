import {render,screen,fireEvent,waitFor,within} from '@testing-library/react';
import {beforeEach,it,expect,vi} from 'vitest';
import {UserRoleAssociationModal} from './UserRoleAssociationModal';
import {usersApi} from '../../api/users';
vi.mock('../../api/users',()=>({usersApi:{roleAssociations:vi.fn(),addRoles:vi.fn(),removeRoles:vi.fn()}}));
const context={roles:[{id:'new',code:'CUSTOM',name:'自定义角色',dataScope:'ALL',status:'ENABLED'},{id:'old',code:'OLD',name:'已关联角色甲',dataScope:'SCHOOL',status:'ENABLED'},{id:'scoped',code:'SCHOOL_ROLE',name:'学校角色',dataScope:'SCHOOL',status:'ENABLED'}],assignments:[{roleId:'old',organizationId:'school',organizationName:'学校甲',removable:true}],organizations:[{id:'school',parentId:null,name:'学校甲',typeCode:'SCHOOL',disabled:false}],globalAssignable:true};
beforeEach(()=>{vi.clearAllMocks();vi.mocked(usersApi.roleAssociations).mockResolvedValue(context);vi.mocked(usersApi.addRoles).mockResolvedValue(undefined);vi.mocked(usersApi.removeRoles).mockResolvedValue(undefined);});
const props={user:{id:'u',displayName:'用户甲'} as any,onClose:vi.fn(),onChanged:vi.fn()};
it('左右展示未关联已关联角色，加入全局角色使用全局授权',async()=>{
 render(<UserRoleAssociationModal {...props}/>);await screen.findByText('自定义角色');const left=screen.getByRole('region',{name:'未关联角色'});const row=within(left).getByText('自定义角色').closest('tr')!;fireEvent.click(within(row).getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'加入所选'}));await waitFor(()=>expect(usersApi.addRoles).toHaveBeenCalledWith('u',[{roleId:'new',organizationId:null}]));
});
it('移除使用原授权组织，学校角色未选择组织不可新增',async()=>{
 render(<UserRoleAssociationModal {...props}/>);await screen.findByText('自定义角色');const left=screen.getByRole('region',{name:'未关联角色'});expect(within(within(left).getByText('学校角色').closest('tr')!).getByRole('checkbox')).toBeDisabled();const right=screen.getByRole('region',{name:'已关联角色'});fireEvent.click(within(within(right).getByText('已关联角色甲').closest('tr')!).getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'移除所选'}));await waitFor(()=>expect(usersApi.removeRoles).toHaveBeenCalledWith('u',[{roleId:'old',organizationId:'school'}]));
});
it('移除失败保留关联并显示具体原因',async()=>{
 vi.mocked(usersApi.removeRoles).mockRejectedValue(new Error('请先取消对应组织的管理员身份，再移除该角色'));render(<UserRoleAssociationModal {...props}/>);const right=screen.getByRole('region',{name:'已关联角色'});await within(right).findByText('已关联角色甲');fireEvent.click(within(within(right).getByText('已关联角色甲').closest('tr')!).getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'移除所选'}));await screen.findByText('请先取消对应组织的管理员身份，再移除该角色');expect(within(right).getByText('已关联角色甲')).toBeInTheDocument();
});
