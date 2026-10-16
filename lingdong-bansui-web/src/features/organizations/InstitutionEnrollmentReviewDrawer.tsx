import {Check,X} from 'lucide-react';
import {formatDateTime} from '../../utils/datetime';
import {useEffect,useRef,useState} from 'react';
import {Alert,Drawer,Input,Modal,QRCode,Space,Tag,message} from 'antd';
import {ViewportTable as Table} from '../../components/ViewportTable';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {institutionEnrollmentApi,type EnrollmentRequest,type InstitutionInvitation} from '../../api/institutionEnrollment';
export function InstitutionEnrollmentReviewDrawer({open,institutionId,classId,onClose,onApproved}:{open:boolean;institutionId?:string;classId?:string;onClose:()=>void;onApproved?:()=>void}){
 const request=useRef(0);
 const [rows,setRows]=useState<EnrollmentRequest[]>([]);const [error,setError]=useState<string>();const [loading,setLoading]=useState(false);const [busy,setBusy]=useState<string>();const [rejecting,setRejecting]=useState<EnrollmentRequest>();const [reason,setReason]=useState('');
 async function load(){const serial=++request.current;setLoading(true);setError(undefined);try{const data=await institutionEnrollmentApi.list({institutionOrganizationId:institutionId,classOrganizationId:classId,status:'PENDING'});if(serial===request.current)setRows(data);}catch(e){if(serial===request.current)setError(e instanceof Error?e.message:'申请读取失败');}finally{if(serial===request.current)setLoading(false);}}
 useEffect(()=>{if(open){setRows([]);void load();}return()=>{request.current++;};},[open,institutionId,classId]);
 async function approve(row:EnrollmentRequest){if(busy)return;setBusy(row.id);try{await institutionEnrollmentApi.approve(row.id);message.success('学生已加入当前机构班级');await load();onApproved?.();}catch(e){setError(e instanceof Error?e.message:'审核失败');}finally{setBusy(undefined);}}
 async function reject(){if(!rejecting||busy||!reason.trim())return;setBusy(rejecting.id);try{await institutionEnrollmentApi.reject(rejecting.id,reason.trim());setRejecting(undefined);setReason('');await load();}catch(e){setError(e instanceof Error?e.message:'驳回失败');}finally{setBusy(undefined);}}
 return <Drawer title="入学申请审核" width="min(1100px,96vw)" open={open} onClose={onClose}>
 {error&&<Alert type="error" message={error}/>}
 <Table rowKey="id" loading={loading} dataSource={rows} columns={[{title:'学生',dataIndex:'studentName',width:150},{title:'机构',dataIndex:'institutionName',width:180},{title:'班级',dataIndex:'className',width:220},{title:'状态',render:()=> <Tag color="gold">待审核</Tag>,width:100},{title:'操作',width:180,render:(_,row)=><Space><Button actionKey="STUDENT_ENROLLMENT_REVIEW" preserveLabel className="action-tone-success" icon={<Check size={14}/>} aria-label="通过" size="small" disabled={!!busy} loading={busy===row.id} onClick={()=>void approve(row)}>通过</Button><Button actionKey="STUDENT_ENROLLMENT_REVIEW" preserveLabel icon={<X size={14}/>} danger disabled={!!busy} onClick={()=>{setRejecting(row);setReason('');}}>驳回</Button></Space>}]}/>
 <Modal title="驳回入学申请" open={!!rejecting} onCancel={()=>{if(!busy)setRejecting(undefined);}} footer={<Button actionKey="STUDENT_ENROLLMENT_REVIEW" preserveLabel icon={<X size={14}/>} danger disabled={!reason.trim()||!!busy} onClick={()=>void reject()}>确认驳回</Button>}><Input.TextArea aria-label="驳回原因" value={reason} onChange={e=>setReason(e.target.value)} maxLength={500}/></Modal>
 </Drawer>;
}
export function ClassInvitationButton({classId}:{classId:string}){
 useEffect(()=>{setInvitation(undefined);},[classId]);
 const [invitation,setInvitation]=useState<InstitutionInvitation>();const [busy,setBusy]=useState(false);const [error,setError]=useState<string>();
 async function issue(){if(busy)return;setBusy(true);setError(undefined);try{setInvitation(await institutionEnrollmentApi.issue(classId));}catch(e){setError(e instanceof Error?e.message:'生成邀请码失败');}finally{setBusy(false);}}
 return <><Button actionKey="STUDENT_ENROLLMENT_INVITE_CREATE" className="class-action-invite" size="small" title="创建班级入学邀请码" loading={busy} onClick={()=>void issue()}>邀请码</Button>{error&&<Alert type="error" message={error}/>}<Modal title="班级邀请码" open={!!invitation} onCancel={()=>setInvitation(undefined)} footer={null}>{invitation&&<><p>{invitation.institutionName} · {invitation.className}</p><QRCode value={invitation.inviteCode??''}/><Input.TextArea readOnly aria-label="班级邀请码" value={invitation.inviteCode}/><p>有效期至 {formatDateTime(invitation.expiresAt)}</p></>}</Modal></>;
}
