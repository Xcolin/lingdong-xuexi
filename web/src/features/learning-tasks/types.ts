export type LearningTaskSourceType = 'FAMILY' | 'ORGANIZATION' | 'TEACHER';
export type LearningTaskStatus = 'DRAFT' | 'PUBLISHED';
export type LearningTaskRecurrenceStatus = 'ACTIVE' | 'COMPLETED' | 'STOPPED';
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
  recurrenceEnabled: boolean;
  recurrenceStatus: LearningTaskRecurrenceStatus | null;
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
  recurrenceEndDate: string | null;
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
  recurrenceEnabled: boolean;
  recurrenceEndDate?: string;
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

export interface StopRecurringTaskResult {
  taskId: string;
  recurrenceId: string;
  status: 'STOPPED';
  stoppedByUserId: string;
  stoppedAt: string;
}

export type TaskDeferType = 'AUTO' | 'MANUAL';

export interface ManagedDeferCandidate {
  assignmentId: string;
  title: string;
  studentId: string;
  studentName: string;
  sourceType: LearningTaskSourceType;
  sourceOrganizationName: string | null;
  scheduledDate: string;
  currentStatus: 'NEEDS_IMPROVEMENT' | 'PENDING_CLAIM';
  lastDeferType: TaskDeferType | null;
  overnightMigrated: boolean;
}

export interface ManagedDeferCandidatePage {
  items: ManagedDeferCandidate[];
  page: number;
  pageSize: number;
  total: number;
}

export interface TaskDeferResult {
  assignmentId: string;
  targetTaskId: string;
  status: 'PENDING_CLAIM';
  targetDate: string;
  deferType: TaskDeferType;
  overnightMigrated: boolean;
}

export interface TaskCopyItemResult {
  itemId: string;
  sourceTaskId: string;
  targetTaskId: string | null;
  taskTitle: string;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  failureCode: string | null;
  failureMessage: string | null;
  retryCount: number;
}

export interface TaskCopyBatchResult {
  batchId: string;
  studentId: string;
  sourceDate: string;
  targetDate: string;
  status: 'PROCESSING' | 'COMPLETED' | 'PARTIAL_FAILED' | 'FAILED';
  totalCount: number;
  successCount: number;
  failureCount: number;
  items: TaskCopyItemResult[];
}

export interface PreviousDayTaskCopyPreview {
  studentId: string;
  studentName: string;
  sourceDate: string;
  targetDate: string;
  candidateCount: number;
  duplicateTitles: string[];
  alreadyCopied: boolean;
  existingBatch: TaskCopyBatchResult | null;
}

export type LearningTaskTemplateScope = 'SYSTEM' | 'PERSONAL';

export interface LearningTaskTemplate {
  id: string;
  templateScope: LearningTaskTemplateScope;
  templateName: string;
  taskTitle: string;
  difficultyLevel: number;
  durationMinutes: number;
  categoryCode: string | null;
  tagCodes: string[];
  remark: string | null;
  sortOrder: number;
  versionNo: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface LearningTaskTemplateInput {
  templateName: string;
  taskTitle: string;
  difficultyLevel: number;
  durationMinutes: number;
  categoryCode?: string;
  tagCodes: string[];
  remark?: string;
}

export interface LearningTaskTemplateOrderItem {
  templateId: string;
  versionNo: number;
}

export type TaskAssignmentStatus =
  | 'PENDING_CLAIM'
  | 'IN_PROGRESS'
  | 'PENDING_REVIEW'
  | 'NEEDS_IMPROVEMENT'
  | 'EXEMPT'
  | 'COMPLETED';

export interface TaskCheckIn {
  id: string;
  submissionNo: number;
  content: string | null;
  status: 'SUBMITTED' | 'REJECTED' | 'APPROVED';
  submittedAt: string;
  reviewComment: string | null;
  attachments: TaskAttachment[];
}

export interface TaskAttachment {
  id: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  contentUrl: string;
}

export interface TaskReview {
  assignmentId: string;
  taskId: string;
  title: string;
  basePoints: number;
  studentId: string;
  studentName: string;
  sourceType: LearningTaskSourceType;
  sourceOrganizationId: string | null;
  sourceOrganizationName: string | null;
  currentStatus: 'PENDING_REVIEW';
  currentReviewerId: string;
  reviewerDisplayName: string;
  latestCheckIn: TaskCheckIn;
}

export interface TaskReviewPage {
  items: TaskReview[];
  page: number;
  pageSize: number;
  total: number;
}

export interface ReviewerOption {
  userId: string;
  displayName: string;
}

export interface TaskReviewActionResult {
  assignmentId: string;
  currentStatus: TaskAssignmentStatus;
  checkInId: string;
  checkInStatus: 'REJECTED';
}

export interface ReviewerTransferResult {
  assignmentId: string;
  currentReviewerId: string;
}

export interface ApproveTaskReviewResult {
  assignmentId: string;
  currentStatus: 'COMPLETED';
  checkInId: string;
  checkInStatus: 'APPROVED';
  awardedPoints: number;
  totalPoints: number;
  availablePoints: number;
  ledgerId: string;
}
