import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {it,expect,vi} from 'vitest';
import {CreateUserDrawer} from './CreateUserDrawer';
import {usersApi} from '../../api/users';
import {organizationApi} from '../../api/organization';
import userEvent from '@testing-library/user-event';
vi.mock('../../api/users',()=>({usersApi:{create:vi.fn()}}));
vi.mock('../../api/organization',()=>({organizationApi:{listTree:vi.fn().mockResolvedValue([{id:'org',name:'示例学校',status:'ENABLED',effectiveStatus:'ENABLED',children:[]}])}}));
it('固定组织创建用户原子提交对应组织',async()=>{
 const onSaved=vi.fn();render(<CreateUserDrawer open onClose={()=>{}} onSaved={onSaved} organization={{id:'org',name:'示例学校'}}/>);
 fireEvent.change(screen.getByLabelText('用户账号'),{target:{value:'newuser'}});fireEvent.change(screen.getByLabelText('用户名称'),{target:{value:'新用户'}});fireEvent.change(screen.getByLabelText('初始密码'),{target:{value:'Password12'}});fireEvent.change(screen.getByLabelText('确认密码'),{target:{value:'Password12'}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'org',username:'newuser',displayName:'新用户',password:'Password12'})));expect(vi.mocked(usersApi.create).mock.calls.at(-1)?.[0]).not.toHaveProperty('confirmPassword');await waitFor(()=>expect(onSaved).toHaveBeenCalledOnce());
});

vi.mock('antd',async()=>{const actual=await vi.importActual<typeof import('antd')>('antd');return {...actual,message:{...actual.message,success:vi.fn(),error:vi.fn()}};});

it('新增用户必须选择组织，未选择不发创建请求',async()=>{vi.mocked(usersApi.create).mockClear();vi.mocked(organizationApi.listTree).mockResolvedValue([{id:'org',name:'示例学校',status:'ENABLED',effectiveStatus:'ENABLED',children:[]}] as any);render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}}/>);fireEvent.change(screen.getByLabelText('用户账号'),{target:{value:'missing_org'}});fireEvent.change(screen.getByLabelText('用户名称'),{target:{value:'未选择组织'}});await waitFor(()=>expect(screen.getByRole('button',{name:'创建用户'})).not.toBeDisabled());fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('请选择所属组织');expect(usersApi.create).not.toHaveBeenCalled();});

it('切换学生账号清除通用密码，切换回来或重新打开不保留密码',async()=>{
 const actor=userEvent.setup();const props={open:true,onClose:()=>{},onSaved:()=>{},organization:{id:'org',name:'示例学校'}};
 const view=render(<CreateUserDrawer {...props}/>);fireEvent.change(screen.getByLabelText('初始密码'),{target:{value:'Password12'}});
 await actor.click(screen.getByLabelText('用户类型'));await actor.click(screen.getByText('学生账号'));
 expect(screen.queryByLabelText('初始密码')).not.toBeInTheDocument();
 await actor.click(screen.getByLabelText('用户类型'));await actor.click(screen.getByText('家长账号'));
 expect(screen.getByLabelText('初始密码')).toHaveValue('');fireEvent.change(screen.getByLabelText('初始密码'),{target:{value:'Password12'}});
 view.rerender(<CreateUserDrawer {...props} open={false}/>);view.rerender(<CreateUserDrawer {...props}/>);
 expect(screen.getByLabelText('初始密码')).toHaveValue('');
});
