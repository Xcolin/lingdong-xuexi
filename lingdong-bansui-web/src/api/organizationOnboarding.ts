import { apiClient } from './http';
export interface ApplicationInput {typeCode:'SCHOOL'|'TRAINING_INSTITUTION';name:string;regionId:string;contactName:string}
export interface ApplicationVersion {versionNo:number;submissionBatch:number}
export interface OnboardingApplication extends ApplicationInput,ApplicationVersion {id:string;mobile:string;status:'PENDING'|'APPROVED'|'REJECTED'|'WITHDRAWN';taskId:string;organizationId:string|null;rejectionReason:string|null;createdAt:string;updatedAt:string}
export interface MyOrganization {id:string;name:string;typeCode:string;roleCodes:string[];owner:boolean;canEnterFamily:boolean;canEnterInstitution:boolean}
const base='/organization-onboarding';
export const organizationOnboardingApi={
 context:():Promise<{mobile:string|null;verifiedMobile:boolean;email?:string|null;verifiedEmail?:boolean}>=>apiClient.get(`${base}/context`),
 mobileCode:():Promise<{retryAfterSeconds:number}>=>apiClient.post(`${base}/mobile-code`,{}),
 verifyMobile:(code:string):Promise<void>=>apiClient.post(`${base}/mobile-verify`,{code}),
 transferOptions:(id:string):Promise<{id:string;name:string}[]>=>apiClient.get(`${base}/organizations/${encodeURIComponent(id)}/owner-transfer-options`),
 transferOwner:(id:string,targetUserId:string):Promise<void>=>apiClient.post(`${base}/organizations/${encodeURIComponent(id)}/owner-transfer`,{targetUserId}),
 regions:():Promise<{id:string;name:string;typeCode:string}[]>=>apiClient.get(`${base}/regions`),
 review:(taskId:string,decision:'approve'|'reject',comment:string):Promise<unknown>=>apiClient.post(`${base}/tasks/${encodeURIComponent(taskId)}/${decision}`,{comment}),
 families:(input:{name:string;idempotencyKey:string;regionId:string}):Promise<MyOrganization>=>apiClient.post(`${base}/families`,input),
 applications:():Promise<OnboardingApplication[]>=>apiClient.get(`${base}/applications`),
 detail:(id:string):Promise<OnboardingApplication>=>apiClient.get(`${base}/applications/${encodeURIComponent(id)}`),
 submit:(input:ApplicationInput):Promise<OnboardingApplication>=>apiClient.post(`${base}/applications`,input),
 withdraw:(id:string,input:ApplicationVersion):Promise<OnboardingApplication>=>apiClient.post(`${base}/applications/${encodeURIComponent(id)}/withdraw`,input),
 resubmit:(id:string,input:ApplicationInput&ApplicationVersion):Promise<OnboardingApplication>=>apiClient.post(`${base}/applications/${encodeURIComponent(id)}/resubmit`,input),
 myOrganizations:():Promise<MyOrganization[]>=>apiClient.get(`${base}/my-organizations`),
};

