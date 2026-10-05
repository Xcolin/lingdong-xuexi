import {useEffect,useRef,useState} from 'react';
import {Alert,Modal,Spin} from 'antd';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {iamApi,type Role} from '../../api/iam';
import {UserPickerModal} from '../users/UserPickerModal';
export function RoleUsersModal({role,onClose}:{role:Role;onClose:()=>void}){
 const [relations,setRelations]=useState<{userId:string;organizationId:string|null}[]>();const [error,setError]=useState<string>();const request=useRef(0);
 async function load(){const serial=++request.current;setError(undefined);try{const data=await iamApi.listRoleUsers(role.id);if(serial===request.current)setRelations(data);}catch(e){if(serial===request.current)setError(e instanceof Error?e.message:'关联用户加载失败');}}
 useEffect(()=>{void load();return()=>{request.current++;};},[role.id]);
 if(!relations)return <Modal open title={`关联用户 · ${role.name}`} onCancel={onClose} footer={null}>{error?<Alert type="error" message={error} action={<Button actionKey="iam.role-users.retry" onClick={()=>void load()}>重试</Button>}/>:<Spin/>}</Modal>;
 return <UserPickerModal open title={`关联用户 · ${role.name}`} actionPrefix="iam.role-users" existingRelations={relations} requireOrganization={role.dataScope!=='ALL'} onClose={onClose} onSubmit={(ids,organizationId)=>iamApi.assignRoleUsers(role.id,ids.map(userId=>({userId,organizationId})))}/>;
}
