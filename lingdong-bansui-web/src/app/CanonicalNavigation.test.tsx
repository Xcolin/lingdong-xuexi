import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { App } from './App';

const auth = vi.hoisted(() => ({ currentUser: vi.fn(), hasLocalSession: vi.fn(), clearLocalSession: vi.fn(), changePassword: vi.fn() }));
const capability = vi.hoisted(() => ({ web: vi.fn() }));
const menus = vi.hoisted(() => ({ current: vi.fn() }));
vi.mock('../api/auth', () => ({ authApi: auth }));
vi.mock('../api/capability', () => ({ capabilityApi: capability }));
vi.mock('../api/menus', () => ({ menuApi: menus }));
vi.mock('../features/dashboard/DashboardPage', () => ({ DashboardPage: () => <div>缓存的工作台页面</div> }));
vi.mock('../features/auth/LoginPage', () => ({ LoginPage: () => <div>登录页面</div> }));

vi.mock('../features/users/UserManagementPage', () => ({ UserManagementPage: () => <div>用户列表内容</div> }));

beforeEach(() => {
  vi.clearAllMocks();
  auth.hasLocalSession.mockReturnValue(true);
  auth.currentUser.mockResolvedValue({ userId: '1', sessionId: '2', username: 'admin', displayName: '系统管理员', clientType: 'WEB', roleCodes: ['SYS_ADMIN'], permissionCodes: ['DASHBOARD_READ', 'IAM_USER_LIST'] });
  capability.web.mockResolvedValue({ client: 'WEB' });
  menus.current.mockResolvedValue([
    { id: '1', code: 'DASHBOARD_READ', permissionCode: 'DASHBOARD_READ', name: '工作台', type: 'PAGE', parentId: null, route: '/dashboard', sortOrder: 1, status: 'ENABLED', version: '1' },
    { id: '2', code: 'IAM_DIRECTORY', name: '权限管理', type: 'DIRECTORY', parentId: null, route: null, sortOrder: 2, status: 'ENABLED', version: '1' },
    { id: '3', code: 'IAM_USER_LIST', permissionCode: 'IAM_USER_LIST', name: '用户管理', type: 'PAGE', parentId: '2', route: '/users', sortOrder: 1, status: 'ENABLED', version: '1' }
  ]);
});
it('规范权限编码与URL不同时显示菜单、工作台并能打开用户页面', async () => {
  const user = userEvent.setup();
  render(<MemoryRouter initialEntries={['/dashboard']}><App /></MemoryRouter>);
  expect(await screen.findByText('缓存的工作台页面')).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '工作台' })).toBeInTheDocument();
  await user.click(screen.getByRole('menuitem', { name: '权限管理' }));
  await user.click(await screen.findByRole('menuitem', { name: '用户管理' }));
  expect(await screen.findByText('用户列表内容')).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '用户管理' })).toHaveAttribute('aria-selected', 'true');
});
