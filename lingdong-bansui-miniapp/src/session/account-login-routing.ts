import { request } from '@/api/http';

/** Check the authenticated account before any role-specific business request. */
export async function routeRequiredPasswordChange(accessToken: string, identity: 'personal' | 'parent' | 'organization'): Promise<boolean> {
  const settings = await request<{passwordChangeRequired:boolean}>('/auth/account-settings', {
    header: {Authorization: `Bearer ${accessToken}`}
  });
  if (!settings.passwordChangeRequired) return false;
  await uni.reLaunch({url: `/pages/account-settings/account-settings?identity=${identity}`});
  return true;
}

/** Teacher-only accounts keep their existing workbench; mixed identities use organization selection. */
export async function routeTeacherAccount(accessToken:string):Promise<boolean>{
 const me=await request<{userId:string;roleCodes:string[]}>('/auth/me',{header:{Authorization:`Bearer ${accessToken}`}});
 if(!me.roleCodes.includes('TEACHER')||me.roleCodes.some(code=>['PARENT','PARENT_ADMIN','ORG_ADMIN'].includes(code)))return false;
 const {getTeacherWorkbenchContext}=await import('@/api/teacher-workbench');
 const {getOrganizationSession,saveOrganizationSession}=await import('@/session/organization-session');
 const {getRegistrationSession}=await import('@/session/registration-session');
 const {ApiError}=await import('@/api/http');
 try{await getTeacherWorkbenchContext(accessToken);}catch(e){if(e instanceof ApiError&&[403,404,428].includes(e.statusCode))return false;throw e;}
 const target=getOrganizationSession();
 if(target&&target.accessToken!==accessToken){try{const other=await request<{userId:string}>('/auth/me',{header:{Authorization:`Bearer ${target.accessToken}`}});if(other.userId!==me.userId)throw new Error('机构身份已登录其他账号，请先退出该账号');}catch(e){if(!(e instanceof ApiError)||e.statusCode!==401)throw e;const current=getOrganizationSession();if(current?.accessToken===target.accessToken)uni.removeStorageSync('lingdong.organization.session');}}
 const session=getRegistrationSession();if(!session||session.accessToken!==accessToken)return false;
 saveOrganizationSession(session);await uni.reLaunch({url:'/pages/teacher-home/teacher-home'});return true;
}
