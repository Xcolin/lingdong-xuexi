import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ParentAccountLifecyclePanel } from './ParentAccountLifecyclePanel';

const authMocks = vi.hoisted(() => ({
  getParentAccountLifecycle: vi.fn(),
  issueCurrentMobileCode: vi.fn(),
  verifyCurrentMobile: vi.fn(),
  issueNewMobileCode: vi.fn(),
  changeParentMobile: vi.fn(),
  issueParentCancellationCode: vi.fn(),
  requestParentCancellation: vi.fn(),
  revokeParentCancellation: vi.fn(),
  clearLocalSession: vi.fn()
}));
vi.mock('../../api/auth', () => ({ authApi: authMocks }));
vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>();
  return {
    ...actual,
    message: {
      success: vi.fn(),
      error: vi.fn()
    }
  };
});

describe('Web 家长账号生命周期', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMocks.getParentAccountLifecycle.mockResolvedValue({
      maskedMobile: '138****8000', activeStudentRelationshipCount: 0,
      cancellationStatus: 'NONE', cancellationId: null, requestedAt: null, coolingEndsAt: null
    });
    authMocks.issueCurrentMobileCode.mockResolvedValue({ expiresAt: '2026-08-10T10:05:00', retryAfterSeconds: 60 });
    authMocks.verifyCurrentMobile.mockResolvedValue({ ticket: 'change-ticket' });
    authMocks.issueNewMobileCode.mockResolvedValue({ expiresAt: '2026-08-10T10:05:00', retryAfterSeconds: 60 });
    authMocks.changeParentMobile.mockResolvedValue(undefined);
  });

  it('完成旧号和新号双重验证后清理当前 Web 会话', async () => {
    const user = userEvent.setup();
    const onSessionEnded = vi.fn();
    render(<ParentAccountLifecyclePanel onSessionEnded={onSessionEnded} />);

    expect(await screen.findByText('138****8000')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '更换手机号' }));
    await user.click(screen.getByRole('button', { name: '发送当前手机号验证码' }));
    await user.type(screen.getByLabelText('当前手机号验证码'), '384291');
    await user.click(screen.getByRole('button', { name: '验证当前手机号' }));
    await user.type(screen.getByLabelText('新手机号'), '13900139000');
    await user.click(screen.getByRole('button', { name: '发送新手机号验证码' }));
    await user.type(screen.getByLabelText('新手机号验证码'), '593827');
    await user.click(screen.getByRole('button', { name: '确认更换手机号' }));

    await waitFor(() => {
      expect(authMocks.changeParentMobile).toHaveBeenCalledWith('change-ticket', '13900139000', '593827');
      expect(authMocks.clearLocalSession).toHaveBeenCalledOnce();
      expect(onSessionEnded).toHaveBeenCalledOnce();
    });
  });

  it('存在活动学生关系时禁用注销申请', async () => {
    authMocks.getParentAccountLifecycle.mockResolvedValue({
      maskedMobile: '138****8000', activeStudentRelationshipCount: 2,
      cancellationStatus: 'NONE', cancellationId: null, requestedAt: null, coolingEndsAt: null
    });

    render(<ParentAccountLifecyclePanel onSessionEnded={vi.fn()} />);

    expect(await screen.findByText('需先解除全部学生关系')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '申请注销' })).toBeDisabled();
  });
});
