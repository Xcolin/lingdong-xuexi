import { request } from './http';
import type { FamilyIdentity, PreservedFields, draftInput } from '@/models/family-task-draft';
export interface FamilyTask extends PreservedFields { id: string; title: string; difficultyLevel: number; durationMinutes: number; scheduledDate: string;
  remark: string | null; status: 'DRAFT' | 'PUBLISHED'; targets: { targetType: string; targetId: string }[];
  description?: string | null; taskType?: string | null; endDate?: string | null; visibility?: string | null }
export interface FamilyTaskOverview {
  id: string; title: string; taskType: string | null; scheduledDate: string; endDate: string | null;
  visibility: string | null; status: 'DRAFT' | 'PUBLISHED'; recurrenceEnabled: boolean; recurrenceFrequency: string | null;
  memberNames: string | null; assignmentTotal: number; assignmentCompleted: number; assignmentPendingReview: number;
}
export type FamilyStatusTab = 'ALL' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'COMPLETED';
export interface FamilyStudent { id: string; studentName: string; relationshipRole: 'PRIMARY_GUARDIAN' | 'SECONDARY_GUARDIAN'; currentClassName: string | null; schoolName: string | null }
export interface FamilyTaskAttachment { id: string; originalName: string; contentType: string; sizeBytes: number; contentUrl: string }
export interface FamilyTaskCheckInRecord {
  checkInId: string; assignmentId: string; studentId: string; studentName: string;
  content: string; status: 'SUBMITTED' | 'APPROVED'; submittedAt: string | null;
  completion: string | null; attachments: FamilyTaskAttachment[];
}
const header=(token:string)=>({Authorization:`Bearer ${token}`});
const path=(id:string)=>`/learning-tasks/${encodeURIComponent(id)}`;
export const familyTaskApi={
  me:(token:string)=>request<FamilyIdentity>('/auth/me',{header:header(token)}),
  list:(token:string,page:number)=>request<{items:FamilyTask[];page:number;pageSize:number;total:number}>(`/learning-tasks?sourceType=FAMILY&page=${page}&pageSize=20`,{header:header(token)}),
  listOverview:(token:string,page:number,statusTab:FamilyStatusTab,taskType:string|null,studentId:string|null)=>
    request<{items:FamilyTaskOverview[];page:number;pageSize:number;total:number}>(
      `/learning-tasks/family-overview?statusTab=${statusTab}`+
      `${taskType?`&taskType=${taskType}`:''}${studentId?`&studentId=${studentId}`:''}&page=${page}&pageSize=20`,
      {header:header(token)}),
  detail:(token:string,id:string)=>request<FamilyTask>(path(id),{header:header(token)}),
  students:(token:string)=>request<FamilyStudent[]>('/learning-task-options/students?sourceType=FAMILY',{header:header(token)}),
  save:(token:string,id:string|null,data:ReturnType<typeof draftInput>)=>request<FamilyTask>(id?path(id):'/learning-tasks',{method:id?'PUT':'POST',header:header(token),data}),
  publish:(token:string,id:string)=>request<{taskId:string;assignmentCount:number;status:string}>(`${path(id)}/publish`,{method:'POST',header:header(token),data:{}}),
  checkInRecords:(token:string,id:string)=>request<FamilyTaskCheckInRecord[]>(`${path(id)}/checkins`,{header:header(token)})
};
