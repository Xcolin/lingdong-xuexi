import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Space, Spin, Typography, message } from 'antd';
import { Check, X } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { authApi } from '../../api/auth';
import { capabilityApi } from '../../api/capability';
import { parentRelationshipApi } from './api';

interface InvitationFormValues {
  mobile: string;
  smsCode: string;
  agreementAccepted: boolean;
}

const DEVICE_ID_KEY = 'lingdong-learning.web.device-id';

/** 未登录用户使用的家长关系邀请接受与拒绝页面。 */
export function ParentRelationshipInvitationPage() {
  const { invitationId } = useParams<{ invitationId: string }>();
  const navigate = useNavigate();
  const [form] = Form.useForm<InvitationFormValues>();
  const [loading, setLoading] = useState(true);
  const [enabled, setEnabled] = useState(false);
  const [agreementVersion, setAgreementVersion] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    void Promise.all([capabilityApi.web(), authApi.parentAuthContext()])
      .then(([capabilities, context]) => {
        setEnabled(capabilities.parentRelationshipManagementEnabled);
        setAgreementVersion(context.agreementVersion);
      })
      .catch((error) => setErrorMessage(toMessage(error)))
      .finally(() => setLoading(false));
  }, []);

  async function accept(values: InvitationFormValues): Promise<void> {
    if (!invitationId) return;
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const result = await parentRelationshipApi.acceptInvitation(invitationId, {
        mobile: values.mobile,
        smsCode: values.smsCode,
        clientType: 'WEB',
        deviceId: getDeviceId(),
        deviceName: '灵动学习 Web 端',
        agreementAccepted: values.agreementAccepted,
        agreementVersion
      });
      message.success('家长关系邀请已接受');
      navigate(
        result.onboardingRequired || result.agreementAcceptanceRequired
          ? '/parent-onboarding' : '/dashboard',
        { replace: true }
      );
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function reject(): Promise<void> {
    if (!invitationId) return;
    const values = await form.validateFields(['mobile', 'smsCode']);
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await parentRelationshipApi.rejectInvitation(invitationId, {
        mobile: values.mobile,
        smsCode: values.smsCode,
        clientType: 'WEB',
        deviceId: getDeviceId(),
        deviceName: '灵动学习 Web 端',
        agreementAccepted: false,
        agreementVersion
      });
      message.success('已拒绝家长关系邀请');
      navigate('/login', { replace: true });
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="invitation-page">
      <section className="invitation-surface" aria-label="家长关系邀请确认">
        <Typography.Title level={2}>家长关系邀请</Typography.Title>
        {loading ? <div className="route-loading"><Spin /></div> : !enabled ? (
          <Alert type="warning" showIcon message="家长关系功能未启用" />
        ) : <>
          {errorMessage && <Alert className="invitation-alert" type="error" showIcon message={errorMessage} />}
          <Form form={form} layout="vertical" requiredMark={false} onFinish={(values) => void accept(values)}>
            <Form.Item name="mobile" label="手机号" rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
            ]}>
              <Input inputMode="tel" autoComplete="tel" maxLength={11} />
            </Form.Item>
            <Form.Item name="smsCode" label="验证码" rules={[
              { required: true, message: '请输入验证码' },
              { pattern: /^\d{6}$/, message: '请输入 6 位验证码' }
            ]}>
              <Input inputMode="numeric" autoComplete="one-time-code" maxLength={6} />
            </Form.Item>
            <Form.Item name="agreementAccepted" valuePropName="checked" rules={[
              { validator: (_, checked) => checked
                ? Promise.resolve()
                : Promise.reject(new Error('请先同意用户协议')) }
            ]}>
              <Checkbox>我已阅读并同意当前用户协议</Checkbox>
            </Form.Item>
            <Space className="invitation-actions">
              <Button icon={<X size={16} />} disabled={submitting} onClick={() => void reject()}>
                拒绝邀请
              </Button>
              <Button type="primary" htmlType="submit" icon={<Check size={16} />} loading={submitting}>
                确认接受
              </Button>
            </Space>
          </Form>
        </>}
      </section>
    </main>
  );
}

function getDeviceId(): string {
  const current = localStorage.getItem(DEVICE_ID_KEY);
  if (current) return current;
  const deviceId = crypto.randomUUID();
  localStorage.setItem(DEVICE_ID_KEY, deviceId);
  return deviceId;
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
