import { Button as PlainButton } from 'antd';
import {useEffect,useRef,useState} from 'react';
import {Alert,Drawer,Form,Input,Space,TreeSelect,message} from 'antd';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {usersApi,type ManagedUser,type UserOrganizationEditContext} from '../../api/users';
function organizationTree(options:UserOrganizationEditContext['options']):{title:string;value:string;disabled:boolean;children:ReturnType<typeof organizationTree>}[]{
 const ids=new Set(options.map(o=>o.id));
 const build=(parentId:string|null):ReturnType<typeof organizationTree>=>options.filter(o=>(o.parentId&&ids.has(o.parentId)?o.parentId:null)===parentId).map(o=>({title:o.name,value:o.id,disabled:o.disabled,children:build(o.id)}));
 return build(null);
}
export function EditUserDrawer({user,onClose,onSaved}:{user:ManagedUser;onClose:()=>void;onSaved:()=>Promise<void>}) {
 const [context,setContext]=useState<UserOrganizationEditContext>();const [loading,setLoading]=useState(true);
 const [form]=Form.useForm<{displayName:string;mobile?:string;email?:string;organizationIds:string[]}>();const [busy,setBusy]=useState(false);const [error,setError]=useState<string>();const saving=useRef(false);
 useEffect(()=>{let active=true;form.setFieldsValue({displayName:user.displayName,mobile:user.mobile??'',email:user.email??'',organizationIds:[]});setError(undefined);setContext(undefined);setLoading(true);void usersApi.organizationEdit(user.id).then(data=>{if(active){setContext(data);form.setFieldsValue({organizationIds:data.organizationIds});}}).catch(e=>{if(active)setError(e instanceof Error?e.message:'组织加载失败');}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[user.id,form]);
 async function save(values:{displayName:string;mobile?:string;email?:string;organizationIds:string[]}){if(saving.current||loading||!context)return;saving.current=true;setBusy(true);setError(undefined);try{await usersApi.updateProfile(user.id,{displayName:values.displayName.trim(),mobile:values.mobile?.trim()||null,email:values.email?.trim().toLowerCase()||null,organizationIds:values.organizationIds,expectedOrganizationIds:context.organizationIds});message.success('用户已更新');onClose();await onSaved();}catch(e){setError(e instanceof Error?e.message:'更新失败');}finally{saving.current=false;setBusy(false);}}
 return <Drawer title="编辑用户" open width={480} onClose={()=>{if(!saving.current)onClose();}} closable={!busy} maskClosable={!busy} keyboard={!busy} footer={<Space><PlainButton  disabled={busy} onClick={onClose}>取消</PlainButton><Button actionKey="IAM_USER_UPDATE" type="primary" loading={busy} disabled={loading||!context} onClick={()=>form.submit()}>保存</Button></Space>}>
 {error&&<Alert type="error" message={error} showIcon/>}
 <Form form={form} layout="vertical" disabled={busy} onFinish={values=>void save(values)}>
 <Form.Item label="账号" htmlFor="edit-user-username"><Input id="edit-user-username" value={user.username} readOnly/></Form.Item>
 <Form.Item label="账号类型" htmlFor="edit-user-type"><Input id="edit-user-type" value={{PERSONAL:'普通账号',PLATFORM:'平台账号',ORGANIZATION:'机构账号',FAMILY:'家长账号',STUDENT:'学生账号'}[user.type]} readOnly/></Form.Item>
 <Form.Item label="名称" name="displayName" rules={[{required:true,whitespace:true,message:'请输入名称'},{max:64,message:'名称最多64个字符'}]}><Input maxLength={64}/></Form.Item>
 <Form.Item label="所属组织" name="organizationIds" rules={[{required:true,type:'array',min:1,message:'请选择至少一个所属组织'}]}><TreeSelect multiple showSearch treeNodeFilterProp="title" loading={loading} disabled={loading||!context} treeData={context?organizationTree(context.options):[]} onChange={ids=>{const selected=Array.isArray(ids)?ids:[];form.setFieldsValue({organizationIds:[...new Set([...selected,...context?.lockedOrganizationIds??[]])]});}} placeholder="请选择所属组织"/></Form.Item>
 <Form.Item label="邮箱" name="email" rules={[{type:'email',message:'请输入有效邮箱地址'},{max:254,message:'邮箱最多254个字符'}]}><Input maxLength={254} allowClear placeholder="维护后可用于邮箱验证码登录"/></Form.Item>
 <Form.Item label="手机号" name="mobile" rules={[{max:32,message:'手机号最多32个字符'}]}><Input maxLength={32} allowClear/></Form.Item>
 </Form></Drawer>;
}
