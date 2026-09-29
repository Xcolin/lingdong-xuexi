import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ParentOnboardingPage } from './ParentOnboardingPage';

const authMocks = vi.hoisted(() => ({
  completeParentOnboarding: vi.fn(),
  parentState: vi.fn(),
  acceptParentAgreement: vi.fn()
}));
vi.mock('../../api/auth', () => ({
  authApi: { ...authMocks, hasLocalSession: () => true, clearLocalSession: vi.fn() }
}));

describe('Web 家长首次引导', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMocks.completeParentOnboarding.mockResolvedValue(undefined);
    authMocks.acceptParentAgreement.mockResolvedValue(undefined);
    authMocks.parentState.mockResolvedValue({
      onboardingRequired: true,
      agreementAcceptanceRequired: false,
      currentAgreementVersion: '2026-08'
    });
  });

  it('展示四步内容并在完成后提交状态', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><ParentOnboardingPage /></MemoryRouter>);

    expect(await screen.findAllByText('欢迎使用')).toHaveLength(2);
    expect(screen.getByText('任务模式')).toBeInTheDocument();
    expect(screen.getByText('产品理念')).toBeInTheDocument();
    expect(screen.getByText('专注模式')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '下一步' }));
    await user.click(screen.getByRole('button', { name: '下一步' }));
    await user.click(screen.getByRole('button', { name: '下一步' }));
    await user.click(screen.getByRole('button', { name: '完成引导' }));

    await waitFor(() => expect(authMocks.completeParentOnboarding).toHaveBeenCalledOnce());
  });

  it('协议版本升级时先接受当前协议再进入首次引导', async () => {
    authMocks.parentState.mockResolvedValue({
      onboardingRequired: true,
      agreementAcceptanceRequired: true,
      currentAgreementVersion: '2026-09'
    });
    const user = userEvent.setup();
    render(<MemoryRouter><ParentOnboardingPage /></MemoryRouter>);

    expect(await screen.findByRole('heading', { name: '用户协议更新' })).toBeInTheDocument();
    expect(screen.queryByText('任务模式')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '同意并继续' }));

    await waitFor(() => expect(authMocks.acceptParentAgreement).toHaveBeenCalledWith('2026-09'));
    expect(await screen.findByText('任务模式')).toBeInTheDocument();
  });
});
