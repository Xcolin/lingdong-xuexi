import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Descriptions, Empty, List, Modal, Select, Space, Spin, Tag, message } from 'antd';
import { RotateCcw } from 'lucide-react';
import { learningTaskApi } from './api';
import { previousDayTaskCopyApi } from './previousDayTaskCopyApi';
import type { PreviousDayTaskCopyPreview, StudentOption, TaskCopyBatchResult } from './types';

interface PreviousDayTaskCopyModalProps {
  open: boolean;
  onClose: () => void;
  onCompleted: () => void;
}

/** 家长按学生预览、确认和重试昨日任务复制。 */
export function PreviousDayTaskCopyModal({
  open,
  onClose,
  onCompleted
}: PreviousDayTaskCopyModalProps) {
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [studentId, setStudentId] = useState<string>();
  const [preview, setPreview] = useState<PreviousDayTaskCopyPreview | null>(null);
  const [result, setResult] = useState<TaskCopyBatchResult | null>(null);
  const [confirmDuplicates, setConfirmDuplicates] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setPreview(null);
    setResult(null);
    setConfirmDuplicates(false);
    setErrorMessage(null);
    setLoading(true);
    void learningTaskApi.listStudents('FAMILY')
      .then((items) => {
        setStudents(items);
        const first = items[0]?.id;
        setStudentId(first);
        if (first) return loadPreview(first);
        return undefined;
      })
      .catch((error) => setErrorMessage(toMessage(error)))
      .finally(() => setLoading(false));
  }, [open]);

  async function loadPreview(nextStudentId: string): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    setConfirmDuplicates(false);
    setResult(null);
    try {
      const nextPreview = await previousDayTaskCopyApi.preview(nextStudentId);
      setPreview(nextPreview);
      setResult(nextPreview.existingBatch);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function copy(): Promise<void> {
    if (!studentId) return;
    setSubmitting(true);
    try {
      const nextResult = await previousDayTaskCopyApi.copy(studentId, confirmDuplicates);
      setResult(nextResult);
      message.success(`成功复制 ${nextResult.successCount} 条，失败 ${nextResult.failureCount} 条`);
      onCompleted();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function retry(itemId: string): Promise<void> {
    if (!result) return;
    setSubmitting(true);
    try {
      const nextResult = await previousDayTaskCopyApi.retry(result.batchId, itemId);
      setResult(nextResult);
      message.success(`成功复制 ${nextResult.successCount} 条，失败 ${nextResult.failureCount} 条`);
      onCompleted();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  const requiresDuplicateConfirmation = Boolean(preview?.duplicateTitles.length);
  const canSubmit = Boolean(
    preview && preview.candidateCount > 0 && !preview.alreadyCopied
      && (!requiresDuplicateConfirmation || confirmDuplicates)
  );

  return (
    <Modal
      title="复制昨日任务"
      open={open}
      width={680}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>关闭</Button>,
        <Button
          key="copy"
          type="primary"
          loading={submitting}
          disabled={!canSubmit}
          onClick={() => void copy()}
        >确认复制</Button>
      ]}
    >
      <Space direction="vertical" size="middle" className="full-width-control">
        <Select
          aria-label="选择学生"
          value={studentId}
          placeholder="选择学生"
          className="full-width-control"
          options={students.map((student) => ({ value: student.id, label: student.studentName }))}
          onChange={(value) => {
            setStudentId(value);
            void loadPreview(value);
          }}
        />
        {loading && <div className="centered-loading"><Spin /></div>}
        {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
        {!loading && students.length === 0 && <Empty description="暂无可管理学生" />}
        {!loading && preview && (
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="学生">{preview.studentName}</Descriptions.Item>
            <Descriptions.Item label="昨日任务">{preview.candidateCount} 条</Descriptions.Item>
            <Descriptions.Item label="来源日期">{preview.sourceDate}</Descriptions.Item>
            <Descriptions.Item label="目标日期">{preview.targetDate}</Descriptions.Item>
          </Descriptions>
        )}
        {!loading && preview?.candidateCount === 0 && (
          <Empty description="昨日暂无可复制任务" />
        )}
        {requiresDuplicateConfirmation && !preview?.alreadyCopied && (
          <Alert
            type="warning"
            showIcon
            message="今天已存在同名任务"
            description={(
              <Space direction="vertical" size="small">
                <Space wrap>{preview?.duplicateTitles.map((title) => <Tag key={title}>{title}</Tag>)}</Space>
                <Checkbox
                  checked={confirmDuplicates}
                  onChange={(event) => setConfirmDuplicates(event.target.checked)}
                >仍然复制这些同名任务</Checkbox>
              </Space>
            )}
          />
        )}
        {result && (
          <Alert
            type={result.failureCount ? 'warning' : 'success'}
            showIcon
            message={`成功复制 ${result.successCount} 条，失败 ${result.failureCount} 条`}
          />
        )}
        {result?.items.some((item) => item.status === 'FAILED') && (
          <List
            size="small"
            dataSource={result.items.filter((item) => item.status === 'FAILED')}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button
                    key="retry"
                    type="text"
                    icon={<RotateCcw size={16} />}
                    loading={submitting}
                    onClick={() => void retry(item.itemId)}
                  >重试</Button>
                ]}
              >
                <List.Item.Meta title={item.taskTitle} description={item.failureMessage} />
              </List.Item>
            )}
          />
        )}
      </Space>
    </Modal>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}
