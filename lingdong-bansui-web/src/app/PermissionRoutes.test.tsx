import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { App } from './App';
const mocks = vi.hoisted(() => ({ currentUser: vi.fn(), hasLocalSession: vi.fn(), clearLocalSession: vi.fn(), web: vi.fn(), current: vi.fn() }));
vi.mock('./WorkspaceTabs', () => ({ WorkspaceTabs: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock('../api/auth', () => ({ authApi: mocks }));
vi.mock('../api/capability', () => ({ capabilityApi: mocks }));
vi.mock('../api/menus', () => ({ menuApi: mocks }));
vi.mock('../features/dashboard/DashboardPage', () => ({ DashboardPage: () => <div>工作台测试页面</div> }));
vi.mock('../features/users/UserManagementPage', () => ({ UserManagementPage: () => <div>用户测试页面</div> }));
vi.mock('../features/iam/IamManagementPage', () => ({ IamManagementPage: () => <div>权限测试页面</div> }));
vi.mock('../features/menus/MenuManagementPage', () => ({ MenuManagementPage: () => <div>菜单测试页面</div> }));
vi.mock('../features/student-login/StudentLoginManagementPage', () => ({ StudentLoginManagementPage: () => <div>学生登录测试页面</div> }));
vi.mock('../features/teachers/TeacherManagementPage', () => ({ TeacherManagementPage: () => <div>教师测试页面</div> }));
vi.mock('../features/learning-tasks/LearningTaskManagementPage', () => ({ LearningTaskManagementPage: () => <div>任务测试页面</div> }));
const user = { userId: '1', sessionId: 's', username: 'custom', displayName: '自定义角色', clientType: 'WEB', roleCodes: ['ALL_ROLE_TEST'], permissionCodes: ['MENU_READ', 'TEACHER_READ', 'LEARNING_TASK_READ_MANAGED', 'IAM_USER_LIST', 'IAM_ROLE_READ', 'STUDENT_READ'] };
beforeEach(() => {
 vi.clearAllMocks(); mocks.hasLocalSession.mockReturnValue(true); mocks.currentUser.mockResolvedValue(user);
 mocks.web.mockResolvedValue({ client: 'WEB', studentQrLoginEnabled: true, teacherManagementEnabled: true, learningTaskManagementEnabled: true });
 mocks.current.mockResolvedValue([{ id: 'd', code: 'dashboard', name: '工作台', type: 'PAGE', route: '/dashboard', status: 'ENABLED', sortOrder: 1 }]);
});
it.each([['/student-login', '学生登录测试页面'], ['/users', '用户测试页面'], ['/iam', '权限测试页面'], ['/menu-management', '菜单测试页面'], ['/teachers', '教师测试页面'], ['/learning-tasks', '任务测试页面']])('自定义角色授权后可直接访问 %s', async (path, label) => {
 render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>); expect(await screen.findByText(label)).toBeInTheDocument();
});
it.each(['/student-login', '/users', '/iam', '/menu-management', '/teachers', '/learning-tasks'])('无权限直接访问 %s 返回工作台', async path => {
 mocks.currentUser.mockResolvedValue({ ...user, permissionCodes: [] });
 render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>); expect(await screen.findByText('工作台测试页面')).toBeInTheDocument();
});
it.each(['/student-login', '/teachers', '/learning-tasks'])('功能停用时直接访问 %s 返回工作台', async path => {
 mocks.web.mockResolvedValue({ client: 'WEB', teacherManagementEnabled: false, learningTaskManagementEnabled: false });
 render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>); expect(await screen.findByText('工作台测试页面')).toBeInTheDocument();
});
