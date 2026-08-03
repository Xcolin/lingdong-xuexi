import { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { KeyRound, LogIn } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';

interface LoginValues {
  username: string;
  password: string;
}

const DEVICE_ID_KEY = 'lingdong-learning.web.device-id';

export function LoginPage() {
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (authApi.hasLocalSession()) {
      navigate('/dashboard', { replace: true });
    }
  }, [navigate]);

  async function submit(values: LoginValues): Promise<void> {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await authApi.login({
        ...values,
        deviceId: getDeviceId(),
        deviceName: '灵动学习管理端'
      });
      await authApi.currentUser();
      navigate('/dashboard', { replace: true });
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
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
            <Input autoComplete="username" size="large" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="current-password" size="large" />
          </Form.Item>
          <Button block type="primary" size="large" htmlType="submit" loading={submitting} icon={<LogIn size={17} />}>登录</Button>
        </Form>
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
