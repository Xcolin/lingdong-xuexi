import {apiClient} from './http';
export interface InstitutionInvitation {id?:string;inviteCode?:string;institutionOrganizationId:string;institutionName:string;classOrganizationId:string;className:string;expiresAt:string;}
export interface EnrollmentRequest {id:string;studentId:string;studentUserId:string;studentName:string;institutionOrganizationId:string;institutionName:string;classOrganizationId:string;className:string;familyOrganizationId:string;status:'PENDING'|'APPROVED'|'REJECTED';createdAt:string;reviewedAt?:string;reviewReason?:string;}
export const institutionEnrollmentApi={
 issue:(classOrganizationId:string)=>apiClient.post<InstitutionInvitation>('/institution-enrollment/invitations',{classOrganizationId,validDays:7}),
 resolve:(code:string)=>apiClient.post<InstitutionInvitation>('/institution-enrollment/invitations/resolve',{code}),
 apply:(studentUserId:string,familyOrganizationId:string,code:string)=>apiClient.post<EnrollmentRequest>('/institution-enrollment/requests',{studentUserId,familyOrganizationId,code}),
 list:(query:{institutionOrganizationId?:string;classOrganizationId?:string;familyOrganizationId?:string;status?:string})=>apiClient.get<EnrollmentRequest[]>('/institution-enrollment/requests?'+new URLSearchParams(Object.entries(query).filter(([,value])=>!!value) as [string,string][])),
 approve:(id:string)=>apiClient.post<EnrollmentRequest>(`/institution-enrollment/requests/${id}/approve`,{}),
 reject:(id:string,reason:string)=>apiClient.post<EnrollmentRequest>(`/institution-enrollment/requests/${id}/reject`,{reason})
};
