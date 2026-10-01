import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrganizationActivityTrend } from './OrganizationActivityTrend';

const mocks = vi.hoisted(() => ({
  currentUser: vi.fn(),
  web: vi.fn(),
  activityTrends: vi.fn()
}));
vi.mock('../../api/auth', () => ({ authApi: mocks }));
vi.mock('../../api/capability', () => ({ capabilityApi: mocks }));
vi.mock('../../api/dashboard', () => ({ dashboardApi: mocks }));

const userId = '1000000000000000001';

function granted() {
  mocks.currentUser.mockResolvedValue({
    userId, username: 'org_admin', displayName: '机构管理员', clientType: 'WEB',
    roleCodes: ['ORG_ADMIN'], permissionCodes: []
  });
  mocks.web.mockResolvedValue({ client: 'WEB', learningTaskManagementEnabled: true });
}

describe('Web 机构管理员看板活跃度趋势', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('功能停用或非机构管理员时显示权限态提示，不调用统计查询', async () => {
    mocks.currentUser.mockResolvedValue({
      userId, username: 'teacher', displayName: '教师', clientType: 'WEB',
      roleCodes: ['TEACHER'], permissionCodes: []
    });
    mocks.web.mockResolvedValue({ client: 'WEB', learningTaskManagementEnabled: false });
    render(<OrganizationActivityTrend userId={userId} />);
    expect(await screen.findByText('当前无机构管理员看板权限或学习任务功能未开启。')).toBeInTheDocument();
    expect(mocks.activityTrends).not.toHaveBeenCalled();
  });

  it('无数据时展示空态文案', async () => {
    granted();
    mocks.activityTrends.mockResolvedValue({ items: [] });
    render(<OrganizationActivityTrend userId={userId} />);
    await waitFor(() => expect(screen.getByText('近 30 天暂无学员活跃数据')).toBeInTheDocument());
  });

  it('有数据时以柱状图呈现各日活跃人数', async () => {
    granted();
    mocks.activityTrends.mockResolvedValue({
      items: [
        { date: '2026-09-28', activeStudents: 12 },
        { date: '2026-09-29', activeStudents: 5 }
      ]
    });
    render(<OrganizationActivityTrend userId={userId} />);
    const chart = await screen.findByRole('img', { name: '近 2 天学员活跃度，最高单日 12 人' });
    expect(chart).toBeInTheDocument();
    expect(screen.getByTitle('2026-09-28：12 人')).toBeInTheDocument();
    expect(screen.getByTitle('2026-09-29：5 人')).toBeInTheDocument();
  });

  it('查询失败时展示错误反馈并可重试', async () => {
    granted();
    mocks.activityTrends.mockRejectedValueOnce(new Error('统计服务暂不可用'));
    const user = userEvent.setup();
    render(<OrganizationActivityTrend userId={userId} />);
    expect(await screen.findByText('统计服务暂不可用')).toBeInTheDocument();
    mocks.activityTrends.mockResolvedValue({ items: [{ date: '2026-09-29', activeStudents: 8 }] });
    await user.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => expect(screen.getByRole('img', { name: '近 1 天学员活跃度，最高单日 8 人' })).toBeInTheDocument());
  });
});
