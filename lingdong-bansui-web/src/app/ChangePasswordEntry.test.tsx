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

beforeEach(() => {
  vi.clearAllMocks();
  auth.hasLocalSession.mockReturnValue(true);
  auth.currentUser.mockResolvedValue({ userId: '1', sessionId: '2', username: 'admin', displayName: '系统管理员', clientType: 'WEB', roleCodes: ['SYS_ADMIN'], permissionCodes: [] });
  capability.web.mockResolvedValue({ client: 'WEB' });
  menus.current.mockResolvedValue([{ id: '3', code: 'dashboard', name: '工作台', type: 'PAGE', parentId: null, route: '/dashboard', sortOrder: 10, status: 'ENABLED', version: '1' }]);
  auth.changePassword.mockResolvedValue(undefined);
});

it('账号菜单提供修改密码入口，取消清空表单，成功回登录并销毁缓存', async () => {
  const user = userEvent.setup();
  render(<MemoryRouter initialEntries={['/dashboard']}><App /></MemoryRouter>);
  expect(await screen.findByText('缓存的工作台页面')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '系统管理员' }));
  await user.click(await screen.findByRole('menuitem', { name: '修改密码' }));
  await user.type(screen.getByLabelText('旧密码', { exact: true }), 'Discarded123');
  await user.click(screen.getByRole('button', { name: '取消' }));
  await waitFor(() => expect(screen.queryByRole('dialog', { name: '修改密码' })).not.toBeInTheDocument());
  await user.click(screen.getByRole('button', { name: '系统管理员' }));
  await user.click(await screen.findByRole('menuitem', { name: '修改密码' }));
  expect(screen.getByLabelText('旧密码', { exact: true })).toHaveValue('');
  await user.type(screen.getByLabelText('旧密码', { exact: true }), 'OldPassword123');
  await user.type(screen.getByLabelText('新密码', { exact: true }), 'NewPassword456');
  await user.type(screen.getByLabelText('确认新密码', { exact: true }), 'NewPassword456');
  await user.click(screen.getByRole('button', { name: '确认修改' }));
  expect(await screen.findByText('登录页面')).toBeInTheDocument();
  expect(auth.clearLocalSession).toHaveBeenCalled();
  expect(screen.queryByText('缓存的工作台页面')).not.toBeInTheDocument();
});
