import {useEffect,useRef,useState} from 'react';
import {Button as PlainButton,Alert,Drawer,Form,Input,Select,TreeSelect,message} from 'antd';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {ConfiguredModal as Modal} from '../../components/ConfiguredModal';
import {lookupOrganizationTree} from '../../api/organizationLookup';
import {usersApi,type CreateUserInput} from '../../api/users';
import {notifyTeacherProfileChanged} from './teacherProfileChanged';
import {organizationUserTypeOptions,defaultOrganizationUserType} from './organizationUserTypes';
import {randomPassword} from './randomPassword';
import {notifyStudentProfileChanged} from './studentProfileChanged';
import {organizationApi,type OrganizationNode} from '../../api/organization';
export function organizationChoices(nodes:OrganizationNode[]):{title:string;value:string;disabled:boolean;children:ReturnType<typeof organizationChoices>}[]{return nodes.map(n=>({title:n.namePath??n.name,value:n.id,disabled:n.effectiveStatus!=='ENABLED'||n.status!=='ENABLED',children:organizationChoices(n.children??[])}));}
interface Props{open:boolean;organization?:Pick<OrganizationNode,'id'|'name'>&Partial<OrganizationNode>;onClose:()=>void;onSaved:()=>void|Promise<void>;actionPrefix?:string;}
function flattenOrganizations(nodes:OrganizationNode[]):OrganizationNode[]{return nodes.flatMap(node=>[node,...flattenOrganizations(node.children??[])]);}
function classGradeAncestor(node:Partial<OrganizationNode>,nodes:OrganizationNode[]):'PRESENT'|'ABSENT'|'UNKNOWN'{
 const visited=new Set<string>();let current=node;
 while(current.parentId){if(visited.has(current.parentId))return 'UNKNOWN';visited.add(current.parentId);const parent=nodes.find(item=>item.id===current.parentId);if(!parent)return 'UNKNOWN';if(parent.typeCode==='GRADE')return 'PRESENT';if(['SCHOOL','TRAINING_INSTITUTION'].includes(parent.typeCode))return 'ABSENT';current=parent;}
 return current.parentId===null?'ABSENT':'UNKNOWN';
}
interface CreateUserForm extends Omit<CreateUserInput,'type'> {type:CreateUserInput['type']|'TEACHER';confirmPassword?:string;}
export function CreateUserDrawer({open,organization,onClose,onSaved,actionPrefix='users.create-user'}:Props){
 const [form]=Form.useForm<CreateUserForm>();const type=Form.useWatch('type',form);const [nodes,setNodes]=useState<OrganizationNode[]>([]);const [loading,setLoading]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState<string>();const saving=useRef(false);const request=useRef(0);
 async function load(){const serial=++request.current;setLoading(true);setError(undefined);try{const data=lookupOrganizationTree(await usersApi.creationOrganizations(organization||form.getFieldValue('organizationId')?(type==='TEACHER'?'ORGANIZATION':type):undefined));if(serial===request.current)setNodes(data??[]);}catch(e){if(serial===request.current)setError(e instanceof Error?e.message:'组织加载失败');}finally{if(serial===request.current)setLoading(false);}}
 function trainingContext(node:Partial<OrganizationNode>|undefined,all:OrganizationNode[]):boolean{const seen=new Set<string>();let item=node;while(item){if(item.typeCode==='TRAINING_INSTITUTION')return true;if(!item.parentId||seen.has(item.parentId))break;seen.add(item.parentId);item=all.find(n=>n.id===item!.parentId);}return false;}
 const organizationId=Form.useWatch('organizationId',form);const allNodes=flattenOrganizations(nodes);const selected=allNodes.find(node=>node.id===organizationId)??organization;const allowedTypeOptions=organizationUserTypeOptions(selected?.typeCode);const independentFamilyStudent=selected?.typeCode==='FAMILY';const educationOrganization=['SCHOOL','TRAINING_INSTITUTION','CAMPUS','GRADE','CLASS'].includes(selected?.typeCode??'');const classGrade=selected?.typeCode==='CLASS'?classGradeAncestor(selected,allNodes):undefined;const needsGrade=!trainingContext(selected,allNodes)&&selected?.typeCode!=='GRADE'&&classGrade!=='PRESENT';const gradeRequired=classGrade!=='UNKNOWN';
 useEffect(()=>{if(open){form.resetFields();const password=randomPassword();form.setFieldsValue({type:defaultOrganizationUserType(organization?.typeCode),organizationId:organization?.id,password,confirmPassword:password});if(!organization)void load();}return()=>{request.current++;};},[open,organization?.id]);
 useEffect(()=>{if(open&&!organization&&type)void load();},[open,type,organization?.id]);
 useEffect(()=>{if(open&&organization&&type==='STUDENT'&&(!educationOrganization||organization.typeCode==='CLASS'))void load();},[open,organization?.id,type]);
 useEffect(()=>{if(open&&type&&!allowedTypeOptions.some(option=>option.value===type)){form.setFieldsValue({type:defaultOrganizationUserType(selected?.typeCode)});}},[open,type,selected?.typeCode,form]);
 useEffect(()=>{if(!open)form.resetFields();},[open,form]);
 useEffect(()=>{if(open&&type&&type!=='STUDENT'&&!form.getFieldValue('password')){const password=randomPassword();form.setFieldsValue({password,confirmPassword:password});}},[open,type,form]);
 async function save(values:CreateUserForm){if(saving.current||loading)return;saving.current=true;setBusy(true);setError(undefined);const {confirmPassword:_,password,studentSchoolOrganizationId,gradeCode,...input}=values;try{const created=await usersApi.create({...input,type:values.type==='TEACHER'?'ORGANIZATION':values.type,...(values.type==='TEACHER'?{defaultRoleCode:'TEACHER' as const}:{}),...(values.type==='STUDENT'?{studentSchoolOrganizationId:educationOrganization?undefined:studentSchoolOrganizationId,gradeCode:needsGrade&&!independentFamilyStudent?gradeCode?.trim():undefined}:{password}),username:values.username.trim(),displayName:values.displayName.trim(),organizationId:organization?.id??values.organizationId,mobile:values.mobile?.trim()||undefined,email:values.email?.trim().toLowerCase()||undefined});if(values.type==='STUDENT'&&created?.studentId)notifyStudentProfileChanged();if(values.type==='TEACHER')notifyTeacherProfileChanged();if(values.type==='STUDENT'&&created?.initialLoginCode){Modal.info({title:'学生账号已创建',okText:'知道了',content:<div><p>学生账号：{created.username}</p><p>初始登录码：{created.initialLoginCode}</p><p>请保存账号和登录码，用于学生登录。</p></div>});}else{message.success('用户已创建');}await onSaved();form.resetFields();onClose();}catch(e){setError(e instanceof Error?e.message:'创建失败');}finally{saving.current=false;setBusy(false);}}
 return <Drawer title="新增用户" open={open} width="min(520px,92vw)" onClose={busy?undefined:onClose} closable={!busy} maskClosable={!busy} keyboard={!busy} extra={<Button actionKey="IAM_USER_CREATE" type="primary" loading={busy} disabled={loading||(!organization&&!!error&&!nodes.length)} onClick={()=>form.submit()}>创建用户</Button>}>
 {error&&<Alert type="error" message={error} action={!organization&&<PlainButton disabled={busy} onClick={()=>void load()}>重试</PlainButton>}/>}
 <Form name="create-user" form={form} disabled={busy} layout="vertical" onFinish={values=>void save(values)}>
 <Form.Item label="所属组织" name="organizationId" rules={[{required:true,message:'请选择所属组织'}]}>{organization?<Select disabled options={[{value:organization.id,label:organization.namePath??organization.name}]}/>:<TreeSelect loading={loading} treeData={organizationChoices(nodes)} showSearch treeNodeFilterProp="title" allowClear/>}</Form.Item>
 <Form.Item label="用户账号" name="username" dependencies={['type']} rules={[{required:true,whitespace:true,message:'请输入用户账号'},{max:64},({getFieldValue})=>({validator:(_,value)=>getFieldValue('type')!=='STUDENT'||!value||/^[A-Za-z0-9_.@-]{1,64}$/.test(value.trim())?Promise.resolve():Promise.reject(new Error('学生账号仅支持字母、数字、下划线、点、@和短横线'))})]}><Input maxLength={64}/></Form.Item>
 <Form.Item label="用户名称" name="displayName" rules={[{required:true,whitespace:true,message:'请输入用户名称'},{max:64}]}><Input maxLength={64}/></Form.Item>
 <Form.Item label="邮箱" name="email" rules={[{type:"email",message:"请输入有效邮箱地址"},{max:254,message:"邮箱最多254个字符"}]}><Input maxLength={254} allowClear placeholder="可选，填写后可用于邮箱验证码登录"/></Form.Item>
 <Form.Item label="手机号" name="mobile" rules={[{max:32}]}><Input maxLength={32}/></Form.Item>
 <Form.Item label="用户类型" name="type" rules={[{required:true}]}><Select options={allowedTypeOptions}/></Form.Item>
 {type==='STUDENT'?<>
 {!educationOrganization&&!independentFamilyStudent&&<Form.Item label="学生学校" name="studentSchoolOrganizationId" preserve={false} rules={[{required:true,message:'请选择学生学校'}]}><Select loading={loading} showSearch optionFilterProp="label" options={allNodes.filter(node=>['SCHOOL','TRAINING_INSTITUTION'].includes(node.typeCode)&&node.status==='ENABLED'&&node.effectiveStatus==='ENABLED').map(node=>({value:node.id,label:node.namePath??node.name}))}/></Form.Item>}
 {needsGrade&&!independentFamilyStudent&&<Form.Item label="学生年级" name="gradeCode" preserve={false} rules={[{required:gradeRequired,whitespace:true,message:'请输入学生年级'},{max:64}]}><Input maxLength={64}/></Form.Item>}
 </>:<>
 <Form.Item label="初始密码" name="password" preserve={false} rules={[{required:true,message:'请输入初始密码'},{pattern:/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/,message:'密码须为8-20位字母和数字组合'}]}><Input.Password autoComplete="new-password" maxLength={20}/></Form.Item>
 <Form.Item label="确认密码" name="confirmPassword" preserve={false} dependencies={['password']} rules={[{required:true,message:'请确认密码'},({getFieldValue})=>({validator:(_,value)=>!value||value===getFieldValue('password')?Promise.resolve():Promise.reject(new Error('两次输入的密码不一致'))})]}><Input.Password autoComplete="new-password" maxLength={20}/></Form.Item>
 </>}
 </Form></Drawer>;
}
