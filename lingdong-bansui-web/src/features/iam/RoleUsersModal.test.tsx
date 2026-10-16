import {render,screen,fireEvent,waitFor,within} from '@testing-library/react';
import {it,expect,vi} from 'vitest';
import {RoleUsersModal} from './RoleUsersModal';
import {organizationApi} from '../../api/organization';
import {iamApi} from '../../api/iam';
vi.mock('../../api/organization',()=>({organizationApi:{listTree:vi.fn().mockResolvedValue([])}}));
vi.mock('../../api/iam',()=>({iamApi:{roleUserPicker:vi.fn(),assignRoleUsers:vi.fn(),revokeRoleUsers:vi.fn()}}));
it('角色用户取消一个具体授权保留同用户另一个学校授权',async()=>{
 vi.mocked(organizationApi.listTree).mockResolvedValue([]);
 const userId='2098888888888888888';const a='2097777777777777777';const b='2096666666666666666';
 vi.mocked(iamApi.roleUserPicker).mockImplementation(async(_id,query)=>({items:query.side==='LINKED'?[{id:userId,username:'teacher',displayName:'跨校教师',status:'ENABLED',organizationNames:['甲校 / 东校区','乙校'],assignments:[{userId,organizationId:a},{userId,organizationId:b}]}]:[],page:1,pageSize:20,total:query.side==='LINKED'?1:0}) as any);
 vi.mocked(iamApi.revokeRoleUsers).mockResolvedValue(undefined);render(<RoleUsersModal role={{id:'role',name:'教学角色',dataScope:'ALL'} as any} onClose={()=>{}}/>);const right=await screen.findByRole('region',{name:'已加入'});await waitFor(()=>expect(within(right).getAllByText('跨校教师')).toHaveLength(2));fireEvent.click(within(right).getAllByRole('checkbox')[1]);fireEvent.click(screen.getByRole('button',{name:'移除所选'}));await waitFor(()=>expect(iamApi.revokeRoleUsers).toHaveBeenCalledWith('role',[{userId,organizationId:a}]));expect(iamApi.assignRoleUsers).not.toHaveBeenCalled();
});

it('SELF基础角色关联使用全局空scope，不要求组织授予',async()=>{
 vi.mocked(organizationApi.listTree).mockResolvedValue([]);vi.mocked(iamApi.roleUserPicker).mockImplementation(async(_id,query)=>({items:query.side==='CANDIDATES'?[{id:'self-user',username:'self',displayName:'基础账号',status:'ENABLED',organizationNames:[]}]:[],page:1,pageSize:20,total:query.side==='CANDIDATES'?1:0}) as any);vi.mocked(iamApi.assignRoleUsers).mockResolvedValue(undefined);render(<RoleUsersModal role={{id:'self-role',name:'普通角色',dataScope:'SELF'} as any} onClose={()=>{}}/>);const left=screen.getByRole('region',{name:'待加入'});await within(left).findByText('基础账号');expect(screen.queryByRole('combobox',{name:'授权组织'})).not.toBeInTheDocument();fireEvent.click(within(left).getAllByRole('checkbox')[1]);fireEvent.click(screen.getByRole('button',{name:'加入所选'}));await waitFor(()=>expect(iamApi.assignRoleUsers).toHaveBeenCalledWith('self-role',[{userId:'self-user',organizationId:null}]));
});
