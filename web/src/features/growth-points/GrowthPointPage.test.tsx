import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { GrowthPointPage } from './GrowthPointPage';

const growthPointApi = vi.hoisted(() => ({
  listStudents: vi.fn(),
  account: vi.fn(),
  ledgers: vi.fn(),
  correct: vi.fn()
}));

vi.mock('./api', () => ({ growthPointApi }));

describe('家长积分台账页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    growthPointApi.listStudents.mockResolvedValue([
      { studentId: '1874244142494647101', studentName: '小灵' }
    ]);
    growthPointApi.account.mockResolvedValue({
      studentId: '1874244142494647101', studentName: '小灵',
      totalPoints: 20, availablePoints: 20, updatedAt: '2026-08-03T09:30:00'
    });
    growthPointApi.ledgers.mockResolvedValue({
      items: [{
        id: '1874244142494647401', changeType: 'TASK_REWARD', amount: 20,
        availableDelta: 20, sourceAssignmentId: '1874244142494647201',
        sourceExchangeId: null,
        sourceTaskId: '1874244142494647001', basePointsSnapshot: 20,
        decayPercent: 0, streakDays: 1, decayRuleId: null,
        sourceType: 'FAMILY', sourceOrganizationId: null, sourceOrganizationName: null,
        taskTitle: '每日阅读打卡', reviewerUserId: '1874244142494646001',
        reviewerDisplayName: '测试家长', occurredAt: '2026-08-03T09:30:00',
        remark: '任务审核通过发放积分', correctionOfId: null,
        correctionLedgerId: null, correctionDeadline: '2026-08-06T09:30:00',
        correctable: true
      }],
      page: 1, pageSize: 20, total: 1
    });
    growthPointApi.correct.mockResolvedValue({
      studentId: '1874244142494647101', assignmentId: '1874244142494647201',
      originalLedgerId: '1874244142494647401', correctionLedgerId: '1874244142494647402',
      correctedPoints: 20, totalPoints: 0, availablePoints: 0,
      currentStatus: 'PENDING_REVIEW', occurredAt: '2026-08-03T10:00:00'
    });
  });

  it('加载主关系孩子的账户余额和来源可追溯台账', async () => {
    render(<AntdApp><GrowthPointPage /></AntdApp>);

    expect(await screen.findByText('每日阅读打卡')).toBeInTheDocument();
    expect(screen.getByText('累计积分')).toBeInTheDocument();
    expect(screen.getByText('可用积分')).toBeInTheDocument();
    expect(screen.getAllByText('20').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('家庭')).toBeInTheDocument();
    expect(screen.getByText('测试家长')).toBeInTheDocument();
    expect(screen.getByText('连续第 1 天，基础 20 分，衰减 0%')).toBeInTheDocument();
    await waitFor(() => {
      expect(growthPointApi.account).toHaveBeenCalledWith('1874244142494647101');
      expect(growthPointApi.ledgers).toHaveBeenCalledWith('1874244142494647101', 1, 20);
    });
  });

  it('确认原因后提交整笔纠错并刷新账户与台账', async () => {
    const user = userEvent.setup();
    render(<AntdApp><GrowthPointPage correctionEnabled /></AntdApp>);

    await screen.findByText('每日阅读打卡');
    await user.click(screen.getByRole('button', { name: '纠错' }));
    expect(screen.getByText('提交后将扣除 20 积分，任务回退至待审核。')).toBeInTheDocument();
    await user.type(screen.getByLabelText('纠错原因'), '误点审核通过，重新核对摘要');
    await user.click(screen.getByRole('button', { name: '确认纠错' }));

    await waitFor(() => {
      expect(growthPointApi.correct).toHaveBeenCalledWith(
        '1874244142494647101',
        '1874244142494647401',
        '误点审核通过，重新核对摘要'
      );
      expect(growthPointApi.account).toHaveBeenCalledTimes(2);
      expect(growthPointApi.ledgers).toHaveBeenCalledTimes(2);
    });
  });

  it('显示兑换奖励名称并保留字符串形式的兑换来源标识', async () => {
    growthPointApi.ledgers.mockResolvedValue({
      items: [{
        id: '1874244142494647501', changeType: 'REDEMPTION', amount: 0,
        availableDelta: -20, sourceAssignmentId: null,
        sourceExchangeId: '1874244142494647500',
        sourceType: 'FAMILY', sourceOrganizationId: null, sourceOrganizationName: null,
        taskTitle: null, reviewerUserId: '1874244142494646001',
        reviewerDisplayName: '测试家长', occurredAt: '2026-08-03T10:30:00',
        remark: '奖励兑换：周末观影', correctionOfId: null,
        correctionLedgerId: null, correctionDeadline: null, correctable: false
      }],
      page: 1, pageSize: 20, total: 1
    });

    render(<AntdApp><GrowthPointPage /></AntdApp>);

    expect(await screen.findByText('奖励兑换：周末观影')).toBeInTheDocument();
    expect(screen.getByText('-20')).toBeInTheDocument();
  });
});
