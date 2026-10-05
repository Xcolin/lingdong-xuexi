import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RolePermissionTreeModal } from './RolePermissionTreeModal';
import { ApiRequestError } from '../../api/http';
vi.mock('antd',async importOriginal=>({...await importOriginal<typeof import('antd')>(),message:{success:vi.fn(),error:vi.fn(),destroy:vi.fn()}}));
const api = vi.hoisted(() => ({ listRolePermissions: vi.fn(), batchRolePermissions: vi.fn(), listRolePermissionMenus: vi.fn() }));
const menus = vi.hoisted(() => ({ list: vi.fn() }));
vi.mock('../../api/iam', () => ({ iamApi: api }));
vi.mock('../../api/menus', () => ({ menuApi: menus }));
const roles = [{ id: 'r1', name: '角色一', code: 'R1' }, { id: 'r2', name: '角色二', code: 'R2' }] as any;
const permissions = [{ id: 'p1', code: 'READ', name: '页面权限', status: 'ENABLED' }, { id: 'p2', code: 'EDIT', name: '编辑权限', status: 'ENABLED' }] as any;
beforeEach(() => {
  vi.resetAllMocks();
  menus.list.mockResolvedValue([{ id: 'd', name: '管理目录', type: 'DIRECTORY', parentId: null }, { id: 'p', name: '管理页面', type: 'PAGE', parentId: 'd', permissionCode: 'READ', grantable: true }, { id: 'b', name: '编辑按钮', type: 'BUTTON', parentId: 'p', permissionCode: 'EDIT', grantable: true }]);
  api.listRolePermissions.mockResolvedValue([]);
  api.batchRolePermissions.mockResolvedValue(undefined);
});
it('目录级联一次保存页面和按钮，角色授权不出现单条效果选择', async () => {
  const user = userEvent.setup();
  render(<RolePermissionTreeModal open roles={roles} permissions={permissions} onClose={() => {}} />);
  await user.click(screen.getByLabelText('角色'));
  await user.click(screen.getByText('角色一（R1）'));
  const title = await screen.findByText('管理目录');
  await user.click(title.closest('.ant-tree-treenode')!.querySelector('.ant-tree-checkbox')!);
  await user.click(screen.getByRole('button', { name: '保存角色权限' }));
  await waitFor(() => expect(api.batchRolePermissions).toHaveBeenCalledWith('r1', {
    permissionIds: ['p1', 'p2'], managedPermissionIds: ['p1', 'p2'], expectedAssignments: []
  }));
  expect(screen.queryByText('禁止')).not.toBeInTheDocument();
});
it('晚到的旧角色响应不覆盖新角色', async () => {
  const user = userEvent.setup();
  let resolveFirst!: (value: any) => void;
  api.listRolePermissions.mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve; })).mockResolvedValueOnce([{ permissionId: 'p2', effect: 'ALLOW' }]);
  render(<RolePermissionTreeModal open roles={roles} permissions={permissions} onClose={() => {}} />);
  await user.click(screen.getByLabelText('角色'));
  await user.click(screen.getByText('角色一（R1）'));
  await user.click(screen.getByLabelText('角色'));
  await user.click(screen.getByText('角色二（R2）'));
  await screen.findByText('管理目录');
  resolveFirst([{ permissionId: 'p1', effect: 'ALLOW' }]);
  await user.click(screen.getByRole('button', { name: '保存角色权限' }));
  await waitFor(() => expect(api.batchRolePermissions).toHaveBeenCalledWith('r2', expect.objectContaining({ permissionIds: ['p2'], expectedAssignments: [{ permissionId: 'p2', effect: 'ALLOW' }] })));
});
it('菜单读取故障禁用保存，403通过IAM受控端点完整读取后才能保存', async () => {
  const user = userEvent.setup();
  menus.list.mockRejectedValueOnce(new Error('菜单加载故障')).mockRejectedValueOnce(new ApiRequestError(403, 'ACCESS_DENIED', '缺少菜单管理查询权限'));
  api.listRolePermissionMenus.mockResolvedValue([]);
  render(<RolePermissionTreeModal open roles={roles} permissions={permissions} onClose={() => {}} />);
  await user.click(screen.getByLabelText('角色'));
  await user.click(screen.getByText('角色一（R1）'));
  expect(await screen.findByText('菜单加载故障')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存角色权限' })).toBeDisabled();
  expect(api.listRolePermissionMenus).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '重新加载' }));
  await screen.findByText('其他权限');
  expect(api.listRolePermissionMenus).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: '保存角色权限' })).not.toBeDisabled();
});
it('保存中禁止角色切换和重复提交，保存失败保留勾选便于重试', async () => {
  const user = userEvent.setup();
  let reject!: (error: Error) => void;
  api.batchRolePermissions.mockImplementationOnce(() => new Promise((_, fail) => { reject = fail; }));
  render(<RolePermissionTreeModal open roles={roles} permissions={permissions} onClose={() => {}} />);
  await user.click(screen.getByLabelText('角色'));
  await user.click(screen.getByText('角色一（R1）'));
  const title = await screen.findByText('管理目录');
  await user.click(title.closest('.ant-tree-treenode')!.querySelector('.ant-tree-checkbox')!);
  await user.dblClick(screen.getByRole('button', { name: '保存角色权限' }));
  expect(api.batchRolePermissions).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('combobox', { name: '角色' })).toBeDisabled();
  reject(new Error('保存故障'));
  expect(await screen.findByText('保存故障')).toBeInTheDocument();
  expect(title.closest('.ant-tree-treenode')!.querySelector('.ant-tree-checkbox')).toHaveClass('ant-tree-checkbox-checked');
  await user.click(screen.getByRole('button', { name: '保存角色权限' }));
  await waitFor(() => expect(api.batchRolePermissions).toHaveBeenCalledTimes(2));
});
