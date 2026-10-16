import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { MenuConfigurationProvider } from '../../app/MenuConfiguration';
import type { MenuNode } from '../../api/menus';
import { UserManagementPage } from './UserManagementPage';

const usersApi = vi.hoisted(() => ({
  scopeOrganizations: vi.fn(),
  list: vi.fn(),
  create: vi.fn(),
  updateStatus: vi.fn(),setPassword:vi.fn()
}));

vi.mock('../../api/users', () => ({ usersApi }));
const orgApi=vi.hoisted(()=>({listTree:vi.fn()}));
vi.mock('../../api/organization',()=>({organizationApi:orgApi}));

function renderPage(roleCodes=['SYS_ADMIN'],permissionCodes=['IAM_USER_PASSWORD_SET','IAM_USER_STATUS_CHANGE']) {
  return render(<ConfigProvider locale={zhCN}><UserManagementPage currentUser={{roleCodes,permissionCodes}} /></ConfigProvider>);
}

describe('用户管理页面', () => {
  it('普通账号显示多组织身份且可查看身份归属',async()=>{
    usersApi.list.mockResolvedValueOnce({items:[{id:'multi',username:'multi',displayName:'多身份用户',type:'PERSONAL',status:'ENABLED',roleIdentities:[{roleCode:'PARENT_ADMIN',roleName:'家庭管理员',organizationId:'family',organizationName:'家庭甲'},{roleCode:'TEACHER',roleName:'教师',organizationId:'school',organizationName:'学校甲'}]}],page:1,pageSize:10,total:1});
    renderPage();
    await screen.findByText('multi');
    expect(screen.getByText('普通账号')).toBeInTheDocument();
    expect(screen.getByText('家庭管理员')).toBeInTheDocument();
    expect(screen.getByText('教师')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button',{name:'多身份用户组织身份'}));
    expect(await screen.findByText('家庭甲')).toBeInTheDocument();
    expect(screen.getByText('学校甲')).toBeInTheDocument();
    expect(screen.queryByText('待入驻账号')).not.toBeInTheDocument();
  });
  it('按实际身份筛选并交由服务端分页',async()=>{
    renderPage();await screen.findByText('目录张老师');
    await userEvent.click(screen.getByLabelText('组织身份'));
    await userEvent.click(await screen.findByText('家庭管理员'));
    await userEvent.click(screen.getByRole('button',{name:'查询'}));
    await waitFor(()=>expect(usersApi.list).toHaveBeenLastCalledWith(expect.objectContaining({identityRoleCode:'PARENT_ADMIN',page:1})));
  });
  beforeEach(() => {
    usersApi.scopeOrganizations.mockImplementation(async()=>{const flatten=(nodes:any[]):any[]=>nodes.flatMap(n=>[{...n,parentId:n.parentId??null,disabled:false},...flatten(n.children??[])]);return flatten(await orgApi.listTree());});
    orgApi.listTree.mockResolvedValue([{id:'org',name:'测试学校',status:'ENABLED',effectiveStatus:'ENABLED',children:[]}]);
    usersApi.list.mockResolvedValue({
      items: [{
        id: '1874244142494646324',
        username: 'directory_teacher',
        displayName: '目录张老师',
        organizationNames: ['测试学校', '第二机构'],
        mobile: '138****8000',
        type: 'ORGANIZATION',
        status: 'ENABLED',
        createdAt: '2026-08-01T09:00:00',
        updatedAt: '2026-08-01T09:00:00'
      }],
      page: 1,
      pageSize: 10,
      total: 1
    });
    usersApi.create.mockResolvedValue({
      id: '1874244142494646325',
      username: 'new_platform_user',
      displayName: '新建平台用户',
      mobile: null,
      type: 'PLATFORM',
      status: 'ENABLED',
      createdAt: '2026-08-01T09:00:00',
      updatedAt: '2026-08-01T09:00:00'
    });
    usersApi.updateStatus.mockResolvedValue({
      id: '1874244142494646324',
      username: 'directory_teacher',
      displayName: '目录张老师',
      mobile: '138****8000',
      type: 'ORGANIZATION',
      status: 'DISABLED',
      createdAt: '2026-08-01T09:00:00',
      updatedAt: '2026-08-01T09:01:00'
    });
  });

  it('切换容量重置第一页并在筛选和后续翻页请求中保留容量', async () => {
    const user = userEvent.setup();
    usersApi.list.mockImplementation(async (query) => ({ items: [], page: query.page, pageSize: query.pageSize, total: 120 }));
    renderPage();
    await waitFor(() => expect(usersApi.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 10 }));
    await user.click(screen.getByTitle('3'));
    await waitFor(() => expect(usersApi.list).toHaveBeenLastCalledWith({ page: 3, pageSize: 10 }));
    await user.click(screen.getByRole('combobox', { name: '页码' }));
    await user.click(screen.getByTitle('50 条/页'));
    await waitFor(() => expect(usersApi.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 50 }));
    await user.type(screen.getByLabelText('账号或名称'), '张老师');
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(usersApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '张老师', page: 1, pageSize: 50 })));
    await user.click(screen.getByTitle('2'));
    await waitFor(() => expect(usersApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '张老师', page: 2, pageSize: 50 })));
  });

  it('按关键字查询用户目录', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('目录张老师')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '机构名称' })).toBeInTheDocument();
    expect(screen.getByText('测试学校、第二机构')).toBeInTheDocument();
    expect(screen.getByText('138****8000')).toBeInTheDocument();

    await user.type(screen.getByLabelText('账号或名称'), '张老师');
    await user.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() => {
      expect(usersApi.list).toHaveBeenLastCalledWith({ keyword: '张老师', page: 1, pageSize: 10 });
    });
  });

  it('查询后重置并显示文字状态操作', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('目录张老师');
    expect(screen.getByRole('radio', { name: '停用' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '锁定' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('账号或名称'), '张老师');
    await user.click(screen.getByRole('button', { name: '查询' }));
    await user.click(screen.getByRole('button', { name: /^重\s*置$/ }));
    expect(screen.getByLabelText('账号或名称')).toHaveValue('');
    await waitFor(() => expect(usersApi.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 10 }));
  });

  it('状态单选通过已有接口更新账号', async () => {
    const user=userEvent.setup(); renderPage(); await screen.findByText('目录张老师');
    await user.click(screen.getByRole('radio',{name:'停用'}));
    await waitFor(()=>expect(usersApi.updateStatus).toHaveBeenCalledWith('1874244142494646324','DISABLED'));
  });
  it('没有状态权限时不显示单选', async () => {
    renderPage([],[]); await screen.findByText('目录张老师'); expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });
  it('按钮配置停用时不显示用户状态单选', async () => {
    render(<MenuConfigurationProvider nodes={[{id:'status',code:'IAM_USER_STATUS_CHANGE',type:'BUTTON',status:'DISABLED'} as MenuNode]} refresh={async()=>{}}><UserManagementPage currentUser={{roleCodes:[],permissionCodes:['IAM_USER_STATUS_CHANGE']}} /></MenuConfigurationProvider>);
    await screen.findByText('目录张老师'); expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });
  it('停用账号禁用锁定单选', async () => {
    const response=await usersApi.list(); usersApi.list.mockResolvedValue({...response,items:response.items.map((item:any)=>({...item,status:'DISABLED'}))});
    renderPage(); await screen.findByText('目录张老师'); expect(screen.getByRole('radio',{name:'锁定'})).toBeDisabled(); expect(screen.getByRole('radio',{name:'解锁'})).toBeDisabled();
  });

  it('展示已注销用户且不提供状态变更操作', async () => {
    usersApi.list.mockResolvedValue({
      items: [{
        id: '1874244142494646326',
        username: 'cancelled_1874244142494646326',
        displayName: '已注销用户',
        mobile: null,
        type: 'FAMILY',
        status: 'CANCELLED',
        createdAt: '2026-08-01T09:00:00',
        updatedAt: '2026-08-14T09:00:00'
      }],
      page: 1,
      pageSize: 10,
      total: 1
    });

    renderPage();

    const name = await screen.findByText('已注销用户');
    const row = name.closest('tr');
    if (!(row instanceof HTMLElement)) {
      throw new Error('未找到已注销用户行');
    }
    expect(within(row).getByText('已注销')).toBeInTheDocument();
    expect(within(row).queryByRole('button')).not.toBeInTheDocument();
  });
  it('按所属组织机构查询并在重置时清除组织条件',async()=>{
    const user=userEvent.setup();renderPage();await screen.findByText('目录张老师');
    await user.click(screen.getByLabelText('所属组织机构'));await user.click(await screen.findByText('测试学校'));
    await user.click(screen.getByRole('button',{name:'查询'}));
    await waitFor(()=>expect(usersApi.list).toHaveBeenLastCalledWith(expect.objectContaining({organizationId:'org',page:1,pageSize:10})));
    await user.click(screen.getByRole('button',{name:/^重\s*置$/}));
    await waitFor(()=>expect(usersApi.list).toHaveBeenLastCalledWith(expect.objectContaining({organizationId:undefined,page:1})));
  });
  it('自定义角色持密码权限可重置密码',async()=>{
    renderPage(['ALL_ROLE_TEST']);await screen.findByText('目录张老师');expect(screen.getByRole('button',{name:'重置密码'})).toBeInTheDocument();
  });
  it('学生账号不展示通用密码重置',async()=>{
    const response=await usersApi.list();usersApi.list.mockResolvedValue({...response,items:response.items.map((item:any)=>({...item,type:'STUDENT'}))});
    renderPage();await screen.findByText('目录张老师');expect(screen.queryByRole('button',{name:'重置密码'})).not.toBeInTheDocument();
  });
});

vi.mock('antd',async()=>{const actual=await vi.importActual<typeof import('antd')>('antd');return {...actual,message:{...actual.message,success:vi.fn(),error:vi.fn()}};});
