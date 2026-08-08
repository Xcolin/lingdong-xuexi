import { apiClient } from '../../api/http';
import type {
  ApproveTaskReviewResult,
  ReviewerOption,
  ReviewerTransferResult,
  TaskReview,
  TaskReviewActionResult,
  TaskReviewPage
} from './types';

/** Web 审核 API 与任务草稿管理 API 分离，避免混合两类状态模型。 */
export const taskReviewApi = {
  list(page: number, pageSize: number): Promise<TaskReviewPage> {
    return apiClient.get<TaskReviewPage>(`/task-reviews?page=${page}&pageSize=${pageSize}`);
  },
  findById(assignmentId: string): Promise<TaskReview> {
    return apiClient.get<TaskReview>(`/task-reviews/${assignmentId}`);
  },
  readAttachment(fileId: string): Promise<Blob> {
    return apiClient.getBlob(`/attachments/${encodeURIComponent(fileId)}/content`);
  },
  approve(assignmentId: string): Promise<ApproveTaskReviewResult> {
    return apiClient.post<ApproveTaskReviewResult>(`/task-reviews/${assignmentId}/approve`, {});
  },
  listReviewerOptions(assignmentId: string): Promise<ReviewerOption[]> {
    return apiClient.get<ReviewerOption[]>(`/task-reviews/${assignmentId}/reviewer-options`);
  },
  reject(assignmentId: string, reviewComment: string): Promise<TaskReviewActionResult> {
    return apiClient.post<TaskReviewActionResult>(`/task-reviews/${assignmentId}/reject`, {
      reviewComment
    });
  },
  transfer(
    assignmentId: string,
    reviewerUserId: string,
    transferReason: string
  ): Promise<ReviewerTransferResult> {
    return apiClient.post<ReviewerTransferResult>(`/task-reviews/${assignmentId}/transfer`, {
      reviewerUserId,
      transferReason
    });
  }
};
