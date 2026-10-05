import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { studentLoginApi } from './api';
import { StudentLoginManagementPage } from './StudentLoginManagementPage';

vi.mock('./api', () => ({
  studentLoginApi: {
    list: vi.fn(),
    issueQrTicket: vi.fn(),
    listWechatBindings: vi.fn(),
    unbindWechat: vi.fn()
  }
}));

describe('学生登录管理页', () => {
  beforeEach(() => {
    vi.mocked(studentLoginApi.list).mockResolvedValue({
      items: [{
        id: '1874244142494646540', studentName: '林小满', gradeCode: 'G3', status: 'ENABLED',
        createdAt: '2026-08-08T10:00:00', updatedAt: '2026-08-08T10:00:00'
      }],
      page: 1, pageSize: 20, total: 1
    });
    vi.mocked(studentLoginApi.issueQrTicket).mockResolvedValue({
      ticketId: '1874244142494646541',
      qrContent: 'lingdong-learning://student-login?ticket=abcdefghijklmnopqrstuvwxyz1234567890123456',
      expiresAt: new Date(Date.now() + 300_000).toISOString()
    });
    vi.mocked(studentLoginApi.listWechatBindings).mockResolvedValue([{
      studentId: '1874244142494646540', studentName: '林小满',
      studentAccountMasked: '20****01', bound: true, boundAt: '2026-08-14T10:00:00'
    }]);
    vi.mocked(studentLoginApi.unbindWechat).mockResolvedValue();
  });

  it('二维码开关关闭时保留微信管理并隐藏生成入口', async () => {
    render(<StudentLoginManagementPage studentQrLoginEnabled={false} studentWechatAuthEnabled canManageStudentWechat />);
    await screen.findByText('林小满');
    expect(screen.queryByRole('button', { name: '生成 林小满 的登录二维码' })).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /解绑.*微信/ })).toBeInTheDocument();
  });

  it('按数据范围加载学生并生成短时二维码', async () => {
    render(<StudentLoginManagementPage />);
    expect(await screen.findByText('林小满')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '生成 林小满 的登录二维码' }));
    await waitFor(() => expect(studentLoginApi.issueQrTicket).toHaveBeenCalledWith('1874244142494646540'));
    expect(await screen.findByText('林小满的登录二维码')).toBeInTheDocument();
    expect(screen.getByText(/二维码将在/)).toBeInTheDocument();
  });

  it('允许手动刷新并替换旧票据', async () => {
    render(<StudentLoginManagementPage />);
    await screen.findByText('林小满');
    fireEvent.click(screen.getByRole('button', { name: '生成 林小满 的登录二维码' }));
    await screen.findByText('林小满的登录二维码');
    fireEvent.click(screen.getByRole('button', { name: '刷新二维码' }));
    await waitFor(() => expect(studentLoginApi.issueQrTicket).toHaveBeenCalledTimes(2));
  });

  it('仅在功能启用的家长管理态显示并二次确认解绑微信', async () => {
    render(<StudentLoginManagementPage studentWechatAuthEnabled canManageStudentWechat />);

    expect(await screen.findByText('已绑定')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '解绑 林小满 的微信' }));
    expect((await screen.findAllByText('解绑林小满的微信？')).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: '确认解绑' }));

    await waitFor(() => expect(studentLoginApi.unbindWechat)
      .toHaveBeenCalledWith('1874244142494646540'));
  });

  it('功能停用时不读取也不显示微信绑定状态', async () => {
    render(<StudentLoginManagementPage canManageStudentWechat />);

    await screen.findByText('林小满');
    expect(studentLoginApi.listWechatBindings).not.toHaveBeenCalled();
    expect(screen.queryByText('微信绑定')).not.toBeInTheDocument();
  });
});
