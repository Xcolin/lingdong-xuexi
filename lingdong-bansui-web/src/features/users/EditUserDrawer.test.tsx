import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {it,expect,vi,beforeEach} from 'vitest';
import {EditUserDrawer} from './EditUserDrawer';
import {usersApi,type ManagedUser} from '../../api/users';
vi.mock('../../api/users',()=>({usersApi:{updateProfile:vi.fn(),organizationEdit:vi.fn()}}));
const user={id:'one',username:'fixed-account',displayName:'原名称',type:'ORGANIZATION',mobile:'13800000000'} as ManagedUser;
beforeEach(()=>{vi.clearAllMocks();vi.mocked(usersApi.organizationEdit).mockResolvedValue({organizationIds:['school'],lockedOrganizationIds:[],options:[{id:'school',parentId:null,name:'学校',typeCode:'SCHOOL',disabled:false}]});});
it('编辑名称及清空手机号，只提交资料并刷新列表',async()=>{
 vi.mocked(usersApi.updateProfile).mockResolvedValue(user);const saved=vi.fn().mockResolvedValue(undefined);const close=vi.fn();
 render(<EditUserDrawer user={user} onClose={close} onSaved={saved}/>);
 expect(screen.getByLabelText('账号')).toHaveAttribute('readonly');
 fireEvent.change(screen.getByLabelText('名称'),{target:{value:' 新名称 '}});
 fireEvent.change(screen.getByLabelText('手机号'),{target:{value:''}});
 await waitFor(()=>expect(screen.getByRole('button',{name:'保存'})).not.toBeDisabled());fireEvent.click(screen.getByRole('button',{name:'保存'}));
 await waitFor(()=>expect(usersApi.updateProfile).toHaveBeenCalledWith('one',{displayName:'新名称',mobile:null,email:null,organizationIds:['school'],expectedOrganizationIds:['school']}));
 expect(saved).toHaveBeenCalledOnce();expect(close).toHaveBeenCalledOnce();
});
it('失败保留输入及编辑窗口',async()=>{
 vi.mocked(usersApi.updateProfile).mockRejectedValue(new Error('手机号已存在'));const close=vi.fn();
 render(<EditUserDrawer user={user} onClose={close} onSaved={async()=>{}}/>);
 fireEvent.change(screen.getByLabelText('名称'),{target:{value:'修改名称'}});await waitFor(()=>expect(screen.getByRole('button',{name:'保存'})).not.toBeDisabled());fireEvent.click(screen.getByRole('button',{name:'保存'}));
 await screen.findByText('手机号已存在');expect(close).not.toHaveBeenCalled();expect(screen.getByLabelText('名称')).toHaveValue('修改名称');
});

it('没有所属组织时不能保存',async()=>{
 vi.mocked(usersApi.organizationEdit).mockResolvedValue({organizationIds:[],lockedOrganizationIds:[],options:[]});
 render(<EditUserDrawer user={user} onClose={()=>{}} onSaved={async()=>{}}/>);
 await waitFor(()=>expect(screen.getByRole('button',{name:'保存'})).not.toBeDisabled());
 fireEvent.click(screen.getByRole('button',{name:'保存'}));
 await screen.findByText('请选择至少一个所属组织');expect(usersApi.updateProfile).not.toHaveBeenCalled();
});

it('维护邮箱同时提交用户资料，邮箱规范化',async()=>{
 vi.mocked(usersApi.updateProfile).mockResolvedValue(user);render(<EditUserDrawer user={{...user,email:'old@example.com'}} onClose={()=>{}} onSaved={async()=>{}}/>);
 await waitFor(()=>expect(screen.getByRole('button',{name:'保存'})).not.toBeDisabled());
 fireEvent.change(screen.getByLabelText('邮箱'),{target:{value:'New@Example.com'}});fireEvent.click(screen.getByRole('button',{name:'保存'}));
 await waitFor(()=>expect(usersApi.updateProfile).toHaveBeenCalledWith('one',expect.objectContaining({email:'new@example.com'})));
});
