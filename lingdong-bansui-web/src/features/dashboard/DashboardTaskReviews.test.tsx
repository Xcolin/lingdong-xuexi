import { MemoryRouter } from 'react-router-dom';
import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { authApi } from '../../api/auth';
import { capabilityApi } from '../../api/capability';
import { taskReviewApi } from '../learning-tasks/reviewApi';
import { DashboardTaskReviews } from './DashboardTaskReviews';
import { systemTasksApi } from '../../api/system-tasks';
vi.mock('../../api/auth', () => ({ authApi: { currentUser: vi.fn() } }));
vi.mock('../../api/capability', () => ({ capabilityApi: { web: vi.fn() } }));
vi.mock('../learning-tasks/reviewApi', () => ({ taskReviewApi: { list: vi.fn() } }));
vi.mock('../../api/system-tasks', () => ({ systemTasksApi: { list: vi.fn() } }));
const user = { userId: '1874244142494699001', sessionId: '1874244142494699002', username: 'parent', displayName: '家长', clientType: 'WEB' as const,
  roleCodes: ['PARENT'], permissionCodes: ['TASK_ASSIGNMENT_REVIEW'] };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(authApi.currentUser).mockResolvedValue(user);
  vi.mocked(capabilityApi.web).mockResolvedValue({ client: 'WEB', learningTaskManagementEnabled: true } as Awaited<ReturnType<typeof capabilityApi.web>>);
  vi.mocked(taskReviewApi.list).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 10 });
});
it('实时核验后复用真实本人待审核查询，零数据才显示空态', async () => {
  render(<MemoryRouter><DashboardTaskReviews userId={user.userId} /></MemoryRouter>);
  expect(await screen.findByText('暂无审核待办')).toBeInTheDocument();
  expect(taskReviewApi.list).toHaveBeenCalledWith(1,10);
});
it('系统审核员混合家长角色也不加载业务队列', async () => {
  vi.mocked(authApi.currentUser).mockResolvedValue({ ...user, roleCodes: ['ALL_ROLE_TEST'], permissionCodes: [] });
  render(<MemoryRouter><DashboardTaskReviews userId={user.userId} /></MemoryRouter>);
  await screen.findByText('当前无任务审核权限或功能未开启。');
  expect(taskReviewApi.list).not.toHaveBeenCalled();
});
it('待办请求失败不显示已经查明的空态', async () => {
  vi.mocked(taskReviewApi.list).mockRejectedValue(new Error('待办查询失败'));
  render(<MemoryRouter><DashboardTaskReviews userId={user.userId} /></MemoryRouter>);
  await screen.findByText('待办查询失败');
  expect(screen.queryByText('暂无审核待办')).not.toBeInTheDocument();
});
it('恢复窗口时撤回权限并卸载旧队列', async () => {
  render(<MemoryRouter><DashboardTaskReviews userId={user.userId} /></MemoryRouter>);
  await screen.findByText('暂无审核待办');
  vi.mocked(authApi.currentUser).mockResolvedValue({ ...user, permissionCodes: [] });
  await act(async () => { window.dispatchEvent(new Event('focus')); });
  await waitFor(() => expect(screen.queryByText('暂无审核待办')).not.toBeInTheDocument());
  await screen.findByText('当前无任务审核权限或功能未开启。');
  expect(taskReviewApi.list).toHaveBeenCalledTimes(1);
});



const systemUser = { ...user, roleCodes: ['SYS_ADMIN'], permissionCodes: ['SYSTEM_TASK_READ','ORGANIZATION_ONBOARDING_REVIEW'] };
const pendingSystemTask = {id:'pending-admission',code:'T',type:'ORGANIZATION_ONBOARDING',title:'学校入驻待审核',description:null,impactScope:'REGION',status:'PENDING_REVIEW' as const,submittedBy:'申请人',submittedById:'other-user',submittedAt:null,reviewedBy:null,reviewedAt:null,reviewComment:null,createdAt:null,updatedAt:null};
it('仅系统任务权限也显示入驻待审，使用服务端状态及总数', async () => {
  vi.mocked(authApi.currentUser).mockResolvedValue(systemUser);
  vi.mocked(capabilityApi.web).mockResolvedValue({client:'WEB',learningTaskManagementEnabled:false} as Awaited<ReturnType<typeof capabilityApi.web>>);
  vi.mocked(systemTasksApi.list).mockResolvedValue({items:[pendingSystemTask],total:7,page:1,pageSize:5});
  render(<MemoryRouter><DashboardTaskReviews userId={user.userId}/></MemoryRouter>);
  expect(await screen.findByText('学校入驻待审核')).toBeInTheDocument();
  expect(screen.getByText('系统任务（7）')).toBeInTheDocument();
  expect(systemTasksApi.list).toHaveBeenCalledWith({page:1,pageSize:5,status:'PENDING_REVIEW'});
  expect(taskReviewApi.list).not.toHaveBeenCalled();
  expect(screen.getByRole('button',{name:/审\s*核/})).toBeInTheDocument();
});
it('自己的待审任务显示查看，不能自审', async () => {
  vi.mocked(authApi.currentUser).mockResolvedValue(systemUser);
  vi.mocked(systemTasksApi.list).mockResolvedValue({items:[{...pendingSystemTask,submittedById:user.userId}],total:1,page:1,pageSize:5});
  render(<MemoryRouter><DashboardTaskReviews userId={user.userId}/></MemoryRouter>);
  expect(await screen.findByRole('button',{name:/查\s*看/})).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:/审\s*核/})).not.toBeInTheDocument();
});
it('系统待审加载失败不能显示空队列', async () => {
  vi.mocked(authApi.currentUser).mockResolvedValue(systemUser);
  vi.mocked(systemTasksApi.list).mockRejectedValue(new Error('系统待审加载失败'));
  render(<MemoryRouter><DashboardTaskReviews userId={user.userId}/></MemoryRouter>);
  expect(await screen.findByText('系统待审加载失败')).toBeInTheDocument();
  expect(screen.queryByText('暂无待审核系统任务')).not.toBeInTheDocument();
});
