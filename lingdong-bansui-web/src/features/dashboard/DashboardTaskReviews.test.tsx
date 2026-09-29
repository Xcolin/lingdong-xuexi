import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { authApi } from '../../api/auth';
import { capabilityApi } from '../../api/capability';
import { taskReviewApi } from '../learning-tasks/reviewApi';
import { DashboardTaskReviews } from './DashboardTaskReviews';
vi.mock('../../api/auth', () => ({ authApi: { currentUser: vi.fn() } }));
vi.mock('../../api/capability', () => ({ capabilityApi: { web: vi.fn() } }));
vi.mock('../learning-tasks/reviewApi', () => ({ taskReviewApi: { list: vi.fn() } }));
const user = { userId: '1874244142494699001', sessionId: '1874244142494699002', username: 'parent', displayName: '家长', clientType: 'WEB' as const,
  roleCodes: ['PARENT'], permissionCodes: ['TASK_ASSIGNMENT_REVIEW'] };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(authApi.currentUser).mockResolvedValue(user);
  vi.mocked(capabilityApi.web).mockResolvedValue({ client: 'WEB', learningTaskManagementEnabled: true } as Awaited<ReturnType<typeof capabilityApi.web>>);
  vi.mocked(taskReviewApi.list).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
});
it('实时核验后复用真实本人待审核查询，零数据才显示空态', async () => {
  render(<DashboardTaskReviews userId={user.userId} />);
  expect(await screen.findByText('暂无审核待办')).toBeInTheDocument();
  expect(taskReviewApi.list).toHaveBeenCalledWith(1,20);
});
it('系统审核员混合家长角色也不加载业务队列', async () => {
  vi.mocked(authApi.currentUser).mockResolvedValue({ ...user, roleCodes: ['PARENT', 'SYS_AUDITOR'] });
  render(<DashboardTaskReviews userId={user.userId} />);
  await screen.findByText('当前无任务审核权限或功能未开启。');
  expect(taskReviewApi.list).not.toHaveBeenCalled();
});
it('待办请求失败不显示已经查明的空态', async () => {
  vi.mocked(taskReviewApi.list).mockRejectedValue(new Error('待办查询失败'));
  render(<DashboardTaskReviews userId={user.userId} />);
  await screen.findByText('待办查询失败');
  expect(screen.queryByText('暂无审核待办')).not.toBeInTheDocument();
});
it('恢复窗口时撤回权限并卸载旧队列', async () => {
  render(<DashboardTaskReviews userId={user.userId} />);
  await screen.findByText('暂无审核待办');
  vi.mocked(authApi.currentUser).mockResolvedValue({ ...user, permissionCodes: [] });
  await act(async () => { window.dispatchEvent(new Event('focus')); });
  await waitFor(() => expect(screen.queryByText('暂无审核待办')).not.toBeInTheDocument());
  await screen.findByText('当前无任务审核权限或功能未开启。');
  expect(taskReviewApi.list).toHaveBeenCalledTimes(1);
});
