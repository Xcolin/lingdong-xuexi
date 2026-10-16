import { request } from './http';
import { getStudentSession } from '@/session/student-session';

export type GrowthReviewSupplementType = 'INSIGHT' | 'STRENGTH_WEAKNESS' | 'NEXT_PLAN';

export interface GrowthReviewSummary {
  reviewId: string;
  studentId: string;
  studentName: string;
  periodType: 'DAY' | 'WEEK' | 'MONTH';
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

export function listMyGrowthReviews(periodType:'DAY'|'WEEK'):Promise<GrowthReviewPageData> {
 return authenticatedRequest(`/growth-reviews/me?periodType=${periodType}&page=1&pageSize=20`);
}
export function listMyDailyGrowthReviews(): Promise<GrowthReviewPageData> {return listMyGrowthReviews('DAY');}
export function getMyReviewIdentity():Promise<{clientType:string;roleCodes:string[];permissionCodes:string[]}> {return authenticatedRequest('/auth/me');}


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

export function saveMyDailyReflection(reviewId:string, insight:string, difficulty:string, nextPlan:string):Promise<GrowthReviewSupplement[]> {
 return authenticatedRequest(`/growth-reviews/me/${reviewId}/daily-reflection`,{method:'POST',data:{insight,difficulty,nextPlan}});
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
