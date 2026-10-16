import {iamApi,type Role} from '../../api/iam';
import {UserPickerModal} from '../users/UserPickerModal';
export function RoleUsersModal({role,onClose}:{role:Role;onClose:()=>void}){
 return <UserPickerModal open title={`关联用户 · ${role.name}`} actionPrefix="IAM_USER_ROLE_ASSIGN" roleId={role.id} requireOrganization={!['ALL','SELF'].includes(role.dataScope)} onClose={onClose} onSubmit={(ids,organizationId)=>iamApi.assignRoleUsers(role.id,ids.map(userId=>({userId,organizationId})))} onRemove={users=>iamApi.revokeRoleUsers(role.id,users.flatMap(user=>user.assignments??[]))}/>;
}
