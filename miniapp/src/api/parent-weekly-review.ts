import { request } from './http';
export interface WeeklyUser { clientType: string; roleCodes: string[]; permissionCodes: string[] }
export const canReadWeekly = (user: WeeklyUser) => user.clientType === 'MINIAPP' && user.roleCodes.includes('PARENT')
  && !user.roleCodes.includes('SYS_AUDITOR') && user.permissionCodes.includes('MINIAPP_GROWTH_REVIEW_READ_CHILD');
export interface WeeklyStudent { studentId: string; studentName: string }
export interface WeeklySummary {
  reviewId: string; studentId: string; studentName: string; periodType: string; periodStart: string; periodEnd: string;
  taskTotalCount: number; completedCount: number; inProgressCount: number; pendingOptimizationCount: number;
  exemptedCount: number; completionRate: number; earnedPoints: number; pauseCount: number; generatedAt: string;
}
export interface WeeklyDetail extends WeeklySummary {
  dataCutoffAt: string;
  categories: { categoryCode: string; taskCount: number; completedCount: number }[];
  dailyTrends: { trendDate: string; taskTotalCount: number; completedCount: number; completionRate: number; earnedPoints: number; pauseCount: number }[];
  supplements: { id: string; content: string; supplementedAt: string }[];
}
const base = '/growth-reviews/miniapp/students';
const options = (token: string) => ({ header: { Authorization: `Bearer ${token}` } });
export const weeklyApi = {
  me: (token: string) => request<WeeklyUser>('/auth/me', options(token)),
  students: (token: string) => request<WeeklyStudent[]>(base, options(token)),
  list: (token: string, studentId: string, page: number) => request<{ items: WeeklySummary[]; total: number; page: number; pageSize: number }>(`${base}/${encodeURIComponent(studentId)}?page=${page}&pageSize=20`, options(token)),
  detail: (token: string, studentId: string, reviewId: string) => request<WeeklyDetail>(`${base}/${encodeURIComponent(studentId)}/${encodeURIComponent(reviewId)}`, options(token))
};
