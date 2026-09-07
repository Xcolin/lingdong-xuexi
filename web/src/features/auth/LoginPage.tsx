import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Segmented, Spin, Typography } from 'antd';
import { KeyRound, LogIn, MessageSquareText } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';

interface LoginValues {
  username: string;
  password: string;
}

interface SmsLoginValues {
  mobile: string;
  code: string;
  agreementAccepted: boolean;
}

const DEVICE_ID_KEY = 'lingdong-learning.web.device-id';

export function LoginPage() {
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [context, setContext] = useState<Awaited<ReturnType<typeof authApi.parentAuthContext>> | null>(null);
  const [mode, setMode] = useState<'sms' | 'password'>('password');
  const [countdown, setCountdown] = useState(0);
  const [sendingCode, setSendingCode] = useState(false);
  const [smsForm] = Form.useForm<SmsLoginValues>();

  useEffect(() => {
    if (authApi.hasLocalSession()) {
      navigate('/dashboard', { replace: true });
    }
    void authApi.parentAuthContext()
      .then((loaded) => {
        setContext(loaded);
        if (loaded.enabled) setMode('sms');
      })
      .catch(() => setContext({
        enabled: false, agreementVersion: '', codeExpiresInSeconds: 300, retryAfterSeconds: 60
      }));
  }, [navigate]);

  useEffect(() => {
    if (countdown <= 0) return undefined;
    const timer = window.setInterval(() => setCountdown((current) => Math.max(0, current - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [countdown]);

  async function submit(values: LoginValues): Promise<void> {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await authApi.login({
        ...values,
        deviceId: getDeviceId(),
        deviceName: '灵动学习管理端'
      });
      const user = await authApi.currentUser();
      if (user.roleCodes.includes('PARENT')) {
        const state = await authApi.parentState();
        navigate(
          state.onboardingRequired || state.agreementAcceptanceRequired ? '/parent-onboarding' : '/dashboard',
          { replace: true }
        );
      } else {
        navigate('/dashboard', { replace: true });
      }
    } catch (error) {
      authApi.clearLocalSession();
      setErrorMessage(error instanceof Error ? error.message : '登录未能完成');
    } finally {
      setSubmitting(false);
    }
  }

  async function sendCode(): Promise<void> {
    const mobile = await smsForm.validateFields(['mobile']).then((values) => values.mobile);
    setSendingCode(true);
    setErrorMessage(null);
    try {
      const issued = await authApi.issueParentSmsCode({
        mobile, purpose: 'REGISTER_OR_LOGIN', clientType: 'WEB'
      });
      setCountdown(issued.retryAfterSeconds);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '验证码发送失败');
    } finally {
      setSendingCode(false);
    }
  }

  async function submitSms(values: SmsLoginValues): Promise<void> {
    if (!context?.enabled) return;
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const result = await authApi.loginParentBySms({
        mobile: values.mobile,
        code: values.code,
        clientType: 'WEB',
        deviceId: getDeviceId(),
        deviceName: '灵动学习 Web 端',
        agreementAccepted: values.agreementAccepted,
        agreementVersion: context.agreementVersion
      });
      await authApi.currentUser();
      navigate(result.onboardingRequired ? '/parent-onboarding' : '/dashboard', { replace: true });
    } catch (error) {
      authApi.clearLocalSession();
      setErrorMessage(error instanceof Error ? error.message : '登录未能完成');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-surface" aria-label="平台账号登录">
        <div className="login-mark"><KeyRound size={24} aria-hidden="true" /></div>
        <Typography.Title level={2}>灵动学习</Typography.Title>
        <Typography.Text type="secondary">管理端</Typography.Text>
        {errorMessage && <Alert className="login-alert" type="error" showIcon message={errorMessage} />}
        {!context ? <div className="route-loading"><Spin /></div> : <>
        {context.enabled && <Segmented
          block
          value={mode}
          onChange={(value) => setMode(value as 'sms' | 'password')}
          options={[
            { label: '验证码登录', value: 'sms', icon: <MessageSquareText size={16} /> },
            { label: '密码登录', value: 'password', icon: <KeyRound size={16} /> }
          ]}
        />}
        {!context.enabled && <Typography.Text strong>密码登录</Typography.Text>}
        {context.enabled && mode === 'sms' ? <Form
          form={smsForm}
          layout="vertical"
          onFinish={submitSms}
          requiredMark={false}
          className="login-form"
        >
          <Form.Item name="mobile" label="手机号" rules={[
            { required: true, message: '请输入手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
          ]}>
            <Input inputMode="tel" autoComplete="tel" size="large" maxLength={11} />
          </Form.Item>
          <Form.Item label="验证码" required>
            <div className="login-code-row">
              <Form.Item name="code" noStyle rules={[
                { required: true, message: '请输入验证码' },
                { pattern: /^\d{6}$/, message: '请输入 6 位验证码' }
              ]}>
                <Input inputMode="numeric" autoComplete="one-time-code" size="large" maxLength={6} />
              </Form.Item>
              <Button size="large" onClick={() => void sendCode()} loading={sendingCode} disabled={countdown > 0}>
                {countdown > 0 ? `${countdown} 秒` : '获取验证码'}
              </Button>
            </div>
          </Form.Item>
          <Form.Item name="agreementAccepted" valuePropName="checked" rules={[
            { validator: (_, checked) => checked ? Promise.resolve() : Promise.reject(new Error('请先同意用户协议')) }
          ]}>
            <Checkbox>我已阅读并同意当前用户协议</Checkbox>
          </Form.Item>
          <Button block type="primary" size="large" htmlType="submit" loading={submitting} icon={<LogIn size={17} />}>登录</Button>
        </Form> : <Form layout="vertical" onFinish={submit} requiredMark={false} className="login-form">
          <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
            <Input autoComplete="username" size="large" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="current-password" size="large" />
          </Form.Item>
          <Button block type="primary" size="large" htmlType="submit" loading={submitting} icon={<LogIn size={17} />}>登录</Button>
        </Form>
        }</>}
      </section>
    </main>
  );
}

function getDeviceId(): string {
  const current = localStorage.getItem(DEVICE_ID_KEY);
  if (current) {
    return current;
  }
  const deviceId = crypto.randomUUID();
  localStorage.setItem(DEVICE_ID_KEY, deviceId);
  return deviceId;
}
