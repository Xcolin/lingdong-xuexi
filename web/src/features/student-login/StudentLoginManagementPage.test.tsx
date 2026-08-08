import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { studentLoginApi } from './api';
import { StudentLoginManagementPage } from './StudentLoginManagementPage';

vi.mock('./api', () => ({
  studentLoginApi: {
    list: vi.fn(),
    issueQrTicket: vi.fn()
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
});
