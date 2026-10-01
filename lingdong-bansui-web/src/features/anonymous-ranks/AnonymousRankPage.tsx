import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Empty, Select, Space, Spin, Switch } from 'antd';
import { rankApi as api, type StudentOption, type ClassOption, type Preference, type RankRow } from './api';
const reason = (error: unknown) => error instanceof Error ? error.message : '读取失败，请刷新重试';

export function AnonymousRankPage() {
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [student, setStudent] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(undefined); setStudents([]); setStudent(undefined);
    void api.students().then(items => { if (active) { setStudents(items); setStudent(items[0]?.studentId); } })
      .catch(error => { if (active) setError(reason(error)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reload]);
  return <div className="page-stack">
    <header className="page-heading"><h1>班级匿名排行</h1>
      <Button actionKey="anonymous-ranks.anonymous-rank-page.1" disabled={loading} onClick={() => setReload(value => value + 1)}>刷新孩子列表</Button></header>
    {error && <Alert type="error" message={error} />}
    {loading ? <Spin /> : <Select aria-label="选择孩子" style={{ width: '100%', maxWidth: 320 }} value={student}
      options={students.map(item => ({ value: item.studentId, label: item.studentName }))} onChange={setStudent} />}
    {!loading && !error && !students.length && <Empty description="暂无可查看的孩子" />}
    {student && <StudentClasses key={student} student={student} />}
  </div>;
}

function StudentClasses({ student }: { student: string }) {
  const [classes, setClasses] = useState<ClassOption[]>([]);
  const [classroom, setClassroom] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    void api.classes(student).then(items => { if (active) { setClasses(items); setClassroom(items[0]?.classId); } })
      .catch(error => { if (active) setError(reason(error)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [student]);
  return <>
    {error && <Alert type="error" message={error} />}
    {loading ? <Spin /> : <Select aria-label="选择班级" style={{ width: '100%', maxWidth: 320 }} value={classroom}
      options={classes.map(item => ({ value: item.classId, label: item.className }))} onChange={setClassroom} />}
    {!loading && !error && !classes.length && <Empty description="暂无有效班级" />}
    {classroom && <Ranking key={classroom} student={student} classroom={classroom} />}
  </>;
}

/** 切换孩子或班级时销毁旧实例；旧响应不能回填新的范围。 */
function Ranking({ student, classroom }: { student: string; classroom: string }) {
  const [preference, setPreference] = useState<Preference>();
  const [rows, setRows] = useState<RankRow[]>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [writeError, setWriteError] = useState(false);
  const sequence = useRef(0);
  const pending = useRef(false);
  const load = useCallback(async () => {
    if (pending.current) return;
    pending.current = true;
    const version = ++sequence.current;
    setBusy(true); setRows(undefined); setError(undefined); setWriteError(false);
    try {
      const value = await api.preference(student, classroom);
      if (version !== sequence.current) return;
      setPreference(value);
      if (value.enabled) {
        const result = await api.ranking(student, classroom);
        if (version === sequence.current) setRows(result);
      }
    } catch (error) { if (version === sequence.current) setError(reason(error)); }
    finally { if (version === sequence.current) { pending.current = false; setBusy(false); } }
  }, [student, classroom]);
  useEffect(() => { void load(); return () => { sequence.current++; pending.current = false; }; }, [load]);
  async function change(enabled: boolean) {
    if (!preference || pending.current || writeError) return;
    pending.current = true;
    const version = ++sequence.current;
    setBusy(true); setRows(undefined); setError(undefined);
    let saved = false;
    try {
      const value = await api.set(student, classroom, { enabled, version: preference.version });
      saved = true;
      if (version !== sequence.current) return;
      setPreference(value);
      if (value.enabled) {
        const result = await api.ranking(student, classroom);
        if (version === sequence.current) setRows(result);
      }
    } catch (error) {
      if (version === sequence.current) { setError(reason(error)); setWriteError(!saved); }
    } finally { if (version === sequence.current) { pending.current = false; setBusy(false); } }
  }
  return <section aria-label="匿名排行">
    <Space wrap><span>主动开启查看</span><Switch aria-label="主动开启查看" checked={preference?.enabled ?? false}
      loading={busy} disabled={busy || !preference || writeError} onChange={value => void change(value)} />
      <Button actionKey="anonymous-ranks.anonymous-rank-page.2" disabled={busy} onClick={() => void load()}>刷新排行</Button></Space>
    {error && <Alert style={{ marginTop: 12 }} type="error" message={error} />}
    {!preference?.enabled && !busy && <p>默认关闭，主动开启后可查看班级匿名排行。</p>}
    {rows && <Table style={{ marginTop: 16 }} pagination={false} dataSource={rows.map((row, index) => ({ ...row, key: index }))}
      locale={{ emptyText: '暂无排行数据' }} columns={[{ title: '名次', dataIndex: 'rank' }, { title: '积分', dataIndex: 'points' }]} />}
  </section>;
}
