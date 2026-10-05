import {useEffect,useRef,useState} from 'react';
import {Alert,Form,Input,Modal,message} from 'antd';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {usersApi,type ManagedUser} from '../../api/users';
interface PasswordForm {password:string;confirmPassword:string;}
export function ResetUserPasswordModal({user,onClose}:{user:ManagedUser;onClose:()=>void}) {
 const [form]=Form.useForm<PasswordForm>();const [busy,setBusy]=useState(false);const [error,setError]=useState<string>();const saving=useRef(false);
 useEffect(()=>{form.resetFields();setError(undefined);},[user.id,form]);
 function close(){if(saving.current)return;form.resetFields();onClose();}
 async function save(values:PasswordForm){if(saving.current)return;saving.current=true;setBusy(true);setError(undefined);try{await usersApi.setPassword(user.id,values.password);message.success('密码已重置');form.resetFields();onClose();}catch(e){setError(e instanceof Error?e.message:'密码重置失败');}finally{saving.current=false;setBusy(false);}}
 return <Modal title={`重置密码：${user.displayName}`} open onCancel={close} closable={!busy} maskClosable={!busy} keyboard={!busy} footer={<>
 <Button actionKey="users.reset-password.cancel" disabled={busy} onClick={close}>取消</Button>
 <Button actionKey="users.reset-password.save" type="primary" loading={busy} disabled={busy} onClick={()=>{if(!saving.current)form.submit();}}>重置密码</Button>
 </>}>
 {error&&<Alert type="error" showIcon message={error}/>}
 <Form name="reset-user-password" form={form} layout="vertical" disabled={busy} onFinish={values=>void save(values)}>
 <Form.Item label="新密码" name="password" rules={[{required:true,message:'请输入新密码'},{pattern:/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/,message:'密码须为8-20位字母和数字组合'}]}><Input.Password autoComplete="new-password" maxLength={20}/></Form.Item>
 <Form.Item label="确认密码" name="confirmPassword" dependencies={['password']} rules={[{required:true,message:'请确认密码'},({getFieldValue})=>({validator:(_,value)=>!value||value===getFieldValue('password')?Promise.resolve():Promise.reject(new Error('两次输入的密码不一致'))})]}><Input.Password autoComplete="new-password" maxLength={20}/></Form.Item>
 </Form></Modal>;
}

