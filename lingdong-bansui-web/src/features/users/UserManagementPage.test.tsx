import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { UserManagementPage } from './UserManagementPage';

const usersApi = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  updateStatus: vi.fn(),setPassword:vi.fn()
}));

vi.mock('../../api/users', () => ({ usersApi }));
const orgApi=vi.hoisted(()=>({listTree:vi.fn()}));
vi.mock('../../api/organization',()=>({organizationApi:orgApi}));

function renderPage(roleCodes=['SYS_ADMIN'],permissionCodes=['IAM_USER_PASSWORD_SET']) {
  return render(<ConfigProvider locale={zhCN}><UserManagementPage currentUser={{roleCodes,permissionCodes}} /></ConfigProvider>);
}

describe('用户管理页面', () => {
  beforeEach(() => {
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
      pageSize: 20,
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
      expect(usersApi.list).toHaveBeenLastCalledWith({ keyword: '张老师', page: 1, pageSize: 20 });
    });
  });

  it('查询后重置并显示文字状态操作', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('目录张老师');
    expect(screen.getByRole('button', { name: '停用 目录张老师' })).toHaveTextContent(/停\s*用/);
    expect(screen.getByRole('button', { name: '锁定 目录张老师' })).toHaveTextContent(/锁\s*定/);
    await user.type(screen.getByLabelText('账号或名称'), '张老师');
    await user.click(screen.getByRole('button', { name: '查询' }));
    await user.click(screen.getByRole('button', { name: /^重\s*置$/ }));
    expect(screen.getByLabelText('账号或名称')).toHaveValue('');
    await waitFor(() => expect(usersApi.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 20 }));
  });

  it('创建用户并停用用户账号', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('目录张老师')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '新增用户' }));
    await user.type(screen.getByLabelText('用户账号'), 'new_platform_user');
    await user.type(screen.getByLabelText('用户名称'), '新建平台用户');
    await user.type(screen.getByLabelText('初始密码'), 'Password12');
    await user.type(screen.getByLabelText('确认密码'), 'Password12');
    await user.click(screen.getByLabelText('所属组织'));
    await user.click((await screen.findAllByText('测试学校')).at(-1)!);
    await user.click(screen.getByRole('button', { name: '创建用户' }));

    await waitFor(() => {
      expect(usersApi.create).toHaveBeenCalledWith({
        username: 'new_platform_user',
        displayName: '新建平台用户',
        mobile: undefined,
        type: 'PLATFORM',
        organizationId:'org',password:'Password12'
      });
    });

    await user.click(screen.getByRole('button', { name: '停用 目录张老师' }));
    const confirmation = await screen.findByText('确认停用该用户？');
    const popup = confirmation.closest('.ant-popconfirm');
    if (!(popup instanceof HTMLElement)) {
      throw new Error('未找到用户状态确认弹层');
    }
    await user.click(within(popup).getByRole('button', { name: /^(OK|确\s*定)$/ }));

    await waitFor(() => {
      expect(usersApi.updateStatus).toHaveBeenCalledWith('1874244142494646324', 'DISABLED');
    });
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
      pageSize: 20,
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
    await waitFor(()=>expect(usersApi.list).toHaveBeenLastCalledWith(expect.objectContaining({organizationId:'org',page:1,pageSize:20})));
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
