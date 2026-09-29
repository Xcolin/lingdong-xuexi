import {request,ApiError} from './http';
import {getMiniappCapabilities} from './capability';
import {getOrganizationWorkbenchContext} from './organization-workbench';
import {getTeacherWorkbenchContext} from './teacher-workbench';
/** 异常报备使用自己的开关，不能借用学习任务开关判定权限。 */
export async function requireExceptionAccess(token:string,identity:'teacher'|'organization',permission='EXCEPTION_REPORT_READ') {
 const [user,capability]=await Promise.all([
  request<{clientType:string;roleCodes:string[];permissionCodes:string[]}>('/auth/me',{header:{Authorization:`Bearer ${token}`}}),getMiniappCapabilities()
 ]);
 if(!capability.organizationMiniappAuthEnabled||!capability.studentExceptionReportEnabled||user.clientType!=='MINIAPP'
   ||user.roleCodes.includes('SYS_AUDITOR')||!user.roleCodes.includes(identity==='teacher'?'TEACHER':'ORG_ADMIN')||!user.permissionCodes.includes(permission))
   throw new ApiError(403,{message:'异常功能未开启或无操作权限'});
 const context=identity==='teacher'?await getTeacherWorkbenchContext(token):await getOrganizationWorkbenchContext(token);
 return {user,context};
}
