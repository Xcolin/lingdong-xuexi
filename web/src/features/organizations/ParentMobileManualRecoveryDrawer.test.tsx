import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ParentMobileManualRecoveryDrawer } from './ParentMobileManualRecoveryDrawer';

const recoveryApi = vi.hoisted(() => ({
  listCandidates: vi.fn(),
  issueCode: vi.fn(),
  recover: vi.fn()
}));

vi.mock('./parentMobileManualRecoveryApi', () => ({ parentMobileManualRecoveryApi: recoveryApi }));

describe('家长手机号人工核验换绑抽屉', () => {
  beforeEach(() => {
    recoveryApi.listCandidates.mockResolvedValue([{
      studentId: '1874244142494646903',
      studentName: '学生甲',
      organizationId: '1874244142494646904',
      organizationName: '第一学校',
      parentUserId: '1874244142494646902',
      parentDisplayName: '家长甲',
      maskedMobile: '138****8000',
      relationshipRole: 'PRIMARY_GUARDIAN'
    }]);
    recoveryApi.issueCode.mockResolvedValue({ expiresAt: '2026-08-14T10:05:00', retryAfterSeconds: 60 });
    recoveryApi.recover.mockResolvedValue(undefined);
  });

  it('验证新手机号后提交线下核验事实', async () => {
    const user = userEvent.setup();
    render(<ParentMobileManualRecoveryDrawer open onClose={vi.fn()} />);

    await user.click(await screen.findByLabelText('学生与家长'));
    await user.click(await screen.findByText('学生甲 · 家长甲 · 主家长 · 138****8000'));
    await user.type(screen.getByLabelText('新手机号'), '13900139000');
    await user.click(screen.getByRole('button', { name: '发送验证码' }));

    await waitFor(() => {
      expect(recoveryApi.issueCode).toHaveBeenCalledWith({
        studentId: '1874244142494646903',
        parentUserId: '1874244142494646902',
        newMobile: '13900139000'
      });
    });

    await user.type(screen.getByLabelText('验证码'), '123456');
    await user.type(screen.getByLabelText('核验原因'), '家长到校核验');
    await user.type(screen.getByLabelText('确认语句'), '已完成线下身份核验');
    await user.click(screen.getByRole('button', { name: '确认换绑' }));

    await waitFor(() => {
      expect(recoveryApi.recover).toHaveBeenCalledWith({
        studentId: '1874244142494646903',
        parentUserId: '1874244142494646902',
        newMobile: '13900139000',
        smsCode: '123456',
        reason: '家长到校核验',
        confirmation: '已完成线下身份核验'
      });
    });
  });
});
