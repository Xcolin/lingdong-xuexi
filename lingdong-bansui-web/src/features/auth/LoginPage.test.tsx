import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';

const authMocks = vi.hoisted(() => ({
  parentAuthContext: vi.fn(),
  issueParentSmsCode: vi.fn(),
  loginParentBySms: vi.fn(),
  hasLocalSession: vi.fn(() => false),
  clearLocalSession: vi.fn(),
  currentUser: vi.fn(),
  parentState: vi.fn(),
  login: vi.fn()
}));

vi.mock('../../api/auth', () => ({ authApi: authMocks }));

describe('Web 家长登录页', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMocks.hasLocalSession.mockReturnValue(false);
    authMocks.parentAuthContext.mockResolvedValue({
      enabled: true,
      agreementVersion: '1',
      codeExpiresInSeconds: 300,
      retryAfterSeconds: 60
    });
    authMocks.issueParentSmsCode.mockResolvedValue({
      expiresAt: '2026-08-09T10:00:00Z',
      retryAfterSeconds: 60
    });
  });

  it('提供验证码与密码两种独立登录方式并发送用途隔离验证码', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    expect(await screen.findByText('验证码登录')).toBeInTheDocument();
    expect(screen.getByText('密码登录')).toBeInTheDocument();
    await user.type(screen.getByLabelText('手机号'), '13800138000');
    await user.click(screen.getByRole('button', { name: '获取验证码' }));

    await waitFor(() => expect(authMocks.issueParentSmsCode).toHaveBeenCalledWith({
      mobile: '13800138000', purpose: 'REGISTER_OR_LOGIN', clientType: 'WEB'
    }));
    expect(screen.getByText(/我已阅读并同意/)).toBeInTheDocument();
  });

  it('功能停用后隐藏验证码模式并保留密码登录', async () => {
    authMocks.parentAuthContext.mockResolvedValue({
      enabled: false,
      agreementVersion: '1',
      codeExpiresInSeconds: 300,
      retryAfterSeconds: 60
    });
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    expect(await screen.findByText('密码登录')).toBeInTheDocument();
    expect(screen.queryByText('验证码登录')).not.toBeInTheDocument();
  });

  it('家长密码登录后查询服务端协议与引导状态', async () => {
    const user = userEvent.setup();
    authMocks.parentAuthContext.mockResolvedValue({
      enabled: false, agreementVersion: '1', codeExpiresInSeconds: 300, retryAfterSeconds: 60
    });
    authMocks.login.mockResolvedValue({ accessToken: 'access' });
    authMocks.currentUser.mockResolvedValue({ roleCodes: ['PARENT'] });
    authMocks.parentState.mockResolvedValue({
      onboardingRequired: true, agreementAcceptanceRequired: false, currentAgreementVersion: '1'
    });
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await user.type(await screen.findByLabelText('账号'), '13800138000');
    await user.type(screen.getByLabelText('密码'), 'ParentPassword1');
    await user.click(screen.getByRole('button', { name: '登录' }));

    await waitFor(() => expect(authMocks.parentState).toHaveBeenCalledOnce());
  });

  it('验证码发送超频时保留表单并显示服务端错误', async () => {
    authMocks.issueParentSmsCode.mockRejectedValue(new Error('请求过于频繁，请稍后重试'));
    const user = userEvent.setup();
    const { container } = render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await user.type(await screen.findByLabelText('手机号'), '13800138000');
    await user.click(screen.getByRole('button', { name: '获取验证码' }));

    expect(await screen.findByText('请求过于频繁，请稍后重试')).toBeInTheDocument();
    expect(container.querySelector('.login-code-row')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '获取验证码' })).toBeEnabled();
  });
});
