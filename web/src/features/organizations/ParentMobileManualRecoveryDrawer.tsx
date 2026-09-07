import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Drawer, Form, Input, Select, Space, message } from 'antd';
import {
  parentMobileManualRecoveryApi,
  type ParentMobileManualRecoveryCandidate,
  type ParentMobileManualRecoveryInput
} from './parentMobileManualRecoveryApi';

const CONFIRMATION = '已完成线下身份核验';

interface ParentMobileManualRecoveryDrawerProps {
  open: boolean;
  onClose: () => void;
}

interface RecoveryFormValues {
  candidateKey: string;
  newMobile: string;
  smsCode: string;
  reason: string;
  confirmation: string;
}

export function ParentMobileManualRecoveryDrawer({ open, onClose }: ParentMobileManualRecoveryDrawerProps) {
  const [form] = Form.useForm<RecoveryFormValues>();
  const [candidates, setCandidates] = useState<ParentMobileManualRecoveryCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    parentMobileManualRecoveryApi.listCandidates()
      .then(setCandidates)
      .catch((error) => message.error(toMessage(error)))
      .finally(() => setLoading(false));
  }, [open]);

  const candidateOptions = useMemo(() => candidates.map((candidate) => ({
    value: candidateKey(candidate),
    label: `${candidate.studentName} · ${candidate.parentDisplayName} · ${roleLabel(candidate.relationshipRole)} · ${candidate.maskedMobile}`
  })), [candidates]);

  async function issueCode(): Promise<void> {
    try {
      const values = await form.validateFields(['candidateKey', 'newMobile']);
      const candidate = findCandidate(values.candidateKey);
      setSending(true);
      await parentMobileManualRecoveryApi.issueCode({
        studentId: candidate.studentId,
        parentUserId: candidate.parentUserId,
        newMobile: values.newMobile
      });
      message.success('验证码已发送至新手机号');
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    } finally {
      setSending(false);
    }
  }

  async function submit(values: RecoveryFormValues): Promise<void> {
    const candidate = findCandidate(values.candidateKey);
    const input: ParentMobileManualRecoveryInput = {
      studentId: candidate.studentId,
      parentUserId: candidate.parentUserId,
      newMobile: values.newMobile,
      smsCode: values.smsCode,
      reason: values.reason.trim(),
      confirmation: values.confirmation
    };
    setSubmitting(true);
    try {
      await parentMobileManualRecoveryApi.recover(input);
      message.success('家长手机号已换绑，原会话已全部撤销');
      form.resetFields();
      onClose();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  function findCandidate(key: string): ParentMobileManualRecoveryCandidate {
    const candidate = candidates.find((item) => candidateKey(item) === key);
    if (!candidate) throw new Error('请选择学生与家长');
    return candidate;
  }

  return (
    <Drawer title="家长换号核验" width={520} open={open} onClose={onClose} destroyOnHidden>
      <Alert
        type="warning"
        showIcon
        message="仅用于原手机号不可用且已完成线下身份核验的家长"
        description="提交后将撤销该家长全部活动会话。系统只保存号码摘要、核验原因和操作审计。"
      />
      <Form form={form} layout="vertical" className="drawer-form" onFinish={submit}>
        <Form.Item label="学生与家长" name="candidateKey" rules={[{ required: true, message: '请选择学生与家长' }]}>
          <Select loading={loading} options={candidateOptions} placeholder="请选择活动关系" />
        </Form.Item>
        <Form.Item label="新手机号" htmlFor="parent-manual-recovery-new-mobile" required>
          <Space.Compact block>
            <Form.Item noStyle name="newMobile" rules={[
              { required: true, message: '请输入新手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
            ]}>
              <Input
                id="parent-manual-recovery-new-mobile"
                inputMode="numeric"
                maxLength={11}
                autoComplete="off"
              />
            </Form.Item>
            <Button loading={sending} onClick={() => void issueCode()}>发送验证码</Button>
          </Space.Compact>
        </Form.Item>
        <Form.Item label="验证码" name="smsCode" rules={[
          { required: true, message: '请输入验证码' },
          { pattern: /^\d{6}$/, message: '验证码必须为 6 位数字' }
        ]}>
          <Input inputMode="numeric" maxLength={6} autoComplete="one-time-code" />
        </Form.Item>
        <Form.Item label="核验原因" name="reason" rules={[
          { required: true, message: '请输入核验原因' },
          { max: 200, message: '核验原因不能超过 200 个字符' }
        ]}>
          <Input.TextArea rows={4} maxLength={200} showCount />
        </Form.Item>
        <Form.Item label="确认语句" name="confirmation" extra={`请输入“${CONFIRMATION}”`} rules={[
          { required: true, message: '请输入确认语句' },
          { validator: (_, value) => value === CONFIRMATION
            ? Promise.resolve()
            : Promise.reject(new Error('确认语句不正确')) }
        ]}>
          <Input autoComplete="off" />
        </Form.Item>
        <div className="form-actions">
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" htmlType="submit" loading={submitting}>确认换绑</Button>
        </div>
      </Form>
    </Drawer>
  );
}

function candidateKey(candidate: ParentMobileManualRecoveryCandidate): string {
  return `${candidate.studentId}:${candidate.parentUserId}`;
}

function roleLabel(role: ParentMobileManualRecoveryCandidate['relationshipRole']): string {
  return role === 'PRIMARY_GUARDIAN' ? '主家长' : '副家长';
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
