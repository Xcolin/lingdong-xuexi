import {request} from './http';
export interface CreatedClassChild {id:string;studentName:string;studentAccount:string;initialLoginCode:string}
const header=(token:string,schoolId:string)=>({Authorization:`Bearer ${token}`,'X-Organization-Id':schoolId});
export interface ClassAccountProof {studentAccount:string;loginCode:string;deviceId:string;deviceName:string;captchaChallengeId?:string;captchaAnswer?:string;consent:boolean}
export const classMemberApi={
 associateAccount:(token:string,schoolId:string,classId:string,proof:ClassAccountProof)=>request<{studentId:string;enrollmentOrganizationId:string;currentClassOrganizationIds:string[]}>(`/classes/${encodeURIComponent(classId)}/account-associations`,{method:'POST',header:header(token,schoolId),data:proof}),
 configureBatch:(token:string,schoolId:string,sourceClassId:string,targetClassId:string,studentIds:string[],reason:string)=>request<{affectedCount:number;studentIds:string[]}>('/students/class-configurations:batch',{method:'POST',header:header(token,schoolId),data:{sourceClassOrganizationId:sourceClassId,targetClassOrganizationId:targetClassId,studentIds,reason}}),
 create:(token:string,schoolId:string,classId:string,studentName:string,gradeCode?:string)=>request<CreatedClassChild>('/students',{method:'POST',header:header(token,schoolId),data:{studentName,organizationId:classId,...(gradeCode?{gradeCode}:{})}}),
 assign:(token:string,schoolId:string,studentId:string,classId:string)=>request<{studentId:string;classOrganizationId:string;status:string}>(`/students/${encodeURIComponent(studentId)}/class`,{method:'PUT',header:header(token,schoolId),data:{classOrganizationId:classId,requireUnassigned:true}})
};
