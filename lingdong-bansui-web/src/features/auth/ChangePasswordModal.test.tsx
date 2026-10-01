import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChangePasswordModal } from './ChangePasswordModal';

const auth = vi.hoisted(() => ({ changePassword: vi.fn(), clearLocalSession: vi.fn() }));
vi.mock('../../api/auth', () => ({ authApi: auth }));

async function fillPasswords(oldPassword = 'OldPassword123', newPassword = 'NewPassword456', confirmation = newPassword) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('旧密码', { exact: true }), oldPassword);
  await user.type(screen.getByLabelText('新密码', { exact: true }), newPassword);
  await user.type(screen.getByLabelText('确认新密码', { exact: true }), confirmation);
  return user;
}

describe('修改自己的密码', () => {
  beforeEach(() => { vi.clearAllMocks(); auth.changePassword.mockResolvedValue(undefined); });

  it.each([
    ['weakpass', 'weakpass', '密码必须为 8 至 20 位字母和数字组合'],
    ['OldPassword123', 'OldPassword123', '新密码不能与旧密码相同'],
    ['NewPassword456', 'OtherPassword789', '两次新密码不一致']
  ])('拒绝不合法的密码组合 %s', async (password, confirmation, error) => {
    render(<ChangePasswordModal onCancel={vi.fn()} onSuccess={vi.fn()} />);
    const user = await fillPasswords('OldPassword123', password, confirmation);
    await user.click(screen.getByRole('button', { name: '确认修改' }));
    expect(await screen.findByText(error)).toBeInTheDocument();
    expect(auth.changePassword).not.toHaveBeenCalled();
  });

  it('后端拒绝时保留输入和会话，允许重新提交', async () => {
    auth.changePassword.mockRejectedValueOnce(new Error('旧密码不正确'));
    const success = vi.fn();
    render(<ChangePasswordModal onCancel={vi.fn()} onSuccess={success} />);
    const user = await fillPasswords();
    await user.click(screen.getByRole('button', { name: '确认修改' }));
    expect(await screen.findByText('旧密码不正确')).toBeInTheDocument();
    expect(screen.getByLabelText('新密码', { exact: true })).toHaveValue('NewPassword456');
    expect(auth.clearLocalSession).not.toHaveBeenCalled();
    expect(success).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: '确认修改' }));
    await waitFor(() => expect(success).toHaveBeenCalledOnce());
  });

  it('请求期间禁止重复提交和关闭，成功后清理本地会话', async () => {
    let resolve!: () => void;
    auth.changePassword.mockImplementationOnce(() => new Promise<void>(done => { resolve = done; }));
    const cancel = vi.fn(), success = vi.fn();
    render(<ChangePasswordModal onCancel={cancel} onSuccess={success} />);
    const user = await fillPasswords();
    await user.click(screen.getByRole('button', { name: '确认修改' }));
    await waitFor(() => expect(auth.changePassword).toHaveBeenCalledWith({ oldPassword: 'OldPassword123', newPassword: 'NewPassword456' }));
    expect(screen.getByRole('button', { name: '取消' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /确认修改/ }));
    await user.keyboard('{Escape}');
    expect(auth.changePassword).toHaveBeenCalledOnce();
    expect(cancel).not.toHaveBeenCalled();
    resolve();
    await waitFor(() => expect(success).toHaveBeenCalledOnce());
    expect(auth.clearLocalSession).toHaveBeenCalledOnce();
  });
});
