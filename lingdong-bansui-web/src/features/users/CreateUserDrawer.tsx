import {useEffect,useRef,useState} from 'react';
import {Alert,Drawer,Form,Input,Select,TreeSelect,message} from 'antd';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {usersApi,type CreateUserInput} from '../../api/users';
import {organizationApi,type OrganizationNode} from '../../api/organization';
export function organizationChoices(nodes:OrganizationNode[]):{title:string;value:string;disabled:boolean;children:ReturnType<typeof organizationChoices>}[]{return nodes.map(n=>({title:n.name,value:n.id,disabled:n.effectiveStatus!=='ENABLED'||n.status!=='ENABLED',children:organizationChoices(n.children??[])}));}
interface Props{open:boolean;organization?:{id:string;name:string};onClose:()=>void;onSaved:()=>void|Promise<void>;actionPrefix?:string;}
interface CreateUserForm extends CreateUserInput {confirmPassword?:string;}
export function CreateUserDrawer({open,organization,onClose,onSaved,actionPrefix='users.create-user'}:Props){
 const [form]=Form.useForm<CreateUserForm>();const type=Form.useWatch('type',form);const [nodes,setNodes]=useState<OrganizationNode[]>([]);const [loading,setLoading]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState<string>();const saving=useRef(false);const request=useRef(0);
 async function load(){const serial=++request.current;setLoading(true);setError(undefined);try{const data=await organizationApi.listTree();if(serial===request.current)setNodes(data);}catch(e){if(serial===request.current)setError(e instanceof Error?e.message:'组织加载失败');}finally{if(serial===request.current)setLoading(false);}}
 useEffect(()=>{if(open){form.resetFields();form.setFieldsValue({type:'PLATFORM',organizationId:organization?.id});if(!organization)void load();}return()=>{request.current++;};},[open,organization?.id]);
 useEffect(()=>{if(!open)form.resetFields();},[open,form]);
 async function save(values:CreateUserForm){if(saving.current||loading)return;saving.current=true;setBusy(true);setError(undefined);const {confirmPassword:_,password,...input}=values;try{await usersApi.create({...input,...(values.type==='STUDENT'?{}:{password}),username:values.username.trim(),displayName:values.displayName.trim(),organizationId:organization?.id??values.organizationId,mobile:values.mobile?.trim()||undefined});message.success('用户已创建');await onSaved();form.resetFields();onClose();}catch(e){setError(e instanceof Error?e.message:'创建失败');}finally{saving.current=false;setBusy(false);}}
 return <Drawer title="新增用户" open={open} width="min(520px,92vw)" onClose={busy?undefined:onClose} closable={!busy} maskClosable={!busy} keyboard={!busy} extra={<Button actionKey={`${actionPrefix}.save`} type="primary" loading={busy} disabled={loading||(!organization&&!!error&&!nodes.length)} onClick={()=>form.submit()}>创建用户</Button>}>
 {error&&<Alert type="error" message={error} action={!organization&&<Button actionKey={`${actionPrefix}.retry`} disabled={busy} onClick={()=>void load()}>重试</Button>}/>}
 <Form name="create-user" form={form} disabled={busy} layout="vertical" onFinish={values=>void save(values)}>
 <Form.Item label="所属组织" name="organizationId" rules={[{required:true,message:'请选择所属组织'}]}>{organization?<Select disabled options={[{value:organization.id,label:organization.name}]}/>:<TreeSelect loading={loading} treeData={organizationChoices(nodes)} showSearch treeNodeFilterProp="title" allowClear/>}</Form.Item>
 <Form.Item label="用户账号" name="username" rules={[{required:true,whitespace:true,message:'请输入用户账号'},{max:64}]}><Input maxLength={64}/></Form.Item>
 <Form.Item label="用户名称" name="displayName" rules={[{required:true,whitespace:true,message:'请输入用户名称'},{max:64}]}><Input maxLength={64}/></Form.Item>
 <Form.Item label="手机号" name="mobile" rules={[{max:32}]}><Input maxLength={32}/></Form.Item>
 <Form.Item label="用户类型" name="type" rules={[{required:true}]}><Select options={[{value:'PLATFORM',label:'平台账号'},{value:'ORGANIZATION',label:'机构账号'},{value:'FAMILY',label:'家长账号'},{value:'STUDENT',label:'学生账号'}]}/></Form.Item>
 {type==='STUDENT'?<Alert type="info" message="学生账号使用专属登录凭据，无需设置通用账号密码。"/>:<>
 <Form.Item label="初始密码" name="password" preserve={false} rules={[{required:true,message:'请输入初始密码'},{pattern:/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/,message:'密码须为8-20位字母和数字组合'}]}><Input.Password autoComplete="new-password" maxLength={20}/></Form.Item>
 <Form.Item label="确认密码" name="confirmPassword" preserve={false} dependencies={['password']} rules={[{required:true,message:'请确认密码'},({getFieldValue})=>({validator:(_,value)=>!value||value===getFieldValue('password')?Promise.resolve():Promise.reject(new Error('两次输入的密码不一致'))})]}><Input.Password autoComplete="new-password" maxLength={20}/></Form.Item>
 </>}
 </Form></Drawer>;
}
