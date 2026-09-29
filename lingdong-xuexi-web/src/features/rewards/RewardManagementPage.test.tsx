import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { RewardManagementPage } from './RewardManagementPage';

const rewardApi = vi.hoisted(() => ({
  listStudents: vi.fn(),
  listRewards: vi.fn(),
  createReward: vi.fn(),
  updateReward: vi.fn(),
  deleteReward: vi.fn(),
  listExchanges: vi.fn(),
  approveExchange: vi.fn(),
  rejectExchange: vi.fn(),
  verifyExchange: vi.fn()
}));

vi.mock('./api', () => ({ rewardApi }));

const studentId = '1874244142494647101';

describe('家长奖励管理页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    rewardApi.listStudents.mockResolvedValue([
      { id: studentId, studentName: '小灵', gradeCode: 'GRADE_3', status: 'ACTIVE' },
      { id: '1874244142494647102', studentName: '小动', gradeCode: 'GRADE_1', status: 'ACTIVE' }
    ]);
    rewardApi.listRewards.mockResolvedValue([{
      id: '1874244142494647501', studentId, rewardName: '周末观影', requiredPoints: 80,
      description: '完成本周计划后兑换', expiresAt: '2026-12-31T23:59:00', status: 'ONLINE',
      createdAt: '2026-08-08T09:00:00', updatedAt: '2026-08-08T09:00:00'
    }]);
    rewardApi.listExchanges.mockResolvedValue([
      {
        id: '1874244142494647601', rewardId: '1874244142494647501', studentId,
        rewardName: '周末观影', requiredPoints: 80, description: '完成本周计划后兑换',
        requestedAt: '2026-08-08T10:00:00', approvalDeadline: '2026-08-09T10:00:00',
        status: 'PENDING_APPROVAL', reviewedBy: null, reviewedAt: null,
        rejectReason: null, verifiedBy: null, verifiedAt: null
      },
      {
        id: '1874244142494647602', rewardId: '1874244142494647501', studentId,
        rewardName: '公园骑行', requiredPoints: 50, description: null,
        requestedAt: '2026-08-07T10:00:00', approvalDeadline: '2026-08-08T10:00:00',
        status: 'PENDING_VERIFICATION', reviewedBy: '1874244142494646001',
        reviewedAt: '2026-08-07T11:00:00', rejectReason: null, verifiedBy: null, verifiedAt: null
      }
    ]);
    rewardApi.createReward.mockResolvedValue({});
    rewardApi.updateReward.mockResolvedValue({});
    rewardApi.deleteReward.mockResolvedValue(undefined);
    rewardApi.approveExchange.mockResolvedValue({});
    rewardApi.rejectExchange.mockResolvedValue({});
    rewardApi.verifyExchange.mockResolvedValue({});
  });

  it('加载主关系孩子的奖励库并保留兑换处理视图', async () => {
    render(<AntdApp><RewardManagementPage /></AntdApp>);

    expect(await screen.findByText('周末观影')).toBeInTheDocument();
    expect(screen.getByText('80')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '兑换处理' })).toBeInTheDocument();
    await waitFor(() => {
      expect(rewardApi.listRewards).toHaveBeenCalledWith(studentId);
      expect(rewardApi.listExchanges).toHaveBeenCalledWith(studentId);
    });
  });

  it('新建奖励后刷新当前孩子的奖励库', async () => {
    const user = userEvent.setup();
    render(<AntdApp><RewardManagementPage /></AntdApp>);

    await screen.findByText('周末观影');
    await user.click(screen.getByRole('button', { name: '新建奖励' }));
    await user.type(screen.getByLabelText('奖励名称'), '科技馆参观');
    await user.type(screen.getByLabelText('所需积分'), '120');
    await user.type(screen.getByLabelText('奖励说明'), '周末共同前往科技馆');
    await user.click(screen.getByRole('button', { name: '保存奖励' }));

    await waitFor(() => {
      expect(rewardApi.createReward).toHaveBeenCalledWith(studentId, {
        rewardName: '科技馆参观', requiredPoints: 120,
        description: '周末共同前往科技馆', expiresAt: null, status: 'ONLINE'
      });
      expect(rewardApi.listRewards).toHaveBeenCalledTimes(2);
    });
  });

  it('切换孩子后只加载所选孩子的奖励和兑换记录', async () => {
    const user = userEvent.setup();
    render(<AntdApp><RewardManagementPage /></AntdApp>);

    await screen.findByText('周末观影');
    await user.click(screen.getByRole('combobox', { name: '选择孩子' }));
    await user.click(await screen.findByText('小动'));

    await waitFor(() => {
      expect(rewardApi.listRewards).toHaveBeenLastCalledWith('1874244142494647102');
      expect(rewardApi.listExchanges).toHaveBeenLastCalledWith('1874244142494647102');
    });
  });

  it('支持编辑、上下架和逻辑删除奖励', async () => {
    const user = userEvent.setup();
    render(<AntdApp><RewardManagementPage /></AntdApp>);

    await screen.findByText('周末观影');
    await user.click(screen.getByRole('button', { name: '编辑奖励 周末观影' }));
    const rewardName = screen.getByLabelText('奖励名称');
    await user.clear(rewardName);
    await user.type(rewardName, '家庭桌游时间');
    await user.click(screen.getByRole('button', { name: '保存奖励' }));
    await waitFor(() => {
      expect(rewardApi.updateReward).toHaveBeenCalledWith('1874244142494647501', {
        rewardName: '家庭桌游时间', requiredPoints: 80,
        description: '完成本周计划后兑换', expiresAt: '2026-12-31T23:59:00', status: 'ONLINE'
      });
    });

    await user.click(screen.getByRole('button', { name: '下架奖励 周末观影' }));
    await user.click(await screen.findByRole('button', { name: '确认下架' }));
    await waitFor(() => {
      expect(rewardApi.updateReward).toHaveBeenLastCalledWith('1874244142494647501', {
        rewardName: '周末观影', requiredPoints: 80,
        description: '完成本周计划后兑换', expiresAt: '2026-12-31T23:59:00', status: 'OFFLINE'
      });
    });

    await user.click(screen.getByRole('button', { name: '删除奖励 周末观影' }));
    await user.click(await screen.findByRole('button', { name: '确认删除' }));
    await waitFor(() => expect(rewardApi.deleteReward).toHaveBeenCalledWith('1874244142494647501'));
  });

  it('兑换处理支持审批通过、驳回和核销', async () => {
    const user = userEvent.setup();
    render(<AntdApp><RewardManagementPage /></AntdApp>);

    await screen.findByText('周末观影');
    await user.click(screen.getByRole('tab', { name: '兑换处理' }));
    await user.click(await screen.findByRole('button', { name: '同意兑换 周末观影' }));
    await user.click(await screen.findByRole('button', { name: '确认同意' }));
    await waitFor(() => expect(rewardApi.approveExchange).toHaveBeenCalledWith('1874244142494647601'));

    await user.click(screen.getByRole('button', { name: '驳回兑换 周末观影' }));
    await user.type(await screen.findByLabelText('驳回原因'), '本周计划尚未全部完成');
    await user.click(screen.getByRole('button', { name: '确认驳回' }));
    await waitFor(() => {
      expect(rewardApi.rejectExchange).toHaveBeenCalledWith(
        '1874244142494647601', '本周计划尚未全部完成'
      );
    });

    await user.click(screen.getByRole('button', { name: '确认核销 公园骑行' }));
    await user.click(await screen.findByRole('button', { name: '确认核销' }));
    await waitFor(() => expect(rewardApi.verifyExchange).toHaveBeenCalledWith('1874244142494647602'));
  });
});
