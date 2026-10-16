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
  updateRole: vi.fn(), updateRoleStatus: vi.fn(), deleteRole: vi.fn(),
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
    menuApi.list.mockResolvedValue([{id:'users',name:'用户管理',type:'PAGE',parentId:null,permissionCode:'IAM_USER_READ',grantable:true,sortOrder:1}]);
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
    nodes.push({id:'iam-page',code:'IAM_ROLE_READ',permissionCode:'IAM_ROLE_READ',name:'角色与权限',type:'PAGE',parentId:null,route:'/iam',icon:null,sortOrder:0,status:'ENABLED',version:'1'} as MenuNode);
    render(<MenuConfigurationProvider nodes={nodes} permissionCodes={['IAM_ROLE_READ','IAM_ROLE_CREATE','IAM_ROLE_PERMISSION_GRANT']} refresh={async()=>{}}><IamManagementPage/></MenuConfigurationProvider>);
    await screen.findByText('系统管理员');
    expect(screen.getByRole('button',{name:'新增角色'})).toBeVisible();
    expect(screen.queryByRole('button',{name:'配置角色权限'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'配置用户权限'})).not.toBeInTheDocument();
  });
  it('保留角色查询和授权，移除独立权限目录与新增权限', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('系统管理员');
    expect(screen.queryByRole('tab', { name: '权限目录' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增权限' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '配置角色权限' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '配置用户权限' })).not.toBeInTheDocument();
    expect(usersApi.list).not.toHaveBeenCalled();
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
    expect(await within(roleDialog).findByText('用户管理')).toBeInTheDocument();
    expect(within(roleDialog).queryByText('其他权限')).not.toBeInTheDocument();
    expect(within(roleDialog).queryByLabelText('权限')).not.toBeInTheDocument();
    expect(within(roleDialog).queryByRole('button', { name: '撤销 查询用户' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '保存角色权限' }));

    await waitFor(() => expect(iamApi.batchRolePermissions).toHaveBeenCalledWith(
      '1874244142494646273', { permissionIds: ['1874244142494646279'], managedPermissionIds: ['1874244142494646279'], expectedAssignments: [{ permissionId: '1874244142494646279', effect: 'ALLOW' }] }
    ));
  });

  it('内置角色禁止编辑删除启停用，授权和关联保留', async () => {
    render(<IamManagementPage permissionCodes={['IAM_ROLE_READ','IAM_ROLE_UPDATE','IAM_ROLE_DELETE','IAM_ROLE_STATUS_CHANGE']} />);
    const row = (await screen.findByText('系统管理员')).closest('tr')!;
    expect(within(row).getByRole('button', { name: /编\s*辑/ })).toBeDisabled();
    expect(within(row).getByRole('button', { name: /删\s*除/ })).toBeDisabled();
    within(row).getAllByRole('radio').forEach(radio => expect(radio).toBeDisabled());
    expect(within(row).getByRole('button', { name: /授\s*权/ })).toBeEnabled();
    expect(within(row).getByRole('button', { name: '关联用户' })).toBeEnabled();
  });
  it('系统管理员可编辑内置角色', async () => {
    render(<IamManagementPage roleCodes={['SYS_ADMIN']} permissionCodes={['IAM_ROLE_READ','IAM_ROLE_UPDATE']} />);
    const row = (await screen.findByText('系统管理员')).closest('tr')!;
    expect(within(row).getByRole('button', { name: /编\s*辑/ })).toBeEnabled();
  });
  it('自定义角色编辑只提交名称和说明，启停用成功刷新', async () => {
    iamApi.listRoles.mockResolvedValue([{ id:'custom',code:'CUSTOM',name:'自定义角色',type:'CUSTOM',builtIn:false,dataScope:'SELF',description:'原说明',status:'ENABLED' }]);
    iamApi.updateRole.mockResolvedValue({}); iamApi.updateRoleStatus.mockResolvedValue({});
    const user=userEvent.setup();
    render(<IamManagementPage permissionCodes={['IAM_ROLE_READ','IAM_ROLE_UPDATE','IAM_ROLE_STATUS_CHANGE']} />);
    const row=(await screen.findByText('自定义角色')).closest('tr')!;
    await user.click(within(row).getByRole('button',{name:/编\s*辑/}));
    const dialog=screen.getByRole('dialog',{name:'编辑角色'});
    expect(within(dialog).getByLabelText('角色编码')).toHaveAttribute('readonly');
    await user.clear(within(dialog).getByLabelText('角色名称')); await user.type(within(dialog).getByLabelText('角色名称'),'新名称');
    await user.click(within(dialog).getByRole('button',{name:/保\s*存/}));
    await waitFor(()=>expect(iamApi.updateRole).toHaveBeenCalledWith('custom',{name:'新名称',description:'原说明'}));
    await waitFor(()=>expect(screen.queryByRole('dialog',{name:'编辑角色'})).not.toBeInTheDocument());
    await user.click(within(row).getByRole('radio',{name:'停用'}));
    await waitFor(()=>expect(iamApi.updateRoleStatus).toHaveBeenCalledWith('custom','DISABLED'));
  });
  it('删除确认后调用接口，关联用户报错时保留列表', async () => {
    iamApi.listRoles.mockResolvedValue([{id:'custom',code:'CUSTOM',name:'关联角色',type:'CUSTOM',builtIn:false,dataScope:'SELF',description:null,status:'ENABLED'}]);
    iamApi.deleteRole.mockRejectedValue(new Error('请先解绑关联用户'));
    const user=userEvent.setup(); render(<IamManagementPage permissionCodes={['IAM_ROLE_READ','IAM_ROLE_DELETE']} />);
    await screen.findByText('关联角色'); await user.click(screen.getByRole('button',{name:/删\s*除/}));
    expect(iamApi.deleteRole).not.toHaveBeenCalled();
    const popup=(await screen.findByText('确认删除角色“关联角色”？')).closest('.ant-popconfirm')!;
    await user.click(within(popup as HTMLElement).getByRole('button',{name:/^(OK|确\s*定)$/}));
    await waitFor(()=>expect(iamApi.deleteRole).toHaveBeenCalledWith('custom'));
    expect(screen.getByText('关联角色')).toBeInTheDocument(); expect(iamApi.listRoles).toHaveBeenCalledTimes(1);
  });

  it('没有业务权限或配置停用时不显示状态单选', async () => {
    const nodes=[{id:'s',code:'IAM_ROLE_STATUS_CHANGE',name:'状态',type:'BUTTON',status:'DISABLED'} as MenuNode];
    render(<MenuConfigurationProvider nodes={nodes} refresh={async()=>{}}><IamManagementPage permissionCodes={['IAM_ROLE_READ','IAM_ROLE_STATUS_CHANGE']} /></MenuConfigurationProvider>);
    await screen.findByText('系统管理员'); expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });

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
