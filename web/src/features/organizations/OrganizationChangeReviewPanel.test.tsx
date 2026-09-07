import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrganizationChangeReviewPanel } from './OrganizationChangeReviewPanel';

const organizationApi = vi.hoisted(() => ({
  listChanges: vi.fn(),
  approveChange: vi.fn(),
  rejectChange: vi.fn()
}));

vi.mock('../../api/organization', () => ({ organizationApi }));

describe('组织变更审核面板', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    organizationApi.listChanges.mockResolvedValue([{
      changeId: '1874244142494646301', taskId: '1874244142494646302',
      organizationId: '1874244142494646303', organizationNameSnapshot: '审核学校',
      organizationCodeSnapshot: 'REVIEW_SCHOOL', changeType: 'MOVE',
      targetParentId: '1874244142494646304', expectedVersion: 2,
      reason: '调整区域归属', executionStatus: 'PENDING', failureReason: null,
      taskStatus: 'PENDING_REVIEW', submittedBy: '1874244142494646305',
      submittedAt: '2026-08-15T10:00:00', reviewedBy: null, reviewedAt: null,
      reviewComment: null, createdAt: '2026-08-15T10:00:00'
    }]);
    organizationApi.approveChange.mockResolvedValue(undefined);
    organizationApi.rejectChange.mockResolvedValue(undefined);
  });

  it('审核员可以填写意见并批准待审核申请', async () => {
    const user = userEvent.setup();
    render(<OrganizationChangeReviewPanel canReview />);

    expect(await screen.findByText('审核学校')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '批准' }));
    await user.type(screen.getByLabelText('审核意见'), '已核对组织归属');
    await user.click(screen.getByRole('button', { name: '确认批准' }));

    await waitFor(() => expect(organizationApi.approveChange)
      .toHaveBeenCalledWith('1874244142494646302', { comment: '已核对组织归属' }));
  });

  it('系统管理员的申请视图不显示审核操作', async () => {
    render(<OrganizationChangeReviewPanel canReview={false} />);

    expect(await screen.findByText('审核学校')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '批准' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '驳回' })).not.toBeInTheDocument();
  });
});
