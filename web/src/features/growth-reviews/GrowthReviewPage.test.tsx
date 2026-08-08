import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { GrowthReviewPage } from './GrowthReviewPage';

const growthReviewApi = vi.hoisted(() => ({
  listStudents: vi.fn(),
  list: vi.fn(),
  detail: vi.fn(),
  supplement: vi.fn()
}));

vi.mock('./api', () => ({ growthReviewApi }));

const review = {
  reviewId: '1874244142494648101', studentId: '1874244142494647101',
  studentName: '小灵', periodType: 'DAY' as const,
  periodStart: '2026-08-08', periodEnd: '2026-08-08',
  snapshotId: '1874244142494648102', contentVersion: 2,
  taskTotalCount: 4, completedCount: 1, inProgressCount: 1,
  pendingOptimizationCount: 1, exemptedCount: 1,
  completionRate: 0.3333, earnedPoints: 25, pauseCount: 1,
  generatedAt: '2026-08-08T21:00:00'
};

describe('家长成长复盘页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    growthReviewApi.listStudents.mockResolvedValue([
      { studentId: review.studentId, studentName: '小灵' }
    ]);
    growthReviewApi.list.mockResolvedValue({ items: [review], page: 1, pageSize: 20, total: 1 });
    growthReviewApi.detail.mockResolvedValue({
      ...review,
      dataCutoffAt: '2026-08-08T21:00:00',
      categories: [{ categoryCode: 'READING', taskCount: 2, completedCount: 1 }],
      dailyTrends: [{
        trendDate: '2026-08-08', taskTotalCount: 4, completedCount: 1,
        inProgressCount: 1, pendingOptimizationCount: 1,
        completionRate: 0.3333, earnedPoints: 25, pauseCount: 1
      }],
      supplements: [{
        id: '1874244142494648103', editorUserId: '1874244142494646001',
        editorRole: 'PARENT', supplementType: 'INSIGHT',
        content: '今天阅读更专注', supplementedAt: '2026-08-08T21:10:00'
      }]
    });
    growthReviewApi.supplement.mockResolvedValue({});
  });

  it('展示当前快照、分类趋势和追加补录', async () => {
    render(<AntdApp><GrowthReviewPage /></AntdApp>);

    expect(await screen.findByText('今天阅读更专注', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText('33.33%')).toBeInTheDocument();
    expect(screen.getByText('READING')).toBeInTheDocument();
    expect(screen.getByText('第 2 版')).toBeInTheDocument();
    await waitFor(() => {
      expect(growthReviewApi.list).toHaveBeenCalledWith(review.studentId, 'DAY', 1, 20);
      expect(growthReviewApi.detail).toHaveBeenCalledWith(review.studentId, review.reviewId);
    });
  });

  it('切换月报时重新按周期查询', async () => {
    const user = userEvent.setup();
    render(<AntdApp><GrowthReviewPage /></AntdApp>);
    await screen.findByText('今天阅读更专注', {}, { timeout: 5000 });

    await user.click(screen.getByText('月报'));

    await waitFor(() => {
      expect(growthReviewApi.list).toHaveBeenLastCalledWith(review.studentId, 'MONTH', 1, 20);
    });
  });
});
