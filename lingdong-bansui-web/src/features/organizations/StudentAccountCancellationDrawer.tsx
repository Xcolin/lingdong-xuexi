import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useEffect, useMemo, useState } from 'react';
import { Alert, Drawer, Form, Input, Select, message } from 'antd';
import { Trash2 } from 'lucide-react';
import {
  studentAccountCancellationApi,
  type StudentAccountCancellationCandidate
} from './studentAccountCancellationApi';

const CONFIRMATION = '确认注销学生账号';

interface StudentAccountCancellationDrawerProps {
  open: boolean;
  onClose: () => void;
}

interface CancellationFormValues {
  studentId: string;
  reason: string;
  confirmation: string;
}

export function StudentAccountCancellationDrawer({
  open,
  onClose
}: StudentAccountCancellationDrawerProps) {
  const [form] = Form.useForm<CancellationFormValues>();
  const [candidates, setCandidates] = useState<StudentAccountCancellationCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    studentAccountCancellationApi.listCandidates()
      .then(setCandidates)
      .catch((error) => message.error(toMessage(error)))
      .finally(() => setLoading(false));
  }, [open]);

  const candidateOptions = useMemo(() => candidates.map((candidate) => ({
    value: candidate.studentId,
    label: `${candidate.studentName} · ${candidate.studentAccount} · ${candidate.organizationName}`
  })), [candidates]);

  function submit(values: CancellationFormValues): void {
    Modal.confirm({ actionPrefix: 'organizations.student-account-cancellation-drawer.confirm.1',
      title: '确认永久注销该学生账号？',
      content: '账号、登录码、二维码和全部会话将立即失效，操作不可恢复。',
      okText: '确认注销',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => cancel(values)
    });
  }

  async function cancel(values: CancellationFormValues): Promise<void> {
    setSubmitting(true);
    try {
      await studentAccountCancellationApi.cancel(values.studentId, {
        reason: values.reason.trim(),
        confirmation: values.confirmation
      });
      message.success('学生账号已注销，全部登录凭据已失效');
      form.resetFields();
      setCandidates((items) => items.filter((item) => item.studentId !== values.studentId));
      onClose();
    } catch (error) {
      message.error(toMessage(error));
      throw error;
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer title="学生账号注销" width="min(520px, 92vw)" open={open} onClose={onClose} destroyOnHidden>
      <Alert
        type="error"
        showIcon
        message="仅可注销已退学且已解除全部家长关系的学生"
        description="系统将永久匿名化学生身份，并撤销登录码、二维码和全部活动会话。历史业务关系与注销审计继续保留。"
      />
      <Form form={form} layout="vertical" className="drawer-form" onFinish={submit}>
        <Form.Item
          label="学生账号"
          name="studentId"
          rules={[{ required: true, message: '请选择学生账号' }]}
        >
          <Select
            loading={loading}
            options={candidateOptions}
            placeholder={candidateOptions.length > 0 ? '请选择待注销学生' : '暂无可注销学生'}
          />
        </Form.Item>
        <Form.Item
          label="注销原因"
          name="reason"
          rules={[
            { required: true, message: '请输入注销原因' },
            { max: 200, message: '注销原因不能超过 200 个字符' }
          ]}
        >
          <Input.TextArea rows={4} maxLength={200} showCount />
        </Form.Item>
        <Form.Item
          label="确认语句"
          name="confirmation"
          extra={`请输入：${CONFIRMATION}`}
          rules={[
            { required: true, message: '请输入确认语句' },
            {
              validator: (_, value) => value === CONFIRMATION
                ? Promise.resolve()
                : Promise.reject(new Error('确认语句不正确'))
            }
          ]}
        >
          <Input autoComplete="off" />
        </Form.Item>
        <div className="form-actions">
          <Button actionKey="organizations.student-account-cancellation-drawer.1" onClick={onClose}>取消</Button>
          <Button actionKey="organizations.student-account-cancellation-drawer.2" danger type="primary" htmlType="submit" loading={submitting} icon={<Trash2 size={16} />}>
            注销学生账号
          </Button>
        </div>
      </Form>
    </Drawer>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '学生账号注销失败';
}
