import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Empty, List, Space } from 'antd';
import { rankApi as api, type WithdrawalOption } from './api';

/** 必要退出操作独立于排行入口，关闭功能或失去关系后仍可撤回本人偏好。 */
export function AnonymousRankWithdrawalPage() {
  const [items, setItems] = useState<WithdrawalOption[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const sequence = useRef(0);
  const writing = useRef(false);
  const load = useCallback(async () => {
    if (writing.current) return;
    const version = ++sequence.current;
    setBusy(true); setError(undefined); setItems([]);
    try { const result = await api.withdrawals(); if (version === sequence.current) setItems(result); }
    catch (error) { if (version === sequence.current) setError(error instanceof Error ? error.message : '读取失败'); }
    finally { if (version === sequence.current) setBusy(false); }
  }, []);
  useEffect(() => { void load(); return () => { sequence.current++; }; }, [load]);
  async function withdraw(item: WithdrawalOption) {
    if (writing.current || busy || error) return;
    writing.current = true;
    const version = ++sequence.current;
    setBusy(true);
    try {
      await api.set(item.studentId, item.classId, { enabled: false, version: item.version });
      if (version === sequence.current) setItems(current => current.filter(value => value !== item));
    } catch (error) { if (version === sequence.current) setError(error instanceof Error ? error.message : '撤回失败，请刷新'); }
    finally { writing.current = false; if (version === sequence.current) setBusy(false); }
  }
  return <div className="page-stack">
    <header className="page-heading"><h1>排行查看授权</h1><Button actionKey="rank-preferences.anonymous-rank-withdrawal-page.1" disabled={busy} onClick={() => void load()}>刷新授权</Button></header>
    {error && <Alert type="error" message={error} />}
    <List loading={busy} dataSource={items} locale={{ emptyText: <Empty description="没有已开启的查看授权" /> }}
      renderItem={(item, index) => <List.Item><Space wrap><span>查看授权 {index + 1}</span>
        <Button actionKey="rank-preferences.anonymous-rank-withdrawal-page.2" disabled={busy || !!error} onClick={() => void withdraw(item)}>撤回授权</Button></Space></List.Item>} />
  </div>;
}
