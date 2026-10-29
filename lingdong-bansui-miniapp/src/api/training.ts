import {request,type MiniappRequestOptions} from './http';
import type {PageResult} from './institution-directory';
export type TrainingType='OFFICIAL_COURSE'|'CLUB_ACTIVITY';
export type TrainingStatus='PENDING'|'APPROVED'|'REJECTED'|'WITHDRAWN';
export interface TrainingProgram {programOrganizationId:string;institutionOrganizationId:string;programName:string;programType:TrainingType;enrollable:'ENABLED'|'DISABLED';description:string|null;stageCodes:string[];subjectCode:string|null}
export interface TrainingRequest {id:string;studentId:string;studentName:string;familyOrganizationId:string;institutionOrganizationId:string;institutionName:string;programOrganizationId:string;programName:string;requestType:TrainingType;reason:string|null;status:TrainingStatus;requestedBy:string;createdAt:string;reviewedBy:string|null;reviewedAt:string|null;reviewReason:string|null}
export interface TrainingParticipation {id:string;trainingRequestId:string;studentId:string;studentName:string;familyOrganizationId:string;institutionOrganizationId:string;institutionName:string;programOrganizationId:string;programName:string;status:'ACTIVE'|'LEFT';joinedAt:string;leftAt:string|null}
export interface TrainingScope {institutionOrganizationId?:string;familyOrganizationId?:string;studentId?:string;status?:string}
function call<T>(token:string,path:string,options:MiniappRequestOptions={}){return request<T>('/institution-enrollment'+path,{...options,header:{Authorization:`Bearer ${token}`}});}
function query(values:Record<string,string|number|undefined>){return Object.entries(values).filter(([,v])=>v!==undefined&&v!=='').map(([k,v])=>`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&');}
export const trainingApi={
 candidates:(token:string,institutionOrganizationId:string,page=1)=>call<PageResult<{programOrganizationId:string;programName:string}>>(token,'/training-program-candidates?'+query({institutionOrganizationId,page,pageSize:20})),
 institutions:(token:string,studentId:string,familyOrganizationId:string)=>call<{id:string;name:string;typeCode:string}[]>(token,'/training-institutions?'+query({studentId,familyOrganizationId})),
 programs:(token:string,scope:TrainingScope,page=1)=>call<PageResult<TrainingProgram>>(token,'/training-programs?'+query({...scope,page,pageSize:20})),
 registrations:(token:string,institutionOrganizationId:string,page=1)=>call<PageResult<TrainingProgram>>(token,'/training-program-registrations?'+query({institutionOrganizationId,page,pageSize:20})),
 register:(token:string,programOrganizationId:string,data:{institutionOrganizationId:string;programType:TrainingType;enrollable:'ENABLED'|'DISABLED'})=>call<TrainingProgram>(token,`/training-programs/${encodeURIComponent(programOrganizationId)}`,{method:'PUT',data}),
 apply:(token:string,data:{studentId:string;familyOrganizationId:string;institutionOrganizationId:string;programOrganizationId:string;requestType:TrainingType;reason:string})=>call<TrainingRequest>(token,'/training-requests',{method:'POST',data}),
 requests:(token:string,scope:TrainingScope,page=1)=>call<PageResult<TrainingRequest>>(token,'/training-requests?'+query({...scope,page,pageSize:20})),
 detail:(token:string,id:string)=>call<TrainingRequest>(token,`/training-requests/${encodeURIComponent(id)}`),
 withdraw:(token:string,id:string)=>call<TrainingRequest>(token,`/training-requests/${encodeURIComponent(id)}/withdraw`,{method:'POST',data:{}}),
 review:(token:string,id:string,decision:'approve'|'reject',reviewReason:string)=>call<TrainingRequest>(token,`/training-requests/${encodeURIComponent(id)}/${decision}`,{method:'POST',data:{reviewReason}}),
 participations:(token:string,scope:TrainingScope,page=1)=>call<PageResult<TrainingParticipation>>(token,'/training-participations?'+query({...scope,page,pageSize:20}))
};
export const trainingStatusLabel:Record<TrainingStatus,string>={PENDING:'待审核',APPROVED:'已通过',REJECTED:'已驳回',WITHDRAWN:'已撤回'};
