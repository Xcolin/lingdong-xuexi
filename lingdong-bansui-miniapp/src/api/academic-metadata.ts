import {request} from './http';
export interface ClassAcademicMetadata {classId:string;headTeacherUserId:string|null;headTeacherName?:string|null;versionNo:number}
export interface StudentNumberMetadata {studentId:string;institutionId:string;studentNumber:string|null;versionNo:number}
const header=(token:string,scope:string)=>({Authorization:`Bearer ${token}`,'X-Organization-Id':scope});
export interface HeadTeacherOption {userId:string;displayName:string;classOrganizationIds:string[]}
export const academicMetadataApi={
 headTeacherOptions:(token:string,scope:string,classId:string)=>request<HeadTeacherOption[]>(`/classes/${encodeURIComponent(classId)}/academic-metadata/head-teacher-options`,{header:header(token,scope)}),
 readClass:(token:string,scope:string,classId:string)=>request<ClassAcademicMetadata>(`/classes/${encodeURIComponent(classId)}/academic-metadata`,{header:header(token,scope)}),
 updateClass:(token:string,scope:string,classId:string,headTeacherUserId:string|null,versionNo:number)=>request<ClassAcademicMetadata>(`/classes/${encodeURIComponent(classId)}/academic-metadata`,{method:'PUT',header:header(token,scope),data:{headTeacherUserId,versionNo}}),
 readNumber:(token:string,scope:string,studentId:string)=>request<StudentNumberMetadata>(`/students/${encodeURIComponent(studentId)}/institutions/${encodeURIComponent(scope)}/student-number`,{header:header(token,scope)}),
 updateNumber:(token:string,scope:string,studentId:string,studentNumber:string|null,versionNo:number)=>request<StudentNumberMetadata>(`/students/${encodeURIComponent(studentId)}/institutions/${encodeURIComponent(scope)}/student-number`,{method:'PUT',header:header(token,scope),data:{studentNumber,versionNo}})
};
