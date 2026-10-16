import {useEffect,useState} from 'react';
import {Alert,Descriptions,Input,Modal,Space,Tag,message} from 'antd';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {institutionEnrollmentApi,type InstitutionInvitation,type EnrollmentRequest} from '../../api/institutionEnrollment';
import {ViewportTable as Table} from '../../components/ViewportTable';
import type {ManagedUser} from '../../api/users';
export function InstitutionEnrollmentModal({user,familyOrganizationId,onClose}:{user:ManagedUser;familyOrganizationId:string;onClose:()=>void}){
 const [history,setHistory]=useState<EnrollmentRequest[]>([]);const [historyError,setHistoryError]=useState<string>();
 useEffect(()=>{let active=true;setHistory([]);setHistoryError(undefined);void institutionEnrollmentApi.list({familyOrganizationId}).then(rows=>{if(active)setHistory(rows.filter(row=>row.studentUserId===user.id));}).catch(e=>{if(active)setHistoryError(e instanceof Error?e.message:'申请记录读取失败');});return()=>{active=false;};},[user.id,familyOrganizationId]);
 const [code,setCode]=useState('');const [target,setTarget]=useState<InstitutionInvitation>();const [busy,setBusy]=useState(false);const [error,setError]=useState<string>();
 async function resolve(){if(busy||!code.trim())return;setBusy(true);setError(undefined);try{setTarget(await institutionEnrollmentApi.resolve(code.trim()));}catch(e){setTarget(undefined);setError(e instanceof Error?e.message:'邀请码无效');}finally{setBusy(false);}}
 async function apply(){if(busy||!target)return;setBusy(true);setError(undefined);try{await institutionEnrollmentApi.apply(user.id,familyOrganizationId,code.trim());message.success('申请已提交，等待机构确认');onClose();}catch(e){setError(e instanceof Error?e.message:'申请失败');}finally{setBusy(false);}}
 return <Modal title={`申请关联学校或培训机构：${user.displayName}`} open onCancel={()=>{if(!busy)onClose();}} closable={!busy} maskClosable={!busy} footer={<Button actionKey="STUDENT_ENROLLMENT_REQUEST_CREATE" preserveLabel type="primary" disabled={!target||busy} loading={busy} onClick={()=>void apply()}>提交入学申请</Button>}>
 {error&&<Alert type="error" message={error}/>}
 <Space.Compact style={{width:'100%',marginBottom:16}}><Input aria-label="机构班级邀请码" placeholder="输入学校或培训机构提供的班级邀请码" value={code} maxLength={100} disabled={busy} onChange={e=>{setCode(e.target.value);setTarget(undefined);}}/><Button actionKey="STUDENT_ENROLLMENT_REQUEST_CREATE" preserveLabel disabled={busy||!code.trim()} onClick={()=>void resolve()}>确认班级</Button></Space.Compact>
 {target&&<Descriptions column={1}><Descriptions.Item label="机构">{target.institutionName}</Descriptions.Item><Descriptions.Item label="班级">{target.className}</Descriptions.Item></Descriptions>}
 {historyError&&<Alert type="error" message={historyError}/>}
 {history.length>0&&<Table rowKey="id" dataSource={history} scroll={{x:500,y:180}} columns={[{title:'申请机构',dataIndex:'institutionName'},{title:'班级',dataIndex:'className'},{title:'申请状态',render:(_,row)=><Tag color={row.status==='PENDING'?'gold':row.status==='APPROVED'?'green':'red'}>{{PENDING:'待审核',APPROVED:'已通过',REJECTED:'已驳回'}[row.status]}</Tag>}]}/>}
 </Modal>;
}
