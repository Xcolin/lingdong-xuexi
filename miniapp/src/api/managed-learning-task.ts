import { request } from './http';
import type { TaskAttachment } from './attachment';

export type ManagedTaskSource = 'ORGANIZATION' | 'TEACHER';
export type ManagedTaskStatus = 'DRAFT' | 'PUBLISHED';

export interface ManagedLearningTask {
  id: string;
  sourceType: ManagedTaskSource;
  sourceOrganizationId: string;
  creatorUserId?: string;
  title: string;
  difficultyLevel: number;
  basePoints: number;
  durationMinutes: number;
  scheduledDate: string;
  categoryCode?: string | null;
  tagCodes?: string[];
  remark?: string | null;
  reviewerUserId?: string;
  targets?: Array<{ id?: string; targetType: 'ORGANIZATION' | 'STUDENT'; targetId: string }>;
  recurrenceEnabled: boolean;
  recurrenceEndDate?: string | null;
  status: ManagedTaskStatus;
}

export interface ManagedLearningTaskPage {
  items: ManagedLearningTask[];
  page: number;
  pageSize: number;
  total: number;
}

export interface ManagedOrganizationOption {
  id: string;
  name: string;
  organizationType: string;
}

export interface ManagedTaskProgress {
  assignmentId: string;
  studentId: string;
  studentName: string;
  studentAccountMasked: string | null;
  className: string | null;
  currentStatus: string;
  scheduledDate: string;
}

export interface ManagedTaskReview {
  assignmentId: string;
  title: string;
  studentName: string;
  basePoints: number;
  sourceType: ManagedTaskSource;
  sourceOrganizationName: string | null;
  latestCheckIn: {
    id: string;
    content: string | null;
    submittedAt: string;
    attachments?: TaskAttachment[];
  };
}

export interface ManagedTaskInput {
  sourceType: ManagedTaskSource;
  sourceOrganizationId: string;
  title: string;
  difficultyLevel: number;
  durationMinutes: number;
  scheduledDate: string;
  remark?: string;
  reviewerUserId?: string;
  targets: Array<{ targetType: 'ORGANIZATION'; targetId: string }>;
  recurrenceEnabled: boolean;
}

export function listManagedTasks(
  accessToken: string,
  page = 1
): Promise<ManagedLearningTaskPage> {
  return authenticated<ManagedLearningTaskPage>(
    accessToken, `/learning-tasks?page=${page}&pageSize=20`);
}

export function getManagedTask(
  accessToken: string,
  taskId: string
): Promise<ManagedLearningTask> {
  return authenticated<ManagedLearningTask>(
    accessToken, `/learning-tasks/${encodeURIComponent(taskId)}`);
}

export function createManagedTask(
  accessToken: string,
  input: ManagedTaskInput
): Promise<ManagedLearningTask> {
  return authenticated<ManagedLearningTask>(accessToken, '/learning-tasks', {
    method: 'POST', data: input
  });
}

export function updateManagedTask(
  accessToken: string,
  taskId: string,
  input: ManagedTaskInput
): Promise<ManagedLearningTask> {
  return authenticated<ManagedLearningTask>(
    accessToken, `/learning-tasks/${encodeURIComponent(taskId)}`, {
      method: 'PUT', data: input
    });
}

export function publishManagedTask(accessToken: string, taskId: string): Promise<void> {
  return authenticated(accessToken, `/learning-tasks/${encodeURIComponent(taskId)}/publish`, {
    method: 'POST', data: {}
  });
}

export function listManagedOrganizations(
  accessToken: string
): Promise<ManagedOrganizationOption[]> {
  return authenticated<ManagedOrganizationOption[]>(
    accessToken, '/learning-task-options/organizations?sourceType=ORGANIZATION');
}

export function listManagedTaskProgress(
  accessToken: string,
  taskId: string
): Promise<{ items: ManagedTaskProgress[]; page: number; pageSize: number; total: number }> {
  return authenticated(
    accessToken, `/learning-tasks/${encodeURIComponent(taskId)}/progress?page=1&pageSize=100`);
}

export function listManagedTaskReviews(
  accessToken: string, page = 1, pageSize = 20
): Promise<{ items: ManagedTaskReview[]; page: number; pageSize: number; total: number }> {
  return authenticated(accessToken, `/task-reviews?page=${page}&pageSize=${pageSize}`);
}

export function approveManagedTaskReview(
  accessToken: string,
  assignmentId: string,
  expectedCheckInId: string
): Promise<void> {
  return authenticated(accessToken, `/task-reviews/${encodeURIComponent(assignmentId)}/approve`, {
    method: 'POST', data: { expectedCheckInId }
  });
}

export function rejectManagedTaskReview(
  accessToken: string,
  assignmentId: string,
  reviewComment: string,
  expectedCheckInId: string
): Promise<void> {
  return authenticated(accessToken, `/task-reviews/${encodeURIComponent(assignmentId)}/reject`, {
    method: 'POST', data: { reviewComment, expectedCheckInId }
  });
}

function authenticated<T>(
  accessToken: string,
  path: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}
): Promise<T> {
  return request<T>(path, {
    ...options,
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
