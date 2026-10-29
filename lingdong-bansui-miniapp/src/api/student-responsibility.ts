import {request} from './http';
import type {PageResult} from './institution-directory';
export interface ResponsiblePerson{id:string;name:string}
export interface StudentResponsibility{responsibleUser:ResponsiblePerson|null;source:'ASSIGNED'|'HEAD_TEACHER'|'NONE';availability:'AVAILABLE'|'UNAVAILABLE'|'UNASSIGNED';className:string|null;versionNo:number}
export type ResponsibilityReason='WORK_ADJUSTMENT'|'RESIGNATION_HANDOVER'|'TEMPORARY_TAKEOVER'|'OTHER';
export interface ResponsibilityHistory{fromUserId:string|null;fromName:string|null;toUserId:string;toName:string|null;reasonType:ResponsibilityReason;reasonText:string|null;transferredBy:string;operatorName:string|null;beforeVersionNo:number;afterVersionNo:number;createdAt:string}
const path=(id:string,action:string)=>`/students/${encodeURIComponent(id)}/institution-responsibility${action}`;
export const responsibilityApi={
 current:(token:string,id:string,institutionOrganizationId:string)=>request<StudentResponsibility>(path(id,'')+`?institutionOrganizationId=${encodeURIComponent(institutionOrganizationId)}`,{header:{Authorization:`Bearer ${token}`}}),
 options:(token:string,id:string,institutionOrganizationId:string)=>request<ResponsiblePerson[]>(path(id,'-transfer-options')+`?institutionOrganizationId=${encodeURIComponent(institutionOrganizationId)}`,{header:{Authorization:`Bearer ${token}`}}),
 transfer:(token:string,id:string,data:{institutionOrganizationId:string;targetUserId:string;reasonType:ResponsibilityReason;reasonText:string;expectedVersion:number})=>request<StudentResponsibility>(path(id,'-transfer'),{method:'POST',data,header:{Authorization:`Bearer ${token}`}}),
 history:(token:string,id:string,institutionOrganizationId:string,page=1)=>request<PageResult<ResponsibilityHistory>>(path(id,'-transfers')+`?institutionOrganizationId=${encodeURIComponent(institutionOrganizationId)}&page=${page}&pageSize=20`,{header:{Authorization:`Bearer ${token}`}})
};
