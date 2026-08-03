export type LearningTaskSourceType = 'FAMILY' | 'ORGANIZATION' | 'TEACHER';
export type LearningTaskStatus = 'DRAFT' | 'PUBLISHED';
export type LearningTaskTargetType = 'ORGANIZATION' | 'STUDENT';

export interface LearningTaskTarget {
  id: string;
  targetType: LearningTaskTargetType;
  targetId: string;
}

export interface LearningTaskSummary {
  id: string;
  sourceType: LearningTaskSourceType;
  sourceOrganizationId: string | null;
  title: string;
  difficultyLevel: number;
  basePoints: number;
  durationMinutes: number;
  scheduledDate: string;
  status: LearningTaskStatus;
  publishedAt: string | null;
  createdAt: string;
}

export interface LearningTaskDetails extends LearningTaskSummary {
  creatorUserId: string;
  categoryCode: string | null;
  tagCodes: string[];
  remark: string | null;
  reviewerUserId: string;
  reviewTimeoutHours: number;
  updatedAt: string;
  targets: LearningTaskTarget[];
}

export interface LearningTaskPage {
  items: LearningTaskSummary[];
  page: number;
  pageSize: number;
  total: number;
}

export interface LearningTaskTargetInput {
  targetType: LearningTaskTargetType;
  targetId: string;
}

export interface LearningTaskInput {
  sourceType: LearningTaskSourceType;
  sourceOrganizationId?: string;
  title: string;
  difficultyLevel: number;
  durationMinutes: number;
  scheduledDate: string;
  categoryCode?: string;
  tagCodes: string[];
  remark?: string;
  reviewerUserId?: string;
  targets: LearningTaskTargetInput[];
}

export interface LearningTaskFilters {
  sourceType?: LearningTaskSourceType;
  status?: LearningTaskStatus;
  scheduledDate?: string;
  keyword?: string;
  page: number;
  pageSize: number;
}

export interface OrganizationOption {
  id: string;
  name: string;
  organizationType: string;
  parentId: string | null;
  organizationPath: string;
}

export interface StudentOption {
  id: string;
  studentName: string;
  studentAccountMasked: string | null;
  currentClassId: string | null;
  currentClassName: string | null;
}

export interface TeacherOption {
  userId: string;
  displayName: string;
  classIds: string[];
}

export interface PublishLearningTaskResult {
  taskId: string;
  assignmentCount: number;
  status: 'PUBLISHED';
}

export interface BatchPublishItemResult {
  taskId: string;
  success: boolean;
  assignmentCount: number | null;
  failureReason: string | null;
}

export interface BatchPublishResult {
  successCount: number;
  failureCount: number;
  items: BatchPublishItemResult[];
}
