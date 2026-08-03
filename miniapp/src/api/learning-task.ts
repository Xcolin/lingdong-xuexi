import { request } from './http';
import { getStudentSession } from '@/session/student-session';

export type LearningTaskSourceType = 'FAMILY' | 'ORGANIZATION' | 'TEACHER';

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
  currentStatus: 'PENDING_CLAIM';
  currentReviewerId: string;
  reviewerDisplayName: string;
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

function authenticatedRequest<T>(path: string): Promise<T> {
  const session = getStudentSession();
  if (!session) {
    return Promise.reject(new Error('学生登录状态已失效'));
  }
  return request<T>(path, {
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
