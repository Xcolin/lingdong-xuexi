import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';

const authMocks = vi.hoisted(() => ({
  listDevices: vi.fn(),
  listSecurityEvents: vi.fn(),
  markSecurityEventRead: vi.fn(),
  markAllSecurityEventsRead: vi.fn(),
  signOutDevice: vi.fn(),
  signOutAllDevices: vi.fn(),
  signOutCurrent: vi.fn(),
  clearLocalSession: vi.fn()
}));
vi.mock('../../api/auth', () => ({ authApi: authMocks }));

const currentUser = {
  userId: '1000000000000000001', sessionId: '1000000000000000002', username: 'parent',
  displayName: '测试家长', clientType: 'WEB' as const, roleCodes: ['PARENT'], permissionCodes: []
};

describe('Web 账号安全工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMocks.listDevices.mockResolvedValue([
      { id: currentUser.sessionId, clientType: 'WEB', deviceName: '当前浏览器', current: true, accessExpiresAt: '2026-08-09T10:00:00', refreshExpiresAt: '2026-08-16T10:00:00', lastActiveAt: '2026-08-09T09:00:00' },
      { id: '1000000000000000003', clientType: 'MINIAPP', deviceName: '家长小程序', current: false, accessExpiresAt: '2026-08-09T10:00:00', refreshExpiresAt: '2026-08-16T10:00:00', lastActiveAt: '2026-08-09T08:00:00' }
    ]);
    authMocks.listSecurityEvents.mockResolvedValue([
      { id: '1000000000000000004', eventType: 'NEW_DEVICE_LOGIN', riskLevel: 'WARNING', clientType: 'WEB', deviceName: '当前浏览器', status: 'UNREAD', occurredAt: '2026-08-09T09:00:00', readAt: null }
    ]);
    authMocks.markSecurityEventRead.mockResolvedValue(undefined);
    authMocks.markAllSecurityEventsRead.mockResolvedValue(undefined);
  });

  it('功能关闭时隐藏全部设备和安全事件入口', () => {
    render(<DashboardPage currentUser={currentUser} accountSecurityManagementEnabled={false} onSessionEnded={vi.fn()} />);
    expect(screen.queryByText('设备会话')).not.toBeInTheDocument();
    expect(screen.queryByText('账号安全事件')).not.toBeInTheDocument();
    expect(authMocks.listDevices).not.toHaveBeenCalled();
  });

  it('区分当前设备并完成风险事件已读操作', async () => {
    const user = userEvent.setup();
    render(<DashboardPage currentUser={currentUser} accountSecurityManagementEnabled onSessionEnded={vi.fn()} />);

    expect(await screen.findByText('当前设备')).toBeInTheDocument();
    expect(screen.getByText('检测到新的 Web 设备登录')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '下线 家长小程序' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '下线 当前浏览器' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '标记已读' }));
    await waitFor(() => expect(authMocks.markSecurityEventRead).toHaveBeenCalledWith('1000000000000000004'));
    await user.click(screen.getByRole('button', { name: '全部标记已读' }));
    await waitFor(() => expect(authMocks.markAllSecurityEventsRead).toHaveBeenCalledOnce());
  });
});
