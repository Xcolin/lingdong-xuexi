import { request } from './http';
import { getStudentSession } from '@/session/student-session';
import type { TaskAttachment } from './attachment';

export type LearningTaskSourceType = 'FAMILY' | 'ORGANIZATION' | 'TEACHER';
export type TaskAssignmentStatus =
  | 'PENDING_CLAIM'
  | 'IN_PROGRESS'
  | 'PENDING_REVIEW'
  | 'NEEDS_IMPROVEMENT'
  | 'EXEMPT'
  | 'COMPLETED';
export type TaskAssignmentEffectiveStatus = TaskAssignmentStatus | 'PAUSED';
export type TaskPauseType = 'EMOTION' | 'DIFFICULTY';
export type TaskDeferType = 'AUTO' | 'MANUAL';

export interface ActiveTaskPause {
  id: string;
  pauseType: TaskPauseType;
  startedAt: string;
  expiresAt: string;
}

export interface TaskCheckIn {
  id: string;
  submissionNo: number;
  content: string | null;
  status: 'SUBMITTED' | 'REJECTED';
  submittedAt: string;
  reviewComment: string | null;
  attachments: TaskAttachment[];
}

export interface StudentTaskAssignment {
  id: string;
  taskId: string;
  sourceType: LearningTaskSourceType;
  sourceOrganizationId: string | null;
  sourceOrganizationName: string | null;
  title: string;
  difficultyLevel: number;
  basePoints: number;
  durationMinutes: number;
  scheduledDate: string;
  dueAt: string;
  categoryCode: string | null;
  remark: string | null;
  currentStatus: TaskAssignmentStatus;
  effectiveStatus: TaskAssignmentEffectiveStatus;
  currentReviewerId: string;
  reviewerDisplayName: string;
  lastDeferType: TaskDeferType | null;
  overnightMigrated: boolean;
  activePause: ActiveTaskPause | null;
  latestCheckIn: TaskCheckIn | null;
  tagCodes: string[];
}

export interface StudentTaskAssignmentPage {
  items: StudentTaskAssignment[];
  page: number;
  pageSize: number;
  total: number;
}

export interface StudentTaskAssignmentFilters {
  sourceType?: LearningTaskSourceType;
  scheduledDate?: string;
  page: number;
  pageSize: number;
}

export function listStudentTaskAssignments(
  filters: StudentTaskAssignmentFilters
): Promise<StudentTaskAssignmentPage> {
  return authenticatedRequest<StudentTaskAssignmentPage>(
    `/task-assignments?${queryString(filters)}`
  );
}

export function getStudentTaskAssignment(id: string): Promise<StudentTaskAssignment> {
  return authenticatedRequest<StudentTaskAssignment>(
    `/task-assignments/${encodeURIComponent(id)}`
  );
}

export function claimStudentTask(id: string): Promise<StudentTaskAssignment> {
  return executeTaskAction(id, 'claim');
}

export function pauseStudentTask(
  id: string,
  pauseType: TaskPauseType,
  durationMinutes: number
): Promise<StudentTaskAssignment> {
  return executeTaskAction(id, 'pause', { pauseType, durationMinutes });
}

export function resumeStudentTask(id: string): Promise<StudentTaskAssignment> {
  return executeTaskAction(id, 'resume');
}

export function abandonStudentTask(id: string, reason?: string): Promise<StudentTaskAssignment> {
  return executeTaskAction(id, 'abandon', { reason });
}

export function submitStudentTaskCheckIn(
  id: string,
  content: string,
  fileIds: string[]
): Promise<StudentTaskAssignment> {
  return executeTaskAction(id, 'check-ins', {
    content: content || null,
    fileIds
  });
}

function executeTaskAction(
  id: string,
  action: string,
  data?: Record<string, unknown>
): Promise<StudentTaskAssignment> {
  return authenticatedRequest<StudentTaskAssignment>(
    `/task-assignments/${encodeURIComponent(id)}/${action}`,
    { method: 'POST', data }
  );
}

function authenticatedRequest<T>(
  path: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}
): Promise<T> {
  const session = getStudentSession();
  if (!session) {
    return Promise.reject(new Error('学生登录状态已失效'));
  }
  return request<T>(path, {
    method: options.method,
    data: options.data,
    header: { Authorization: `Bearer ${session.accessToken}` }
  });
}

function queryString(values: StudentTaskAssignmentFilters): string {
  const entries: string[] = [];
  if (values.sourceType) entries.push(`sourceType=${encodeURIComponent(values.sourceType)}`);
  if (values.scheduledDate) entries.push(`scheduledDate=${encodeURIComponent(values.scheduledDate)}`);
  entries.push(`page=${values.page}`, `pageSize=${values.pageSize}`);
  return entries.join('&');
}
