import {request,type MiniappRequestOptions} from './http';
export interface InstitutionInvitation {id?:string;inviteCode?:string;institutionOrganizationId:string;institutionName:string;classOrganizationId:string;className:string;expiresAt:string}
export interface InvitationExpiry {id:string;expiresAt:string}
export interface EnrollmentRequest {id:string;studentId:string;studentUserId:string;studentName:string;institutionOrganizationId:string;institutionName:string;classOrganizationId:string;className:string;familyOrganizationId:string;requestedBy?:string;applicantName?:string;applicantMobile?:string;status:'PENDING'|'APPROVED'|'REJECTED';createdAt:string;reviewedAt?:string;reviewReason?:string}
function call<T>(token:string,path:string,options:MiniappRequestOptions={}){return request<T>('/institution-enrollment'+path,{...options,header:{Authorization:`Bearer ${token}`}});}
export const institutionEnrollmentApi={
 issue:(token:string,classOrganizationId:string)=>call<InstitutionInvitation>(token,'/invitations',{method:'POST',data:{classOrganizationId,validDays:7}}),
 /** 对已生成邀请码设置有效期（1-30 天），返回更新后的过期时间。 */
 updateExpiry:(token:string,invitationId:string,validDays:number)=>call<InvitationExpiry>(token,`/invitations/${encodeURIComponent(invitationId)}/expiry`,{method:'PUT',data:{validDays}}),
 resolve:(token:string,code:string)=>call<InstitutionInvitation>(token,'/invitations/resolve',{method:'POST',data:{code}}),
 apply:(token:string,studentUserId:string,familyOrganizationId:string,code:string)=>call<EnrollmentRequest>(token,'/requests',{method:'POST',data:{studentUserId,familyOrganizationId,code}}),
 list:(token:string,query:Record<string,string>)=>call<EnrollmentRequest[]>(token,'/requests?'+Object.entries(query).filter(([,v])=>v).map(([k,v])=>encodeURIComponent(k)+'='+encodeURIComponent(v)).join('&')),
 approve:(token:string,id:string)=>call<EnrollmentRequest>(token,`/requests/${encodeURIComponent(id)}/approve`,{method:'POST',data:{}}),
 reject:(token:string,id:string,reason:string)=>call<EnrollmentRequest>(token,`/requests/${encodeURIComponent(id)}/reject`,{method:'POST',data:{reason}})
};
export function enrollmentDate(value?:string){return value?value.replace('T',' ').slice(0,19):'—';}
export function maskedMobile(value?:string|null){return value?value.replace(/^(\d{3})\d{4}(\d{4})$/,'$1****$2'):'';}
