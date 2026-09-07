import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Input, Modal, Popconfirm, Space, Tag, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { KeyRound, Send, Smartphone, UserRoundX } from 'lucide-react';
import { authApi, type ParentAccountLifecycleState } from '../../api/auth';

interface ParentAccountLifecyclePanelProps {
  onSessionEnded: () => void;
}

export function ParentAccountLifecyclePanel({ onSessionEnded }: ParentAccountLifecyclePanelProps) {
  const [state, setState] = useState<ParentAccountLifecycleState | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [mobileModalOpen, setMobileModalOpen] = useState(false);
  const [mobileStage, setMobileStage] = useState<'CURRENT' | 'NEW'>('CURRENT');
  const [currentCode, setCurrentCode] = useState('');
  const [ticket, setTicket] = useState('');
  const [newMobile, setNewMobile] = useState('');
  const [newCode, setNewCode] = useState('');
  const [cancellationModalOpen, setCancellationModalOpen] = useState(false);
  const [cancellationCode, setCancellationCode] = useState('');
  const [confirmation, setConfirmation] = useState('');

  useEffect(() => { void loadState(); }, []);

  async function loadState(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      setState(await authApi.getParentAccountLifecycle());
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function run(action: () => Promise<void>): Promise<void> {
    setLoading(true);
    try {
      await action();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  function openMobileModal(): void {
    setMobileStage('CURRENT');
    setCurrentCode('');
    setTicket('');
    setNewMobile('');
    setNewCode('');
    setMobileModalOpen(true);
  }

  async function verifyCurrentMobile(): Promise<void> {
    await run(async () => {
      const result = await authApi.verifyCurrentMobile(currentCode);
      setTicket(result.ticket);
      setMobileStage('NEW');
    });
  }

  async function completeMobileChange(): Promise<void> {
    await run(async () => {
      await authApi.changeParentMobile(ticket, newMobile, newCode);
      message.success('手机号已更换，请重新登录');
      authApi.clearLocalSession();
      onSessionEnded();
    });
  }

  async function requestCancellation(): Promise<void> {
    await run(async () => {
      await authApi.requestParentCancellation(cancellationCode, confirmation);
      setCancellationModalOpen(false);
      setCancellationCode('');
      setConfirmation('');
      await loadState();
      message.success('注销申请已进入 7 天冷静期');
    });
  }

  async function revokeCancellation(): Promise<void> {
    await run(async () => {
      await authApi.revokeParentCancellation();
      await loadState();
      message.success('注销申请已撤销');
    });
  }

  return <>
    <ProCard className="content-panel" title="家长账号" bordered={false} loading={loading && state === null}>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
      {state && <>
        <Descriptions column={{ xs: 1, sm: 3 }} size="small">
          <Descriptions.Item label="当前手机号">{state.maskedMobile}</Descriptions.Item>
          <Descriptions.Item label="活动学生关系">{state.activeStudentRelationshipCount}</Descriptions.Item>
          <Descriptions.Item label="注销状态">{cancellationLabel(state.cancellationStatus)}</Descriptions.Item>
        </Descriptions>
        {state.activeStudentRelationshipCount > 0 && state.cancellationStatus === 'NONE'
          && <Alert type="info" showIcon message="需先解除全部学生关系" />}
        {state.cancellationStatus === 'READY_FOR_FINALIZATION'
          && <Alert type="warning" showIcon message="冷静期已结束，账号待后续注销处理" />}
        <div className="panel-footer">
          <Space wrap>
            <Button icon={<Smartphone size={16} />} onClick={openMobileModal}>更换手机号</Button>
            {state.cancellationStatus === 'NONE' && (
              <Button danger icon={<UserRoundX size={16} />}
                disabled={state.activeStudentRelationshipCount > 0}
                onClick={() => setCancellationModalOpen(true)}>申请注销</Button>
            )}
            {state.cancellationStatus === 'COOLING_OFF' && (
              <Popconfirm title="确认撤销注销申请？" onConfirm={() => void revokeCancellation()}>
                <Button>撤销注销申请</Button>
              </Popconfirm>
            )}
            {state.cancellationStatus !== 'NONE' && <Tag>{formatTime(state.coolingEndsAt)}</Tag>}
          </Space>
        </div>
      </>}
    </ProCard>

    <Modal title="更换手机号" open={mobileModalOpen} footer={null}
      onCancel={() => setMobileModalOpen(false)} destroyOnHidden>
      {mobileStage === 'CURRENT' ? <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert type="info" showIcon message={`验证码将发送至 ${state?.maskedMobile ?? '当前手机号'}`} />
        <Button icon={<Send size={16} />} onClick={() => void run(async () => {
          await authApi.issueCurrentMobileCode();
          message.success('验证码已发送');
        })}>发送当前手机号验证码</Button>
        <Input aria-label="当前手机号验证码" value={currentCode} maxLength={6}
          onChange={(event) => setCurrentCode(event.target.value)} placeholder="6 位验证码" />
        <Button type="primary" icon={<KeyRound size={16} />} disabled={!/^\d{6}$/.test(currentCode)}
          onClick={() => void verifyCurrentMobile()}>验证当前手机号</Button>
      </Space> : <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Input aria-label="新手机号" value={newMobile} maxLength={11}
          onChange={(event) => setNewMobile(event.target.value)} placeholder="新手机号" />
        <Button icon={<Send size={16} />} disabled={!/^1[3-9]\d{9}$/.test(newMobile)}
          onClick={() => void run(async () => {
            await authApi.issueNewMobileCode(ticket, newMobile);
            message.success('验证码已发送');
          })}>发送新手机号验证码</Button>
        <Input aria-label="新手机号验证码" value={newCode} maxLength={6}
          onChange={(event) => setNewCode(event.target.value)} placeholder="6 位验证码" />
        <Button type="primary" disabled={!/^\d{6}$/.test(newCode)}
          onClick={() => void completeMobileChange()}>确认更换手机号</Button>
      </Space>}
    </Modal>

    <Modal title="申请账号注销" open={cancellationModalOpen} footer={null}
      onCancel={() => setCancellationModalOpen(false)} destroyOnHidden>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert type="warning" showIcon message="申请后进入 7 天冷静期，期间账号仍可登录并撤销申请" />
        <Button icon={<Send size={16} />} onClick={() => void run(async () => {
          await authApi.issueParentCancellationCode();
          message.success('验证码已发送');
        })}>发送注销验证码</Button>
        <Input aria-label="注销验证码" value={cancellationCode} maxLength={6}
          onChange={(event) => setCancellationCode(event.target.value)} placeholder="6 位验证码" />
        <Input aria-label="注销确认文本" value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)} placeholder="请输入“确认注销”" />
        <Button danger type="primary"
          disabled={!/^\d{6}$/.test(cancellationCode) || confirmation !== '确认注销'}
          onClick={() => void requestCancellation()}>提交注销申请</Button>
      </Space>
    </Modal>
  </>;
}

function cancellationLabel(status: ParentAccountLifecycleState['cancellationStatus']): string {
  if (status === 'COOLING_OFF') return '冷静期中';
  if (status === 'READY_FOR_FINALIZATION') return '待执行注销';
  return '未申请';
}

function formatTime(value: string | null): string {
  return value ? `冷静期截止 ${new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))}` : '';
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
