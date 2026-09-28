import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi, type CurrentUser } from '../../api/auth';
import { capabilityApi, type ClientCapabilities } from '../../api/capability';
import { systemTasksApi } from '../../api/system-tasks';
import { SystemTaskWorkbench, systemTaskDestination } from './SystemTaskWorkbench';
vi.mock('../../api/auth', () => ({ authApi: { currentUser: vi.fn() } }));
vi.mock('../../api/capability', () => ({ capabilityApi: { web: vi.fn() } }));
vi.mock('../../api/system-tasks', () => ({ systemTasksApi: { list: vi.fn(), detail: vi.fn() } }));
const user: CurrentUser = { userId: '1900000000000000001', sessionId: 's', username: 'audit', displayName: '审核员', clientType: 'WEB', roleCodes: ['SYS_AUDITOR'], permissionCodes: ['SYSTEM_TASK_READ', 'CACHE_READ', 'CACHE_REVIEW'] };
const caps = { client: 'WEB', cacheManagementEnabled: true } as ClientCapabilities;
const item = { id: '1900000000000000002', code: 'T1', type: 'CACHE_CLEAR', title: '清理缓存申请', description: '变更说明', impactScope: 'ALL', status: 'PENDING_REVIEW' as const, submittedBy: '1900000000000000003', submittedAt: null, reviewedBy: null, reviewedAt: null, reviewComment: null, createdAt: null, updatedAt: null };
describe('系统任务工作台', () => {
  it('详情展示业务字段、前后差异及执行结果', async () => {
    vi.mocked(systemTasksApi.detail).mockResolvedValue({ ...item, payload: {
      fields: [{ label: '功能编码', value: 'TEST_FEATURE' }],
      differences: [{ label: '开关状态', before: 'DISABLED', after: 'ENABLED' }],
      executionStatus: 'FAILED', failureReason: '执行未成功', notice: null
    } } as typeof item);
    render(<SystemTaskWorkbench currentUser={user} onNavigate={vi.fn()} />);
    await screen.findByText('清理缓存申请');
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
    expect(await screen.findByText('TEST_FEATURE')).toBeInTheDocument();
    expect(screen.getByText('DISABLED')).toBeInTheDocument();
    expect(screen.getByText('ENABLED')).toBeInTheDocument();
    expect(screen.getByText('执行未成功')).toBeInTheDocument();
  });
  it('全局开关仅为具备领域读取和审批权限的审核员提供处理入口', () => {
    const task = { ...item, type: 'GLOBAL_FEATURE_TOGGLE' };
    const reviewer = { ...user, permissionCodes: ['SYSTEM_TASK_READ', 'FEATURE_TOGGLE_READ', 'FEATURE_TOGGLE_REVIEW'] };
    expect(systemTaskDestination(task, reviewer, caps)).toBe('/feature-management');
    expect(systemTaskDestination(task, { ...reviewer, permissionCodes: ['SYSTEM_TASK_READ', 'FEATURE_TOGGLE_REVIEW'] }, caps)).toBeNull();
    expect(systemTaskDestination(task, { ...reviewer, roleCodes: ['SYS_ADMIN'] }, caps)).toBeNull();
    expect(systemTaskDestination({ ...task, status: 'EFFECTIVE' }, reviewer, caps)).toBeNull();
  });
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(authApi.currentUser).mockResolvedValue(user);
    vi.mocked(capabilityApi.web).mockResolvedValue(caps);
    vi.mocked(systemTasksApi.list).mockResolvedValue({ items: [item], page: 1, pageSize: 20, total: 41 });
    vi.mocked(systemTasksApi.detail).mockResolvedValue(item);
  });
  it('使用服务端总数分页，并复核身份后展示详情和领域入口', async () => {
    const navigate = vi.fn();
    render(<SystemTaskWorkbench currentUser={user} onNavigate={navigate} />);
    expect(await screen.findByText('清理缓存申请')).toBeInTheDocument();
    expect(screen.getByText('共 41 条')).toBeInTheDocument();
    fireEvent.click(screen.getByTitle('2'));
    await waitFor(() => expect(systemTasksApi.list).toHaveBeenLastCalledWith({ page: 2, pageSize: 20, status: undefined }));
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
    expect(await screen.findByText('变更说明')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '前往领域处理页' }));
    expect(navigate).toHaveBeenCalledWith('/cache-management');
    expect(authApi.currentUser).toHaveBeenCalledTimes(3);
  });
  it('焦点恢复时撤权清空列表和详情', async () => {
    render(<SystemTaskWorkbench currentUser={user} onNavigate={vi.fn()} />);
    await screen.findByText('清理缓存申请');
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
    await screen.findByText('变更说明');
    vi.mocked(authApi.currentUser).mockResolvedValue({ ...user, permissionCodes: [] });
    fireEvent.focus(window);
    await screen.findByText('当前会话无系统任务读取权限');
    expect(screen.queryByText('清理缓存申请')).not.toBeInTheDocument();
    expect(screen.queryByText('变更说明')).not.toBeInTheDocument();
  });
  it('查询失败清空旧数据并可重试', async () => {
    render(<SystemTaskWorkbench currentUser={user} onNavigate={vi.fn()} />);
    await screen.findByText('清理缓存申请');
    vi.mocked(systemTasksApi.list).mockRejectedValueOnce(new Error('网络失败'));
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    await screen.findByText('网络失败');
    expect(screen.queryByText('清理缓存申请')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText('清理缓存申请')).toBeInTheDocument();
  });
  it.each([{ ...user, roleCodes: ['PARENT'] }, { ...user, clientType: 'MINIAPP' }, { ...user, userId: 'other' }])('拒绝普通角色、跨端或身份变更', async changed => {
    vi.mocked(authApi.currentUser).mockResolvedValue(changed as CurrentUser);
    render(<SystemTaskWorkbench currentUser={user} onNavigate={vi.fn()} />);
    await screen.findByText('当前会话无系统任务读取权限');
    expect(systemTasksApi.list).not.toHaveBeenCalled();
  });
});
