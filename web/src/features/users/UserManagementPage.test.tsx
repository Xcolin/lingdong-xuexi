import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { UserManagementPage } from './UserManagementPage';

const usersApi = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  updateStatus: vi.fn()
}));

vi.mock('../../api/users', () => ({ usersApi }));

function renderPage() {
  return render(<ConfigProvider locale={zhCN}><UserManagementPage /></ConfigProvider>);
}

describe('用户管理页面', () => {
  beforeEach(() => {
    usersApi.list.mockResolvedValue({
      items: [{
        id: '1874244142494646324',
        username: 'directory_teacher',
        displayName: '目录张老师',
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
    expect(screen.getByText('138****8000')).toBeInTheDocument();

    await user.type(screen.getByLabelText('账号或名称'), '张老师');
    await user.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() => {
      expect(usersApi.list).toHaveBeenLastCalledWith({ keyword: '张老师', page: 1, pageSize: 20 });
    });
  });

  it('创建用户并停用用户账号', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('目录张老师')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '新增用户' }));
    await user.type(screen.getByLabelText('用户账号'), 'new_platform_user');
    await user.type(screen.getByLabelText('用户名称'), '新建平台用户');
    await user.click(screen.getByRole('button', { name: '创建用户' }));

    await waitFor(() => {
      expect(usersApi.create).toHaveBeenCalledWith({
        username: 'new_platform_user',
        displayName: '新建平台用户',
        mobile: undefined,
        type: 'PLATFORM'
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
});
