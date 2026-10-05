import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {beforeEach,it,expect,vi} from 'vitest';
import {ResetUserPasswordModal} from './ResetUserPasswordModal';
import {usersApi,type ManagedUser} from '../../api/users';
vi.mock('../../api/users',()=>({usersApi:{setPassword:vi.fn()}}));
vi.mock('antd',async()=>{const actual=await vi.importActual<typeof import('antd')>('antd');return {...actual,message:{...actual.message,success:vi.fn(),error:vi.fn()}};});
const user={id:'user1',displayName:'测试老师'} as ManagedUser;
beforeEach(()=>{vi.mocked(usersApi.setPassword).mockReset();});
function fill(password:string,confirmation:string){fireEvent.change(screen.getByLabelText('新密码'),{target:{value:password}});fireEvent.change(screen.getByLabelText('确认密码'),{target:{value:confirmation}});fireEvent.click(screen.getByRole('button',{name:/重置密码/}));}
it('拒绝格式错误及不一致密码，密码不可通过trim绕过校验',async()=>{
 render(<ResetUserPasswordModal user={user} onClose={()=>{}}/>);
 fill(' Password12',' Password12');await screen.findByText('密码须为8-20位字母和数字组合');expect(usersApi.setPassword).not.toHaveBeenCalled();
 fill('Password12','Password13');await screen.findByText('两次输入的密码不一致');expect(usersApi.setPassword).not.toHaveBeenCalled();
});
it('只提交密码，提交期间不能取消且防止重复提交，完成后清空密码',async()=>{
 let resolve!:()=>void;vi.mocked(usersApi.setPassword).mockImplementation(()=>new Promise<void>(done=>{resolve=done;}));const close=vi.fn();
 render(<ResetUserPasswordModal user={user} onClose={close}/>);fill('Password12','Password12');
 await waitFor(()=>expect(usersApi.setPassword).toHaveBeenCalledWith('user1','Password12'));
 expect(screen.getByRole('button',{name:/取\s*消/})).toBeDisabled();fireEvent.click(screen.getByRole('button',{name:/重置密码/}));expect(usersApi.setPassword).toHaveBeenCalledOnce();expect(close).not.toHaveBeenCalled();
 resolve();await waitFor(()=>expect(close).toHaveBeenCalledOnce());expect(screen.getByLabelText('新密码')).toHaveValue('');expect(screen.getByLabelText('确认密码')).toHaveValue('');
});

