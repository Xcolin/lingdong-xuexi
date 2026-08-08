import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Image, Input, Modal, Select, Space, Spin, message } from 'antd';
import { CircleCheckBig, RotateCcw, UserRoundCheck } from 'lucide-react';
import { taskReviewApi } from './reviewApi';
import type { ReviewerOption, TaskReview } from './types';

interface TaskReviewDrawerProps {
  open: boolean;
  assignmentId: string | null;
  onClose: () => void;
  onChanged: () => Promise<void>;
}

/** 审核详情集中处理通过、驳回和转交，确保客户端不参与积分计算。 */
export function TaskReviewDrawer({
  open,
  assignmentId,
  onClose,
  onChanged
}: TaskReviewDrawerProps) {
  const [review, setReview] = useState<TaskReview | null>(null);
  const [reviewerOptions, setReviewerOptions] = useState<ReviewerOption[]>([]);
  const [reviewComment, setReviewComment] = useState('');
  const [reviewerUserId, setReviewerUserId] = useState<string>();
  const [transferReason, setTransferReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !assignmentId) return;
    setLoading(true);
    setErrorMessage(null);
    setReviewComment('');
    setReviewerUserId(undefined);
    setTransferReason('');
    Promise.all([
      taskReviewApi.findById(assignmentId),
      taskReviewApi.listReviewerOptions(assignmentId)
    ]).then(([details, options]) => {
      setReview(details);
      setReviewerOptions(options);
    }).catch((error: unknown) => {
      setErrorMessage(toMessage(error));
    }).finally(() => setLoading(false));
  }, [open, assignmentId]);

  function confirmReject(): void {
    const comment = reviewComment.trim();
    if (!assignmentId || !comment) {
      message.warning('请填写驳回意见');
      return;
    }
    Modal.confirm({
      title: '确认驳回本次打卡',
      content: '任务将退回学生继续执行，当前打卡记录会保留。',
      okText: '确认驳回',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => submitChange(async () => {
        await taskReviewApi.reject(assignmentId, comment);
        message.success('已驳回本次打卡');
      })
    });
  }

  function confirmApprove(): void {
    if (!assignmentId || !review) return;
    Modal.confirm({
      title: '确认审核通过',
      content: `任务将完成并向学生发放 ${review.basePoints} 积分。积分到账后只能通过纠错台账调整。`,
      okText: '确认通过',
      cancelText: '取消',
      onOk: () => submitChange(async () => {
        const result = await taskReviewApi.approve(assignmentId);
        message.success(`审核通过，已发放 ${result.awardedPoints} 积分`);
      })
    });
  }

  async function transfer(): Promise<void> {
    const reason = transferReason.trim();
    if (!assignmentId || !reviewerUserId || !reason) {
      message.warning('请选择审核人并填写转交原因');
      return;
    }
    await submitChange(async () => {
      await taskReviewApi.transfer(assignmentId, reviewerUserId, reason);
      message.success('审核责任已转交');
    });
  }

  async function submitChange(action: () => Promise<void>): Promise<void> {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await action();
      await onChanged();
      onClose();
    } catch (error) {
      setErrorMessage(toMessage(error));
      throw error;
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer
      title="任务审核"
      open={open}
      width={560}
      destroyOnClose
      loading={loading}
      onClose={submitting ? undefined : onClose}
    >
      {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
      {review && (
        <div className="review-drawer-content">
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="任务">{review.title}</Descriptions.Item>
            <Descriptions.Item label="学生">{review.studentName}</Descriptions.Item>
            <Descriptions.Item label="基础积分">{review.basePoints} 分</Descriptions.Item>
            <Descriptions.Item label="当前审核人">{review.reviewerDisplayName}</Descriptions.Item>
            <Descriptions.Item label="提交次数">第 {review.latestCheckIn.submissionNo} 次</Descriptions.Item>
            <Descriptions.Item label="提交时间">{formatTime(review.latestCheckIn.submittedAt)}</Descriptions.Item>
          </Descriptions>

          <section className="review-section">
            <h2>打卡内容</h2>
            {review.latestCheckIn.content && (
              <div className="review-content-text">{review.latestCheckIn.content}</div>
            )}
            <TaskAttachmentPreview review={review} />
          </section>

          <section className="review-section">
            <h2>审核通过</h2>
            <Button
              type="primary"
              icon={<CircleCheckBig size={16} />}
              loading={submitting}
              onClick={confirmApprove}
            >审核通过并发放 {review.basePoints} 积分</Button>
          </section>

          <section className="review-section">
            <h2>驳回打卡</h2>
            <Input.TextArea
              value={reviewComment}
              maxLength={500}
              showCount
              rows={4}
              placeholder="填写驳回意见"
              onChange={(event) => setReviewComment(event.target.value)}
            />
            <Button
              danger
              icon={<RotateCcw size={16} />}
              loading={submitting}
              onClick={confirmReject}
            >驳回打卡</Button>
          </section>

          <section className="review-section">
            <h2>转交审核</h2>
            <Select
              value={reviewerUserId}
              placeholder="选择审核人"
              options={reviewerOptions.map((option) => ({
                value: option.userId,
                label: option.displayName
              }))}
              onChange={setReviewerUserId}
            />
            <Input.TextArea
              value={transferReason}
              maxLength={500}
              rows={3}
              placeholder="填写转交原因"
              onChange={(event) => setTransferReason(event.target.value)}
            />
            <Space>
              <Button
                icon={<UserRoundCheck size={16} />}
                disabled={!reviewerOptions.length}
                loading={submitting}
                onClick={() => void transfer().catch(() => undefined)}
              >确认转交</Button>
            </Space>
          </section>
        </div>
      )}
    </Drawer>
  );
}

function TaskAttachmentPreview({ review }: { review: TaskReview }) {
  const [previewUrls, setPreviewUrls] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const attachments = review.latestCheckIn.attachments;
    let active = true;
    const createdUrls: string[] = [];
    setPreviewUrls({});
    setFailed(false);
    setLoading(attachments.length > 0);
    if (!attachments.length) return () => undefined;
    Promise.all(attachments.map(async (attachment) => {
      const blob = await taskReviewApi.readAttachment(attachment.id);
      const url = URL.createObjectURL(blob);
      createdUrls.push(url);
      return [attachment.id, url] as const;
    })).then((entries) => {
      if (active) setPreviewUrls(Object.fromEntries(entries));
    }).catch(() => {
      if (active) setFailed(true);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
      createdUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [review.latestCheckIn.id, review.latestCheckIn.attachments]);

  if (!review.latestCheckIn.attachments.length) return null;
  if (loading) return <Spin size="small" />;
  if (failed) return <Alert type="warning" showIcon message="部分打卡图片读取失败" />;
  return (
    <Image.PreviewGroup>
      <div className="review-attachment-grid">
        {review.latestCheckIn.attachments.map((attachment) => (
          previewUrls[attachment.id] && (
            <Image
              key={attachment.id}
              width={120}
              height={120}
              src={previewUrls[attachment.id]}
              alt={attachment.originalName}
              style={{ objectFit: 'cover' }}
            />
          )
        ))}
      </div>
    </Image.PreviewGroup>
  );
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short'
  }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
