import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {beforeEach,it,expect,vi} from 'vitest';
import {RegistrationPage} from './RegistrationPage';
const api=vi.hoisted(()=>({context:vi.fn(),code:vi.fn(),register:vi.fn(),agreement:vi.fn()}));
vi.mock('../../api/registration',()=>({registrationApi:api}));
beforeEach(()=>{vi.clearAllMocks();api.context.mockResolvedValue({emailEnabled:true,smsEnabled:false,agreementVersion:'v2'});api.code.mockResolvedValue({retryAfterSeconds:60});api.register.mockResolvedValue({session:{sessionId:'1'}});api.agreement.mockResolvedValue({version:'v2',content:'当前有效协议全文'});});
function setup(){render(<MemoryRouter><Routes><Route path="/" element={<RegistrationPage/>}/><Route path="/registration-progress" element={<div>已进入进度</div>}/></Routes></MemoryRouter>);return userEvent.setup();}
it('默认邮箱，发送后倒计时防止重复发送',async()=>{const user=setup();await user.type(await screen.findByLabelText('邮箱'),'test@example.com');await user.click(screen.getByRole('button',{name:'发送验证码'}));await waitFor(()=>expect(api.code).toHaveBeenCalledWith('EMAIL','test@example.com'));expect(await screen.findByRole('button',{name:'60 秒'})).toBeDisabled();expect(screen.queryByLabelText('手机号')).not.toBeInTheDocument();});
it('读取真实协议并成功注册进入进度',async()=>{const user=setup();await user.click(await screen.findByText('用户协议',{selector:'a'}));expect(await screen.findByText('当前有效协议全文')).toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Close'}));for(const [label,value] of [['邮箱','test@example.com'],['验证码','123456'],['账号','myuser'],['名称','本人'],['密码','Password123'],['确认密码','Password123']])await user.type(screen.getByLabelText(label),value);await user.click(screen.getByRole('checkbox'));await user.click(screen.getByRole('button',{name:/注\s*册/}));expect(await screen.findByText('已进入进度')).toBeInTheDocument();expect(api.register).toHaveBeenCalledWith(expect.objectContaining({channel:'EMAIL',agreementAccepted:true,agreementVersion:'v2',passwordConfirmation:'Password123'}));});
it('密码不一致不会请求注册',async()=>{const user=setup();await user.type(await screen.findByLabelText('密码'),'Password123');await user.type(screen.getByLabelText('确认密码'),'different');await user.click(screen.getByRole('button',{name:/注\s*册/}));expect(await screen.findByText('两次密码不一致')).toBeInTheDocument();expect(api.register).not.toHaveBeenCalled();});
it('上下文失败禁用注册并显示错误',async()=>{api.context.mockRejectedValue(new Error('服务暂不可用'));setup();expect(await screen.findByText('服务暂不可用')).toBeInTheDocument();expect(screen.getByRole('button',{name:/注\s*册/})).toBeDisabled();});

