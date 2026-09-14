import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
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

  it.each(['WEEK', 'MONTH'] as const)('%s模板切换仅改变呈现，不改变快照或请求', async (periodType) => {
    const user = userEvent.setup();
    const detail = await growthReviewApi.detail();
    growthReviewApi.detail.mockClear();
    growthReviewApi.detail.mockResolvedValue({ ...detail, periodType });
    render(<AntdApp><GrowthReviewPage /></AntdApp>);
    await screen.findByText('今天阅读更专注', {}, { timeout: 5000 });
    const calls = growthReviewApi.detail.mock.calls.length;
    const listCalls = growthReviewApi.list.mock.calls.length;

    await user.click(screen.getByText('简洁版'));
    expect(screen.getByText('33.33%')).toBeInTheDocument();
    expect(screen.queryByText('任务分类')).not.toBeInTheDocument();
    expect(screen.queryByText('今天阅读更专注')).not.toBeInTheDocument();
    expect(screen.queryByText('成长分析')).not.toBeInTheDocument();

    await user.click(screen.getByText('详细版'));
    expect(screen.getByText('今天阅读更专注')).toBeInTheDocument();
    expect(screen.getByText('成长分析')).toBeInTheDocument();
    expect(screen.getByText('暂无已补录的下一步计划')).toBeInTheDocument();
    expect(growthReviewApi.detail).toHaveBeenCalledTimes(calls);
    expect(growthReviewApi.list).toHaveBeenCalledTimes(listCalls);
  });

  it('日报保持完整内容，不提供周月模板控件', async () => {
    render(<AntdApp><GrowthReviewPage /></AntdApp>);
    await screen.findByText('今天阅读更专注', {}, { timeout: 5000 });
    expect(screen.queryByText('简洁版')).not.toBeInTheDocument();
    expect(screen.getByText('任务分类')).toBeInTheDocument();
  });

  it('切换周期后旧列表迟到不得覆盖当前报告或触发旧详情', async () => {
    const user = userEvent.setup();
    let resolveOld!: (value: unknown) => void;
    growthReviewApi.list.mockImplementation((_student: string, period: string) => period === 'DAY'
      ? new Promise(resolve => { resolveOld = resolve; })
      : Promise.resolve({ items: [review] }));
    render(<AntdApp><GrowthReviewPage /></AntdApp>);
    await waitFor(() => expect(growthReviewApi.list).toHaveBeenCalled());
    await user.click(screen.getByText('月报'));
    await screen.findByText('今天阅读更专注');
    const calls = growthReviewApi.detail.mock.calls.length;
    await act(async () => { resolveOld({ items: [] }); });
    expect(screen.getByText('今天阅读更专注')).toBeInTheDocument();
    expect(growthReviewApi.detail).toHaveBeenCalledTimes(calls);
  });

  it('旧详情迟到不得恢复已切换周期的内容', async () => {
    const user = userEvent.setup();
    const oldDetail = await growthReviewApi.detail();
    let resolveOld!: (value: unknown) => void;
    growthReviewApi.detail.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
    render(<AntdApp><GrowthReviewPage /></AntdApp>);
    await waitFor(() => expect(resolveOld).toBeDefined());
    growthReviewApi.list.mockResolvedValue({ items: [] });
    await user.click(screen.getByText('月报'));
    await waitFor(() => expect(growthReviewApi.list).toHaveBeenLastCalledWith(review.studentId, 'MONTH', 1, 20));
    await act(async () => { resolveOld(oldDetail); });
    expect(screen.queryByText('今天阅读更专注')).not.toBeInTheDocument();
  });
});
