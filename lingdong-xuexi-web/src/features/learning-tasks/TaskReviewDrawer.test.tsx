import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { TaskReviewDrawer } from './TaskReviewDrawer';
import { taskReviewApi } from './reviewApi';
import type { TaskReview } from './types';

vi.mock('./reviewApi', () => ({ taskReviewApi: { findById: vi.fn(), listReviewerOptions: vi.fn() } }));
const firstId = '1874244142494699101';
const secondId = '1874244142494699102';
function review(id: string, title: string): TaskReview {
  return { assignmentId: id, taskId: id, title, basePoints: 10, studentId: id,
    studentName: '测试学生', sourceType: 'FAMILY', sourceOrganizationId: null,
    sourceOrganizationName: null, currentStatus: 'PENDING_REVIEW', currentReviewerId: id,
    reviewerDisplayName: '测试家长', latestCheckIn: { id, submissionNo: 1, content: '测试打卡',
      status: 'SUBMITTED', submittedAt: '2026-09-10T10:00:00', reviewComment: null, attachments: [] } };
}
beforeEach(() => { vi.resetAllMocks(); vi.mocked(taskReviewApi.listReviewerOptions).mockResolvedValue([]); });
const callbacks = { onClose: vi.fn(), onChanged: async () => {} };

it('切换任务后新详情失败时清空旧内容和操作', async () => {
  vi.mocked(taskReviewApi.findById).mockResolvedValueOnce(review(firstId, '旧任务'))
    .mockRejectedValueOnce(new Error('当前任务不可访问'));
  const { rerender } = render(<TaskReviewDrawer open assignmentId={firstId} {...callbacks} />);
  await screen.findByText('旧任务');
  rerender(<TaskReviewDrawer open assignmentId={secondId} {...callbacks} />);
  await screen.findByText('当前任务不可访问');
  expect(screen.queryByText('旧任务')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /审核通过并发放/ })).not.toBeInTheDocument();
});

it('上一任务延迟响应不能覆盖当前任务', async () => {
  let resolveFirst!: (value: TaskReview) => void;
  vi.mocked(taskReviewApi.findById).mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve; }))
    .mockResolvedValueOnce(review(secondId, '当前任务'));
  const { rerender } = render(<TaskReviewDrawer open assignmentId={firstId} {...callbacks} />);
  await waitFor(() => expect(taskReviewApi.findById).toHaveBeenCalledWith(firstId));
  rerender(<TaskReviewDrawer open assignmentId={secondId} {...callbacks} />);
  await screen.findByText('当前任务');
  await act(async () => resolveFirst(review(firstId, '迟到旧任务')));
  expect(screen.getByText('当前任务')).toBeInTheDocument();
  expect(screen.queryByText('迟到旧任务')).not.toBeInTheDocument();
});
