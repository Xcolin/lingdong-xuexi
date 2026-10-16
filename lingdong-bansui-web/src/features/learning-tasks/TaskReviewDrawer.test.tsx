import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { MenuConfigurationProvider } from '../../app/MenuConfiguration';
import { TaskReviewDrawer } from './TaskReviewDrawer';
import { taskReviewApi } from './reviewApi';
import type { TaskReview } from './types';

vi.mock('./reviewApi', () => ({ taskReviewApi: { findById: vi.fn(), listReviewerOptions: vi.fn(), readAttachment: vi.fn() } }));
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

function reviewWithAttachment(): TaskReview {
 const result=review(firstId,'带附件任务');
 result.latestCheckIn.attachments=[{id:'file',originalName:'task.png',contentType:'image/png',sizeBytes:2,contentUrl:'/unused'}];
 return result;
}
it('有效附件读取被拒绝时仍显示文本审核，但不自动请求附件403',async()=>{
 vi.mocked(taskReviewApi.findById).mockResolvedValue(reviewWithAttachment());
 render(<MenuConfigurationProvider nodes={[]} permissionCodes={['TASK_ASSIGNMENT_REVIEW']} refresh={async()=>{}}><TaskReviewDrawer open assignmentId={firstId} {...callbacks}/></MenuConfigurationProvider>);
 await screen.findByText('当前权限不允许读取打卡附件');
 expect(screen.getByText('测试打卡')).toBeInTheDocument();
 expect(taskReviewApi.readAttachment).not.toHaveBeenCalled();
});
it('有效附件读取可以加载，撤销读取后隐藏图片并释放预览URL',async()=>{
 Object.defineProperty(URL,'createObjectURL',{configurable:true,value:vi.fn(()=> 'blob:task-preview')});
 Object.defineProperty(URL,'revokeObjectURL',{configurable:true,value:vi.fn()});
 vi.mocked(taskReviewApi.findById).mockResolvedValue(reviewWithAttachment());
 vi.mocked(taskReviewApi.readAttachment).mockResolvedValue(new Blob(['ok'],{type:'image/png'}));
 const view=render(<MenuConfigurationProvider nodes={[]} permissionCodes={['TASK_ASSIGNMENT_REVIEW','ATTACHMENT_READ']} refresh={async()=>{}}><TaskReviewDrawer open assignmentId={firstId} {...callbacks}/></MenuConfigurationProvider>);
 await screen.findByAltText('task.png');
 expect(taskReviewApi.readAttachment).toHaveBeenCalledWith('file');
 view.rerender(<MenuConfigurationProvider nodes={[]} permissionCodes={['TASK_ASSIGNMENT_REVIEW']} refresh={async()=>{}}><TaskReviewDrawer open assignmentId={firstId} {...callbacks}/></MenuConfigurationProvider>);
 await screen.findByText('当前权限不允许读取打卡附件');
 expect(screen.queryByAltText('task.png')).toBeNull();
 expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:task-preview');
});
