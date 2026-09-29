export type GrowthReviewPeriodType = 'DAY' | 'WEEK' | 'MONTH';
export type GrowthReviewSupplementType = 'INSIGHT' | 'STRENGTH_WEAKNESS' | 'NEXT_PLAN';

export interface GrowthReviewStudentOption {
  studentId: string;
  studentName: string;
}

export interface GrowthReviewSummary {
  reviewId: string;
  studentId: string;
  studentName: string;
  periodType: GrowthReviewPeriodType;
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

export interface GrowthReviewPageData {
  items: GrowthReviewSummary[];
  page: number;
  pageSize: number;
  total: number;
}

export interface GrowthReviewCategory {
  categoryCode: string;
  taskCount: number;
  completedCount: number;
}

export interface GrowthReviewDailyTrend {
  trendDate: string;
  taskTotalCount: number;
  completedCount: number;
  inProgressCount: number;
  pendingOptimizationCount: number;
  completionRate: number;
  earnedPoints: number;
  pauseCount: number;
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
  categories: GrowthReviewCategory[];
  dailyTrends: GrowthReviewDailyTrend[];
  supplements: GrowthReviewSupplement[];
}

export interface AddGrowthReviewSupplementInput {
  supplementType: GrowthReviewSupplementType;
  content: string;
}
