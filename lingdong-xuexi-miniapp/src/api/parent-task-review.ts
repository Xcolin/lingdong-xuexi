import { request, apiUrl } from './http';
import type { TaskAttachment } from './attachment';
import type { ReviewIdentity } from '@/models/parent-task-reviews';
export interface ParentTaskReview {
  assignmentId: string; title: string; studentName: string; basePoints: number;
  sourceType: 'FAMILY' | 'TEACHER' | 'ORGANIZATION'; sourceOrganizationName: string | null;
  latestCheckIn: { id: string; content: string | null; submittedAt: string; attachments: TaskAttachment[] } | null;
}
export interface ParentReviewPage { items: ParentTaskReview[]; page: number; pageSize: number; total: number }
const header = (token: string) => ({ Authorization: `Bearer ${token}` });
const path = (id: string) => `/task-reviews/${encodeURIComponent(id)}`;
/** 始终使用独立家长令牌，不借用学生或机构会话。 */
export const parentReviewApi = {
  me: (token: string) => request<ReviewIdentity>('/auth/me', { header: header(token) }),
  list: (token: string, page = 1) => request<ParentReviewPage>(`/task-reviews?page=${page}&pageSize=20`, { header: header(token) }),
  detail: (token: string, id: string) => request<ParentTaskReview>(path(id), { header: header(token) }),
  approve: (token: string, id: string, expectedCheckInId: string) => request<void>(`${path(id)}/approve`, { method: 'POST', header: header(token), data: { expectedCheckInId } }),
  reject: (token: string, id: string, reviewComment: string, expectedCheckInId: string) => request<void>(`${path(id)}/reject`, { method: 'POST', header: header(token), data: { reviewComment, expectedCheckInId } }),
  download: (token: string, url: string) => new Promise<string>((resolve, reject) => uni.downloadFile({
    url: apiUrl(url), header: header(token),
    success: response => response.statusCode === 200 ? resolve(response.tempFilePath) : reject(new Error('附件读取失败')),
    fail: () => reject(new Error('附件读取失败'))
  }))
};
