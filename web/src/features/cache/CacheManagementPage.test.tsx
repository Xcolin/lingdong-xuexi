import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { cacheManagementApi } from '../../api/cache-management';
import { CacheManagementPage } from './CacheManagementPage';

vi.mock('../../api/cache-management', () => ({
  cacheManagementApi: {
    listOperations: vi.fn(),
    executeOperation: vi.fn(),
    submitHighRisk: vi.fn(),
    listReviewQueue: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn()
  }
}));

const operation = {
  id: '1874244142494647201',
  code: 'cache-operation-code',
  cacheDomain: 'DICTIONARY' as const,
  operationType: 'REFRESH' as const,
  status: 'SUCCEEDED' as const,
  impactDescription: '刷新数据字典缓存',
  requestedBy: '1874244142494646201',
  executedBy: '1874244142494646201',
  executedAt: '2026-08-31T10:00:00',
  createdAt: '2026-08-31T10:00:00',
  updatedAt: '2026-08-31T10:00:00'
};

const reviewItem = {
  operationId: '1874244142494647202',
  taskId: '1874244142494647203',
  cacheDomain: 'ALL' as const,
  operationType: 'CLEAR' as const,
  operationStatus: 'PENDING' as const,
  impactDescription: '发布后全量清理',
  taskStatus: 'PENDING_REVIEW' as const,
  taskTitle: '全量清除缓存',
  submittedBy: '1874244142494646202',
  submittedAt: '2026-08-31T10:05:00'
};

function renderPage(canManage = true, canReview = false) {
  return render(
    <ConfigProvider locale={zhCN}>
      <CacheManagementPage canManage={canManage} canReview={canReview} />
    </ConfigProvider>
  );
}

describe('缓存管理页面', () => {
  beforeEach(() => {
    vi.mocked(cacheManagementApi.listOperations).mockResolvedValue([operation]);
    vi.mocked(cacheManagementApi.executeOperation).mockResolvedValue(operation);
    vi.mocked(cacheManagementApi.submitHighRisk).mockResolvedValue({
      ...operation,
      id: reviewItem.operationId,
      taskId: reviewItem.taskId,
      cacheDomain: 'ALL',
      operationType: 'CLEAR',
      status: 'PENDING'
    });
    vi.mocked(cacheManagementApi.listReviewQueue).mockResolvedValue([reviewItem]);
    vi.mocked(cacheManagementApi.approve).mockResolvedValue({
      ...operation,
      id: reviewItem.operationId,
      taskId: reviewItem.taskId,
      cacheDomain: 'ALL',
      operationType: 'CLEAR'
    });
    vi.mocked(cacheManagementApi.reject).mockResolvedValue({
      ...operation,
      id: reviewItem.operationId,
      taskId: reviewItem.taskId,
      cacheDomain: 'ALL',
      operationType: 'CLEAR',
      status: 'REJECTED'
    });
  });

  it('加载并展示缓存操作台账，只有查询权限时隐藏操作入口', async () => {
    renderPage(false, false);

    expect(await screen.findByText('刷新数据字典缓存')).toBeInTheDocument();
    expect(screen.getByText('数据字典')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '执行缓存操作' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '提交高风险操作' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: '待审核任务' })).not.toBeInTheDocument();
  });

  it('执行普通缓存刷新并重新加载台账', async () => {
    const user = userEvent.setup();
    renderPage(true, false);
    await screen.findByText('刷新数据字典缓存');

    await user.click(screen.getByRole('button', { name: '执行缓存操作' }));
    const dialog = screen.getByRole('dialog', { name: '执行缓存操作' });
    await user.type(within(dialog).getByLabelText('影响说明'), '手工刷新数据字典');
    await user.click(within(dialog).getByRole('button', { name: /确\s*认\s*执\s*行/ }));

    await waitFor(() => expect(cacheManagementApi.executeOperation).toHaveBeenCalledWith({
      cacheDomain: 'DICTIONARY',
      operationType: 'REFRESH',
      impactDescription: '手工刷新数据字典'
    }));
    expect(cacheManagementApi.listOperations).toHaveBeenCalledTimes(2);
  });

  it('仅允许提交全量或用户会话清除的高风险任务', async () => {
    const user = userEvent.setup();
    renderPage(true, false);
    await screen.findByText('刷新数据字典缓存');

    await user.click(screen.getByRole('button', { name: '提交高风险操作' }));
    const dialog = screen.getByRole('dialog', { name: '提交高风险操作' });
    await user.type(within(dialog).getByLabelText('任务标题'), '全量清除缓存');
    await user.type(within(dialog).getByLabelText('影响说明'), '发布后全量清理');
    await user.click(within(dialog).getByLabelText('我已确认该操作需要系统审核员审批'));
    await user.click(within(dialog).getByRole('button', { name: /提\s*交\s*审\s*核/ }));

    await waitFor(() => expect(cacheManagementApi.submitHighRisk).toHaveBeenCalledWith({
      cacheDomain: 'ALL',
      operationType: 'CLEAR',
      title: '全量清除缓存',
      description: '发布后全量清理',
      confirmed: true
    }));
  });

  it('系统审核员可查看队列并批准或驳回任务', async () => {
    renderPage(false, true);
    await screen.findByText('刷新数据字典缓存');
    fireEvent.click(screen.getByRole('tab', { name: '待审核任务' }));
    expect(await screen.findByText('全量清除缓存')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '批准-全量清除缓存' }));
    let dialog = screen.getByRole('dialog', { name: '批准高风险任务' });
    fireEvent.change(within(dialog).getByLabelText('审核意见'), { target: { value: '同意执行' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /确\s*认\s*批\s*准/ }));
    await waitFor(() => expect(cacheManagementApi.approve).toHaveBeenCalledWith(reviewItem.taskId, '同意执行'));

    fireEvent.click(screen.getByRole('button', { name: '驳回-全量清除缓存' }));
    dialog = screen.getByRole('dialog', { name: '驳回高风险任务' });
    fireEvent.change(within(dialog).getByLabelText('审核意见'), { target: { value: '不在变更窗口' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /确\s*认\s*驳\s*回/ }));
    await waitFor(() => expect(cacheManagementApi.reject).toHaveBeenCalledWith(reviewItem.taskId, '不在变更窗口'));
  });
});
