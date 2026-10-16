import {request} from './http';
import {onboardingApi,type MyOrganization} from './organization-onboarding';
export interface ChildIdentity {userId:string;displayName?:string;username?:string;parentProfile:boolean;clientType:string;userType:string;permissionCodes:string[];roleCodes:string[]}
export interface Child {id:string;studentUserId:string;studentName:string;gradeCode:string|null;status:string;createdAt:string;updatedAt:string}
export interface CreatedChild extends Child {studentAccount:string;initialLoginCode:string}
export interface ChildPage {items:Child[];page:number;pageSize:number;total:number}
export function canManageChildren(me:ChildIdentity,permission:string){return me.parentProfile===true&&me.clientType==='MINIAPP'&&me.userType!=='STUDENT'&&!me.roleCodes.includes('SYS_AUDITOR')&&me.permissionCodes.includes(permission);}
const header=(token:string)=>({Authorization:`Bearer ${token}`});
export const parentChildrenApi={
 me:(token:string)=>request<ChildIdentity>('/auth/me',{header:header(token)}),
 families:async(token:string):Promise<MyOrganization[]> => (await onboardingApi.organizations(token)).filter(row=>row.typeCode==='FAMILY'&&row.canEnterFamily),
 list:(token:string,page:number)=>request<ChildPage>(`/students?page=${page}&pageSize=20`,{header:header(token)}),
 detail:(token:string,id:string)=>request<Child>(`/students/${encodeURIComponent(id)}`,{header:header(token)}),
 resetLoginCode:(token:string,id:string)=>request<{studentAccount:string;loginCode:string}>(`/students/${encodeURIComponent(id)}/login-code-resets`,{method:'POST',header:header(token)}),
 create:(token:string,data:{studentName:string;gradeCode?:string;familyOrganizationId:string})=>request<CreatedChild>('/students',{method:'POST',header:header(token),data})
};
