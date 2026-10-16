import {apiUrl,ApiError,request,type ApiErrorBody} from './http';
export interface TransferContext {accessToken:string;familyOrganizationId:string;studentId:string;sourceInstitutionId:string}
export interface InstitutionTransferContext {accessToken:string;studentId:string;sourceInstitutionId:string}
export interface TransferInstitution {id:string;name:string;typeCode:string;classNames:string[]}
export interface TransferOptions {studentId:string;studentName:string;institutions:TransferInstitution[]}
export interface TransferProof {id:string;originalName:string;contentType:string;sizeBytes:number}
export interface TransferProposal {id:string;studentId:string;sourceInstitutionId:string;familyOrganizationId:string;applicantUserId:string;targetDescription:string;reason:string;effectiveDate:string;attachmentIds:string[];status:'PENDING'|'APPROVED'|'REJECTED'|'WITHDRAWN'|'COMPLETED';versionNo:number;executionBlocked:boolean;executionErrorCode:string|null;display?:{studentName:string|null;institutionName:string|null;reviewReason:string|null;reviewedAt:string|null;createdAt:string|null;completedAt:string|null;proofs:TransferProof[]}}
export interface TransferPage {items:TransferProposal[];total:number;page:number;pageSize:number}
const base='/student-transfer-applications';
const headers=(token:string,organizationId:string)=>({Authorization:`Bearer ${token}`,'X-Organization-Id':organizationId});
const part=encodeURIComponent;
export const transferApplicationApi={
 institutionOptions:(token:string,studentId:string,sourceId:string)=>request<TransferOptions>(`${base}/institution-options?studentId=${part(studentId)}&sourceInstitutionId=${part(sourceId)}`,{header:headers(token,sourceId)}),
 submitInstitution:(context:InstitutionTransferContext,data:{targetDescription:string;reason:string;effectiveDate:string;attachmentIds:string[]})=>request<TransferProposal>(`${base}/institution-submissions`,{method:'POST',header:headers(context.accessToken,context.sourceInstitutionId),data:{studentId:context.studentId,sourceInstitutionId:context.sourceInstitutionId,...data}}),
 options:(token:string,studentId:string,familyId:string)=>request<TransferOptions>(`${base}/family-options?studentId=${part(studentId)}&familyOrganizationId=${part(familyId)}`,{header:headers(token,familyId)}),
 submit:(context:TransferContext,data:{targetDescription:string;reason:string;effectiveDate:string;attachmentIds:string[]})=>request<TransferProposal>(base,{method:'POST',header:headers(context.accessToken,context.familyOrganizationId),data:{studentId:context.studentId,sourceInstitutionId:context.sourceInstitutionId,familyOrganizationId:context.familyOrganizationId,...data}}),
 list:(token:string,organizationId:string,page=1,status?:string)=>request<TransferPage>(`${base}?organizationId=${part(organizationId)}&page=${page}&pageSize=20${status?`&status=${part(status)}`:''}`,{header:headers(token,organizationId)}),
 read:(token:string,organizationId:string,id:string)=>request<TransferProposal>(`${base}/${part(id)}`,{header:headers(token,organizationId)}),
 action:(token:string,organizationId:string,id:string,action:'approve'|'reject'|'withdraw'|'reject-blocked',versionNo:number,reason?:string)=>request<TransferProposal>(`${base}/${part(id)}/${action}`,{method:'POST',header:headers(token,organizationId),data:{versionNo,...(reason===undefined?{}:{reason})}}),
 retire:(token:string,familyId:string,fileId:string)=>request<void>(`${base}/materials/${part(fileId)}`,{method:'DELETE',header:headers(token,familyId)})
};
export function uploadTransferProof(context:TransferContext,filePath:string):Promise<TransferProof>{
 return uploadProof(context.accessToken,context.familyOrganizationId,`${base}/materials`,{studentId:context.studentId,sourceInstitutionId:context.sourceInstitutionId,familyOrganizationId:context.familyOrganizationId},filePath);
}
export function uploadInstitutionTransferProof(context:InstitutionTransferContext,filePath:string):Promise<TransferProof>{
 return uploadProof(context.accessToken,context.sourceInstitutionId,`${base}/institution-materials`,{studentId:context.studentId,sourceInstitutionId:context.sourceInstitutionId},filePath);
}
function uploadProof(token:string,organizationId:string,path:string,formData:Record<string,string>,filePath:string):Promise<TransferProof>{
 return new Promise((resolve,reject)=>{
  uni.uploadFile({url:apiUrl(path),filePath,name:'file',timeout:15000,
   header:headers(token,organizationId),
   formData,
   success(response){
    let body:unknown;try{body=JSON.parse(response.data);}catch{reject(new Error('材料上传响应无法识别'));return;}
    if(response.statusCode<200||response.statusCode>=300){reject(new ApiError(response.statusCode,body as ApiErrorBody));return;}
    const proof=body as TransferProof;
    if(typeof proof?.id!=='string'||!/^\d+$/.test(proof.id)||typeof proof.originalName!=='string'||typeof proof.sizeBytes!=='number'){reject(new Error('材料上传结果不完整'));return;}
    resolve(proof);
   },fail(){reject(new Error('材料上传未完成，请重试'));}
  });
 });
}
export function downloadTransferProof(token:string,organizationId:string,applicationId:string,fileId:string):Promise<string>{
 return new Promise((resolve,reject)=>uni.downloadFile({url:apiUrl(`${base}/${part(applicationId)}/materials/${part(fileId)}`),header:headers(token,organizationId),timeout:15000,
  success:r=>r.statusCode>=200&&r.statusCode<300?resolve(r.tempFilePath):reject(new Error('材料不存在或不可访问')),
  fail:()=>reject(new Error('材料读取未完成，请重试'))}));
}
