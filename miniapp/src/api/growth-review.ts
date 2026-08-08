import { request } from './http';
import { getStudentSession } from '@/session/student-session';

export type GrowthReviewSupplementType = 'INSIGHT' | 'STRENGTH_WEAKNESS' | 'NEXT_PLAN';

export interface GrowthReviewSummary {
  reviewId: string;
  studentId: string;
  studentName: string;
  periodType: 'DAY';
  periodStart: string;
  periodEnd: string;
  snapshotId: string;
  contentVersion: number;
  taskTotalCount: number;
  completedCount: number;
  inProgressCount: number;
  pendingOptimizationCount: number;
  exemptedCount: number;
  completionRate: number;
  earnedPoints: number;
  pauseCount: number;
  generatedAt: string;
}

export interface GrowthReviewSupplement {
  id: string;
  editorUserId: string;
  editorRole: 'PARENT' | 'STUDENT';
  supplementType: GrowthReviewSupplementType;
  content: string;
  supplementedAt: string;
}

export interface GrowthReviewDetail extends GrowthReviewSummary {
  dataCutoffAt: string;
  categories: Array<{ categoryCode: string; taskCount: number; completedCount: number }>;
  dailyTrends: Array<{
    trendDate: string;
    taskTotalCount: number;
    completedCount: number;
    inProgressCount: number;
    pendingOptimizationCount: number;
    completionRate: number;
    earnedPoints: number;
    pauseCount: number;
  }>;
  supplements: GrowthReviewSupplement[];
}

export interface GrowthReviewPageData {
  items: GrowthReviewSummary[];
  page: number;
  pageSize: number;
  total: number;
}

export function listMyDailyGrowthReviews(): Promise<GrowthReviewPageData> {
  return authenticatedRequest<GrowthReviewPageData>(
    '/growth-reviews/me?periodType=DAY&page=1&pageSize=20'
  );
}

export function getMyGrowthReview(reviewId: string): Promise<GrowthReviewDetail> {
  return authenticatedRequest<GrowthReviewDetail>(`/growth-reviews/me/${reviewId}`);
}

export function addMyGrowthReviewSupplement(
  reviewId: string,
  supplementType: GrowthReviewSupplementType,
  content: string
): Promise<GrowthReviewSupplement> {
  return authenticatedRequest<GrowthReviewSupplement>(
    `/growth-reviews/me/${reviewId}/supplements`,
    { method: 'POST', data: { supplementType, content } }
  );
}

function authenticatedRequest<T>(
  path: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}
): Promise<T> {
  const session = getStudentSession();
  if (!session) return Promise.reject(new Error('学生登录状态已失效'));
  return request<T>(path, {
    ...options,
    header: { Authorization: `Bearer ${session.accessToken}` }
  });
}
