import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StudentAccountCancellationDrawer } from './StudentAccountCancellationDrawer';

const cancellationApi = vi.hoisted(() => ({
  listCandidates: vi.fn(),
  cancel: vi.fn()
}));

vi.mock('./studentAccountCancellationApi', () => ({
  studentAccountCancellationApi: cancellationApi
}));

describe('学生账号注销抽屉', () => {
  beforeEach(() => {
    cancellationApi.listCandidates.mockResolvedValue([{
      studentId: '8930000000000000104',
      studentName: '待注销学生',
      studentAccount: '20260001',
      organizationId: '8930000000000000101',
      organizationName: '原注销学校'
    }]);
    cancellationApi.cancel.mockResolvedValue(undefined);
  });

  it('完成精确确认和危险操作二次确认后提交注销', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<StudentAccountCancellationDrawer open onClose={onClose} />);

    await user.click(await screen.findByLabelText('学生账号'));
    await user.click(await screen.findByText('待注销学生 · 20260001 · 原注销学校'));
    await user.type(screen.getByLabelText('注销原因'), '学生已经退学并解除全部家长关系');
    await user.type(screen.getByLabelText('确认语句'), '确认注销学生账号');
    await user.click(screen.getByRole('button', { name: '注销学生账号' }));
    await user.click(await screen.findByRole('button', { name: '确认注销' }));

    await waitFor(() => {
      expect(cancellationApi.cancel).toHaveBeenCalledWith('8930000000000000104', {
        reason: '学生已经退学并解除全部家长关系',
        confirmation: '确认注销学生账号'
      });
      expect(onClose).toHaveBeenCalledOnce();
    });
  });
});
