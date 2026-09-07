import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { IamManagementPage } from './IamManagementPage';

const iamApi = vi.hoisted(() => ({
  listRoles: vi.fn(),
  createRole: vi.fn(),
  listPermissions: vi.fn(),
  createPermission: vi.fn(),
  listRolePermissions: vi.fn(),
  configureRolePermission: vi.fn(),
  removeRolePermission: vi.fn(),
  listUserPermissions: vi.fn(),
  configureUserPermission: vi.fn(),
  removeUserPermission: vi.fn()
  ,listAudits: vi.fn()
}));

const usersApi = vi.hoisted(() => ({ list: vi.fn() }));

vi.mock('../../api/iam', () => ({ iamApi }));
vi.mock('../../api/users', () => ({ usersApi }));

function renderPage() {
  return render(<ConfigProvider locale={zhCN}><IamManagementPage /></ConfigProvider>);
}

describe('角色与权限管理页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    iamApi.listRoles.mockResolvedValue([{ id: '1874244142494646273', code: 'SYS_ADMIN', name: '系统管理员', type: 'BUILT_IN', dataScope: 'ALL', builtIn: true, status: 'ENABLED', description: null }]);
    iamApi.listPermissions.mockResolvedValue([{ id: '1874244142494646279', code: 'IAM_USER_READ', name: '查询用户', resourceType: 'OPERATION', client: 'WEB', parentId: null, status: 'ENABLED', description: null }]);
    usersApi.list.mockResolvedValue({ items: [{ id: '2088500000000000001', username: 'ops_user', displayName: '运维用户', mobile: null, type: 'PLATFORM', status: 'ENABLED', createdAt: '', updatedAt: '' }], page: 1, pageSize: 100, total: 1 });
    iamApi.listRolePermissions.mockResolvedValue([{ permissionId: '1874244142494646279', effect: 'ALLOW' }]);
    iamApi.listUserPermissions.mockResolvedValue([{ permissionId: '1874244142494646279', effect: 'DENY' }]);
    iamApi.configureRolePermission.mockResolvedValue(undefined);
    iamApi.removeRolePermission.mockResolvedValue(undefined);
    iamApi.configureUserPermission.mockResolvedValue(undefined);
    iamApi.removeUserPermission.mockResolvedValue(undefined);
    iamApi.listAudits.mockResolvedValue({
      items: [{
        id: '2088500000000000099', eventType: 'USER_CREATE', operatorId: '2088500000000000001',
        targetType: 'USER', targetId: '2088500000000000002', relatedId: null,
        organizationId: null, beforeValue: null, afterValue: 'PLATFORM', occurredAt: '2026-08-15T10:00:00'
      }],
      page: 1, pageSize: 10, total: 1
    });
  });

  it('查询并配置角色明确禁止权限', async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('系统管理员')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '配置角色权限' }));
    await user.click(screen.getByLabelText('角色'));
    await user.click(await screen.findByText('系统管理员（SYS_ADMIN）'));
    await waitFor(() => expect(iamApi.listRolePermissions).toHaveBeenCalledWith('1874244142494646273'));
    const roleDialog = screen.getByRole('dialog', { name: '配置角色权限' });
    expect(await within(roleDialog).findAllByText('允许')).toHaveLength(2);

    await user.click(screen.getByLabelText('权限'));
    await user.click(await screen.findByText('查询用户（IAM_USER_READ）'));
    await user.click(screen.getByText('禁止'));
    await user.click(screen.getByRole('button', { name: '保存角色权限' }));

    await waitFor(() => expect(iamApi.configureRolePermission).toHaveBeenCalledWith(
      '1874244142494646273', '1874244142494646279', 'DENY'
    ));
  });

  it('查询并撤销用户显式限制权限', async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('系统管理员')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '配置用户权限' }));
    await user.click(screen.getByLabelText('用户'));
    await user.click(await screen.findByText('运维用户（ops_user）'));
    await waitFor(() => expect(iamApi.listUserPermissions).toHaveBeenCalledWith('2088500000000000001'));

    const userDialog = screen.getByRole('dialog', { name: '配置用户权限' });
    const permissionName = await within(userDialog).findByText('查询用户');
    const row = permissionName.closest('tr');
    if (!(row instanceof HTMLElement)) throw new Error('未找到用户权限行');
    await user.click(within(row).getByRole('button', { name: '撤销 查询用户' }));
    const confirmation = await screen.findByText('确认撤销该显式权限？');
    const popup = confirmation.closest('.ant-popconfirm');
    if (!(popup instanceof HTMLElement)) throw new Error('未找到撤销确认弹层');
    await user.click(within(popup).getByRole('button', { name: /^(OK|确\s*定)$/ }));

    await waitFor(() => expect(iamApi.removeUserPermission).toHaveBeenCalledWith(
      '2088500000000000001', '1874244142494646279'
    ));
  });

  it('按事件类型查询身份权限变更审计', async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('权限变更日志')).toBeInTheDocument();
    await waitFor(() => expect(iamApi.listAudits).toHaveBeenCalledWith({ page: 1, pageSize: 10 }));
    expect(await screen.findByText('创建用户')).toBeInTheDocument();

    await user.click(screen.getByLabelText('审计事件'));
    await user.click(await screen.findByText('用户状态变更'));
    await user.click(screen.getByRole('button', { name: '查询审计日志' }));

    await waitFor(() => expect(iamApi.listAudits).toHaveBeenLastCalledWith({
      eventType: 'USER_STATUS_CHANGE', page: 1, pageSize: 10
    }));
  });
});
