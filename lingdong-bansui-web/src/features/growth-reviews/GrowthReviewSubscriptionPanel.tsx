import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Space, Switch, Tooltip } from 'antd';
import { RefreshCw } from 'lucide-react';
import { growthReviewSubscriptionApi as api, type GrowthReviewSubscription } from './subscriptionApi';

interface Props { studentId: string; canEnable: boolean }

/** 以孩子为组件生命周期边界，旧请求不能写入另一孩子的偏好。 */
export function GrowthReviewSubscriptionPanel(props: Props) {
  return <SubscriptionState key={props.studentId} {...props} />;
}

function SubscriptionState({ studentId, canEnable }: Props) {
  const [value, setValue] = useState<GrowthReviewSubscription>();
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string>();
  const sequence = useRef(0);
  const writing = useRef(false);
  const load = useCallback(async () => {
    if (writing.current) return;
    const current = ++sequence.current;
    setBusy(true);
    setError(undefined);
    try {
      const result = await api.get(studentId);
      if (current === sequence.current) setValue(result);
    } catch (cause) {
      if (current === sequence.current) setError(cause instanceof Error ? cause.message : '订阅状态读取失败');
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  }, [studentId]);
  useEffect(() => {
    void load();
    return () => { sequence.current++; };
  }, [load]);

  async function change(enabled: boolean) {
    if (!value || busy || error || writing.current || (enabled && !canEnable)) return;
    writing.current = true;
    const current = ++sequence.current;
    setBusy(true);
    try {
      const result = await api.set(studentId, { enabled, version: value.version });
      if (current === sequence.current) setValue(result);
    } catch (cause) {
      // 失败可能意味着版本冲突或结果不确定，重新读取前禁止再次写入。
      if (current === sequence.current) setError(cause instanceof Error ? cause.message : '订阅状态保存失败');
    } finally {
      writing.current = false;
      if (current === sequence.current) setBusy(false);
    }
  }

  return <section aria-label="周报订阅" style={{ width: '100%' }}>
    <Space wrap>
      <span>周报订阅</span>
      <Switch aria-label="周报订阅" checked={value?.enabled ?? false} loading={busy}
        disabled={busy || !value || !!error || (!value.enabled && !canEnable)}
        onChange={enabled => void change(enabled)} />
      <Tooltip title="刷新订阅状态"><Button aria-label="刷新订阅状态" icon={<RefreshCw size={16} />}
        disabled={busy} onClick={() => void load()} /></Tooltip>
    </Space>
    {error && <Alert type="error" showIcon message={error} style={{ marginTop: 8 }} />}
  </section>;
}
