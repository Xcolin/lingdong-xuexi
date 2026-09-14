import { request } from './http';
import type { FamilyIdentity, PreservedFields, draftInput } from '@/models/family-task-draft';
export interface FamilyTask extends PreservedFields { id: string; title: string; difficultyLevel: number; durationMinutes: number; scheduledDate: string;
  remark: string | null; status: 'DRAFT' | 'PUBLISHED'; targets: { targetType: string; targetId: string }[] }
export interface FamilyStudent { id: string; studentName: string; relationshipRole: 'PRIMARY_GUARDIAN' | 'SECONDARY_GUARDIAN'; currentClassName: string | null }
const header=(token:string)=>({Authorization:`Bearer ${token}`});
const path=(id:string)=>`/learning-tasks/${encodeURIComponent(id)}`;
export const familyTaskApi={
  me:(token:string)=>request<FamilyIdentity>('/auth/me',{header:header(token)}),
  list:(token:string,page:number)=>request<{items:FamilyTask[];page:number;pageSize:number;total:number}>(`/learning-tasks?sourceType=FAMILY&page=${page}&pageSize=20`,{header:header(token)}),
  detail:(token:string,id:string)=>request<FamilyTask>(path(id),{header:header(token)}),
  students:(token:string)=>request<FamilyStudent[]>('/learning-task-options/students?sourceType=FAMILY',{header:header(token)}),
  save:(token:string,id:string|null,data:ReturnType<typeof draftInput>)=>request<FamilyTask>(id?path(id):'/learning-tasks',{method:id?'PUT':'POST',header:header(token),data}),
  publish:(token:string,id:string)=>request<{taskId:string;assignmentCount:number;status:string}>(`${path(id)}/publish`,{method:'POST',header:header(token),data:{}})
};
