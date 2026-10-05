import { useEffect, useId, useRef, useState } from 'react';
import { Alert, Button, Form, Input, Modal } from 'antd';
import { authApi } from '../../api/auth';

interface PasswordFields { oldPassword: string; newPassword: string; confirmation: string }

/** Account settings stay available independently of configurable business menus. */
export function ChangePasswordModal({ onCancel, onSuccess }: { onCancel: () => void; onSuccess: () => void }) {
  const [form] = Form.useForm<PasswordFields>();
  const formId = useId();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submitting = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  async function submit({ oldPassword, newPassword }: PasswordFields) {
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true); setError('');
    try {
      await authApi.changePassword({ oldPassword, newPassword });
      if (mounted.current) {
        authApi.clearLocalSession();
        onSuccess();
      }
    } catch (cause) {
      if (mounted.current) setError(cause instanceof Error ? cause.message : '密码修改失败，请重试');
    } finally {
      submitting.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  return <Modal open title="修改密码" width="min(440px, 92vw)" closable={!busy} maskClosable={false} keyboard={!busy}
    onCancel={() => { if (!submitting.current) onCancel(); }}
    footer={<><Button aria-label="取消" disabled={busy} onClick={onCancel}>取消</Button><Button aria-label="确认修改" type="primary" form={formId} htmlType="submit" loading={busy} disabled={busy}>确认修改</Button></>}>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Form id={formId} form={form} layout="vertical" disabled={busy} onFinish={submit} requiredMark={false}>
      <Form.Item label="旧密码" name="oldPassword" rules={[{ required: true, message: '请输入旧密码' }]}>
        <Input.Password autoComplete="current-password" maxLength={64} />
      </Form.Item>
      <Form.Item label="新密码" name="newPassword" dependencies={['oldPassword']} rules={[
        { required: true, message: '请输入新密码' },
        { pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/, message: '密码必须为 8 至 20 位字母和数字组合' },
        ({ getFieldValue }) => ({ validator: (_, value) => !value || value !== getFieldValue('oldPassword')
          ? Promise.resolve() : Promise.reject(new Error('新密码不能与旧密码相同')) })
      ]}>
        <Input.Password autoComplete="new-password" maxLength={20} placeholder="8—20位字母和数字组合" />
      </Form.Item>
      <Form.Item label="确认新密码" name="confirmation" dependencies={['newPassword']} rules={[
        { required: true, message: '请再次输入新密码' },
        ({ getFieldValue }) => ({ validator: (_, value) => !value || value === getFieldValue('newPassword')
          ? Promise.resolve() : Promise.reject(new Error('两次新密码不一致')) })
      ]}>
        <Input.Password autoComplete="new-password" maxLength={20} />
      </Form.Item>
    </Form>
  </Modal>;
}
