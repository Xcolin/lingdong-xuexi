import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { interfaceServiceManagementApi } from '../../api/interface-services';
import { InterfaceServiceManagementPage } from './InterfaceServiceManagementPage';

vi.mock('../../api/interface-services', () => ({
  interfaceServiceManagementApi: {
    listServices: vi.fn(),
    listChanges: vi.fn(),
    listCallLogs: vi.fn(),
    listReviewQueue: vi.fn(),
    submitRegistration: vi.fn(),
    submitEnable: vi.fn(),
    submitDisable: vi.fn(),
    submitAuthorization: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn()
  }
}));

const service = {
  id: '1874244142494647301',
  serviceName: '微信服务通知',
  direction: 'OUTBOUND' as const,
  purpose: 'WECHAT' as const,
  callerName: 'notification-adapter',
  authorizationScope: 'GLOBAL' as const,
  authorizationScopeValue: null,
  ownerId: '1874244142494646201',
  status: 'ENABLED' as const,
  createdAt: '2026-08-31T12:00:00',
  updatedAt: '2026-08-31T12:00:00'
};

const reviewItem = {
  changeId: '1874244142494647302',
  taskId: '1874244142494647303',
  serviceId: null,
  changeType: 'CREATE' as const,
  serviceName: '学校数据同步',
  direction: 'OUTBOUND' as const,
  purpose: 'DATA_SYNC' as const,
  callerName: 'school-sync-adapter',
  authorizationScope: 'SCHOOL' as const,
  authorizationScopeValue: 'school:1001',
  ownerId: '1874244142494646202',
  targetStatus: 'ENABLED' as const,
  executionStatus: 'FAILED' as const,
  failureReason: '接口服务变更执行失败',
  taskTitle: '登记学校数据同步',
  taskDescription: '登记学校数据同步接口',
  taskStatus: 'PENDING_REVIEW' as const,
  submittedBy: '1874244142494646203',
  submittedAt: '2026-08-31T12:05:00',
  reviewedBy: null,
  reviewedAt: null,
  reviewComment: null,
  createdAt: '2026-08-31T12:05:00'
};

function renderPage(canManage = true, canReview = false) {
  return render(
    <ConfigProvider locale={zhCN}>
      <InterfaceServiceManagementPage canManage={canManage} canReview={canReview} />
    </ConfigProvider>
  );
}

describe('接口服务管理页面', () => {
  beforeEach(() => {
    vi.mocked(interfaceServiceManagementApi.listServices).mockResolvedValue([service]);
    vi.mocked(interfaceServiceManagementApi.listChanges).mockResolvedValue([reviewItem]);
    vi.mocked(interfaceServiceManagementApi.listReviewQueue).mockResolvedValue([reviewItem]);
    vi.mocked(interfaceServiceManagementApi.listCallLogs).mockResolvedValue([]);
    vi.mocked(interfaceServiceManagementApi.submitRegistration).mockResolvedValue({
      changeId: reviewItem.changeId, taskId: reviewItem.taskId, serviceId: null, changeType: 'CREATE'
    });
    vi.mocked(interfaceServiceManagementApi.approve).mockResolvedValue({
      taskId: reviewItem.taskId, status: 'EFFECTIVE', reviewedBy: '1874244142494646204',
      reviewedAt: '2026-08-31T12:10:00', reviewComment: '同意登记'
    });
    vi.mocked(interfaceServiceManagementApi.reject).mockResolvedValue({
      taskId: reviewItem.taskId, status: 'REJECTED', reviewedBy: '1874244142494646204',
      reviewedAt: '2026-08-31T12:10:00', reviewComment: '资料不完整'
    });
  });

  it('展示接口服务台账，只有查询权限时隐藏变更入口', async () => {
    renderPage(false, false);

    expect(await screen.findByText('微信服务通知')).toBeInTheDocument();
    expect(screen.getByText('notification-adapter')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '登记接口服务' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /停用-/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: '变更审核' })).not.toBeInTheDocument();
  });

  it('在变更记录中明确展示执行失败状态和审计原因', async () => {
    renderPage(false, false);
    fireEvent.click(screen.getByRole('tab', { name: '变更记录' }));

    expect(await screen.findByText('执行失败')).toBeInTheDocument();
    expect(screen.getByText('接口服务变更执行失败')).toBeInTheDocument();
  });

  it('系统管理员填写完整元数据后提交登记审核', async () => {
    const user = userEvent.setup();
    renderPage(true, false);
    await screen.findByText('微信服务通知');

    expect(screen.getByText('此处仅登记服务元数据和调用结果，不保存接口地址或凭据，也不会直接调用任意 URL。')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '登记接口服务' }));
    const dialog = screen.getByRole('dialog', { name: '登记接口服务' });
    await user.type(within(dialog).getByLabelText('服务名称'), '学校数据同步');
    await user.type(within(dialog).getByLabelText('调用方名称'), 'school-sync-adapter');
    await user.type(within(dialog).getByLabelText('责任人标识'), '1874244142494646202');
    await user.type(within(dialog).getByLabelText('任务标题'), '登记学校数据同步');
    await user.type(within(dialog).getByLabelText('任务说明'), '登记学校数据同步接口');
    await user.click(within(dialog).getByRole('button', { name: /提\s*交\s*审\s*核/ }));

    await waitFor(() => expect(interfaceServiceManagementApi.submitRegistration).toHaveBeenCalledWith({
      serviceName: '学校数据同步', direction: 'OUTBOUND', purpose: 'DATA_SYNC',
      callerName: 'school-sync-adapter', authorizationScope: 'GLOBAL',
      authorizationScopeValue: undefined, ownerId: '1874244142494646202',
      title: '登记学校数据同步', description: '登记学校数据同步接口'
    }));
    expect(interfaceServiceManagementApi.listServices).toHaveBeenCalledTimes(2);
  });

  it('系统审核员可查看队列并批准或驳回变更', async () => {
    renderPage(false, true);
    await screen.findByText('微信服务通知');
    fireEvent.click(screen.getByRole('tab', { name: '变更审核' }));
    expect(await screen.findByText('登记学校数据同步')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '批准-登记学校数据同步' }));
    let dialog = screen.getByRole('dialog', { name: '批准接口服务变更' });
    fireEvent.change(within(dialog).getByLabelText('审核意见'), { target: { value: '同意登记' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /确\s*认\s*批\s*准/ }));
    await waitFor(() => expect(interfaceServiceManagementApi.approve)
      .toHaveBeenCalledWith(reviewItem.taskId, '同意登记'));

    fireEvent.click(screen.getByRole('button', { name: '驳回-登记学校数据同步' }));
    dialog = screen.getByRole('dialog', { name: '驳回接口服务变更' });
    fireEvent.change(within(dialog).getByLabelText('审核意见'), { target: { value: '资料不完整' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /确\s*认\s*驳\s*回/ }));
    await waitFor(() => expect(interfaceServiceManagementApi.reject)
      .toHaveBeenCalledWith(reviewItem.taskId, '资料不完整'));
  });
});
