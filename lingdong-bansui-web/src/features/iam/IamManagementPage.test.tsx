import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { MenuConfigurationProvider } from '../../app/MenuConfiguration';
import type { MenuNode } from '../../api/menus';
import { IamManagementPage } from './IamManagementPage';

const iamApi = vi.hoisted(() => ({
  listRoles: vi.fn(),
  createRole: vi.fn(),
  listPermissions: vi.fn(),
  createPermission: vi.fn(),
  listRolePermissions: vi.fn(),
  batchRolePermissions: vi.fn(),
  configureRolePermission: vi.fn(),
  removeRolePermission: vi.fn(),
  listUserPermissions: vi.fn(),
  userPermissionTree:vi.fn(),
  batchUserPermissions:vi.fn(),
  configureUserPermission: vi.fn(),
  removeUserPermission: vi.fn()
  ,listAudits: vi.fn()
}));

const usersApi = vi.hoisted(() => ({ list: vi.fn() }));
const menuApi = vi.hoisted(() => ({ list: vi.fn() }));

vi.mock('../../api/iam', () => ({ iamApi }));
vi.mock('../../api/users', () => ({ usersApi }));
vi.mock('../../api/menus', () => ({ menuApi }));

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
    iamApi.batchRolePermissions.mockResolvedValue(undefined);
    menuApi.list.mockResolvedValue([]);
    iamApi.userPermissionTree.mockResolvedValue({menus:[],inheritedPermissionIds:[],inheritedDeniedPermissionIds:[]});
    iamApi.batchUserPermissions.mockResolvedValue(undefined);
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

  it('使用实际迁移后的大写权限按钮编码展示角色和用户授权入口', async () => {
    const nodes = [
      ['IAM_ROLE_CREATE','新增角色'],
      ['IAM_ROLE_PERMISSION_GRANT','配置角色权限'],
      ['IAM_USER_PERMISSION_CONFIGURE','配置用户权限']
    ].map(([code,name],index)=>({id:String(index),code,name,type:'BUTTON',parentId:'iam-page',route:null,icon:null,permissionCode:code,grantable:true,sortOrder:index,status:'ENABLED',version:'1'} as MenuNode));
    render(<MenuConfigurationProvider nodes={nodes} refresh={async()=>{}}><IamManagementPage/></MenuConfigurationProvider>);
    await screen.findByText('系统管理员');
    expect(screen.getByRole('button',{name:'新增角色'})).toBeVisible();
    expect(screen.getByRole('button',{name:'配置角色权限'})).toBeVisible();
    expect(screen.getByRole('button',{name:'配置用户权限'})).toBeVisible();
  });
  it('保留角色查询和授权，移除独立权限目录与新增权限', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('系统管理员');
    expect(screen.queryByRole('tab', { name: '权限目录' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增权限' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '配置角色权限' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '配置用户权限' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('名称或编码'), '不存在');
    await user.click(screen.getByRole('button', { name: /^查\s*询$/ }));
    expect(screen.queryByText('系统管理员')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^重\s*置$/ }));
    expect(await screen.findByText('系统管理员')).toBeInTheDocument();
  });

  it('树形回显角色允许权限并一次保存，移除单条授权效果和撤销', async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('系统管理员')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /授\s*权/ }));
    expect(screen.getByLabelText('角色')).toBeDisabled();
    await waitFor(() => expect(iamApi.listRolePermissions).toHaveBeenCalledWith('1874244142494646273'));
    const roleDialog = screen.getByRole('dialog', { name: '配置角色权限' });
    const otherGroup = await within(roleDialog).findByText('其他权限');
    await user.click(otherGroup.closest('.ant-tree-treenode')!.querySelector('.ant-tree-switcher')!);
    expect(await within(roleDialog).findByText('查询用户（IAM_USER_READ）')).toBeInTheDocument();
    expect(within(roleDialog).queryByLabelText('权限')).not.toBeInTheDocument();
    expect(within(roleDialog).queryByRole('button', { name: '撤销 查询用户' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '保存角色权限' }));

    await waitFor(() => expect(iamApi.batchRolePermissions).toHaveBeenCalledWith(
      '1874244142494646273', { permissionIds: ['1874244142494646279'], managedPermissionIds: ['1874244142494646279'], expectedAssignments: [{ permissionId: '1874244142494646279', effect: 'ALLOW' }] }
    ));
  });

  it('用户树授权保留已有拒绝权限并统一保存',async()=>{const user=userEvent.setup();renderPage();await screen.findByText('系统管理员');await user.click(screen.getByRole('button',{name:'配置用户权限'}));await user.click(screen.getByLabelText('用户'));await user.click(await screen.findByText('运维用户（ops_user）'));const dialog=screen.getByRole('dialog',{name:'配置用户权限'});const group=await within(dialog).findByText('其他权限');await user.click(group.closest('.ant-tree-treenode')!.querySelector('.ant-tree-switcher')!);const title=await within(dialog).findByText('查询用户（IAM_USER_READ）（已禁止，保留）');expect(title.closest('.ant-tree-treenode')!.querySelector('.ant-tree-checkbox')).toHaveClass('ant-tree-checkbox-disabled');await user.click(within(dialog).getByRole('button',{name:'保存用户权限'}));await waitFor(()=>expect(iamApi.batchUserPermissions).toHaveBeenCalledWith('2088500000000000001',{permissionIds:[],managedPermissionIds:[],expectedAssignments:[{permissionId:'1874244142494646279',effect:'DENY'}]}));expect(iamApi.removeUserPermission).not.toHaveBeenCalled();});

  it('按事件类型查询身份权限变更审计', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole('tab', { name: '授权审计' }));
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

vi.mock('antd',async()=>{const actual=await vi.importActual<typeof import('antd')>('antd');return {...actual,message:{...actual.message,success:vi.fn(),error:vi.fn()}};});
