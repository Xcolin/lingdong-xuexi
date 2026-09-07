import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ParentRelationshipInvitationPage } from './ParentRelationshipInvitationPage';

const apiMocks = vi.hoisted(() => ({ acceptInvitation: vi.fn(), rejectInvitation: vi.fn() }));
const capabilityMocks = vi.hoisted(() => ({ web: vi.fn() }));
const authMocks = vi.hoisted(() => ({ parentAuthContext: vi.fn() }));

vi.mock('./api', () => ({ parentRelationshipApi: apiMocks }));
vi.mock('../../api/capability', () => ({ capabilityApi: capabilityMocks }));
vi.mock('../../api/auth', () => ({ authApi: authMocks }));

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/parent-relationship-invitations/8910000000000000911']}>
      <Routes>
        <Route path="/parent-relationship-invitations/:invitationId" element={<ParentRelationshipInvitationPage />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Web 家长关系邀请确认', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capabilityMocks.web.mockResolvedValue({ parentRelationshipManagementEnabled: true });
    authMocks.parentAuthContext.mockResolvedValue({ agreementVersion: '2026-08' });
  });

  it('接受失败后保留手机号和验证码供用户修正', async () => {
    apiMocks.acceptInvitation.mockRejectedValue(new Error('验证码不正确或邀请不可用'));
    const user = userEvent.setup();
    renderPage();

    const mobile = await screen.findByRole('textbox', { name: '手机号' });
    const code = screen.getByRole('textbox', { name: '验证码' });
    await user.type(mobile, '13800138000');
    await user.type(code, '384291');
    await user.click(screen.getByRole('checkbox', { name: '我已阅读并同意当前用户协议' }));
    await user.click(screen.getByRole('button', { name: '确认接受' }));

    expect(await screen.findByText('验证码不正确或邀请不可用')).toBeInTheDocument();
    expect(mobile).toHaveValue('13800138000');
    expect(code).toHaveValue('384291');
  });

  it('功能停用时拒绝直达邀请页', async () => {
    capabilityMocks.web.mockResolvedValue({ parentRelationshipManagementEnabled: false });
    renderPage();

    expect(await screen.findByText('家长关系功能未启用')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '手机号' })).not.toBeInTheDocument();
    await waitFor(() => expect(apiMocks.acceptInvitation).not.toHaveBeenCalled());
  });
});
