import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useRef, useState } from 'react';
import { Alert, Drawer, Form, Input, Select, Space, message } from 'antd';
import { RefreshCw, Save } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import { attendanceApi, type AttendanceClass, type AttendanceRosterRow } from './api';
import { buildBatch, canRecordAttendance, shanghaiToday, statusLabels, type AttendanceDraft } from './rules';

export function AttendanceRecordDrawer({ checkAccess, onAccessError, onClose, onSaved }: {
  checkAccess: () => Promise<CurrentUser | null>; onAccessError: (e: unknown) => void; onClose: () => void; onSaved: () => void;
}) {
  const [classes, setClasses] = useState<AttendanceClass[]>([]);
  const [classId, setClassId] = useState<string>();
  const [date, setDate] = useState(shanghaiToday);
  const [roster, setRoster] = useState<AttendanceRosterRow[]>([]);
  const [drafts, setDrafts] = useState<Record<string, AttendanceDraft>>({});
  const [loading, setLoading] = useState(false);
  const [classLoading, setClassLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();
  const [classError, setClassError] = useState<string>();
  const [revision, setRevision] = useState(0);
  const alive = useRef(true);
  const submittingRef = useRef(false);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    let active = true; setClassLoading(true); setClassError(undefined);
    void attendanceApi.classes(true).then((data) => { if (active) setClasses(data); })
      .catch((e: unknown) => { if (active) { setClasses([]); setClassError(toMessage(e)); onAccessError(e); } })
      .finally(() => { if (active) setClassLoading(false); });
    return () => { active = false; };
  }, [revision, onAccessError]);
  useEffect(() => {
    let active = true;
    setRoster([]); setDrafts({}); setError(undefined);
    if (!classId || !date || date > shanghaiToday()) { setLoading(false); return; }
    setLoading(true);
    void attendanceApi.roster(classId, date).then((data) => {
      if (!active) return;
      setRoster(data);
      // 保留原始时间和版本，但每位学生仍须明确选择本次提交的状态。
      setDrafts(Object.fromEntries(data.map((row) => [row.studentId, { checkinTime: row.record?.checkinTime ?? '', checkoutTime: row.record?.checkoutTime ?? '' }])));
    }).catch((e: unknown) => { if (active) { setError(toMessage(e)); onAccessError(e); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [classId, date, revision, onAccessError]);

  function update(id: string, value: Partial<AttendanceDraft>) {
    setDrafts((current) => ({ ...current, [id]: { ...current[id], ...value } }));
  }
  async function submit() {
    if (submittingRef.current || loading || classLoading || classError) return;
    try {
      const payload = buildBatch(classId ?? '', date, roster, drafts);
      submittingRef.current = true; setSubmitting(true); setError(undefined);
      const freshUser = await checkAccess();
      if (!alive.current || !freshUser) return;
      if (!canRecordAttendance(freshUser)) { setError('考勤登记权限已变更'); return; }
      // 不拆批、不自动重试版本冲突，确保整笔请求由服务端原子提交。
      await attendanceApi.batch(payload);
      if (alive.current) { message.success('考勤已提交'); onSaved(); }
    } catch (e) { if (alive.current) { setError(toMessage(e)); onAccessError(e); } }
    finally { submittingRef.current = false; if (alive.current) setSubmitting(false); }
  }
  const selected = Object.values(drafts).filter((draft) => draft.status).length;
  return <Drawer title="班级点名" open width="min(1100px, 92vw)" onClose={onClose} closable={!submitting} maskClosable={!submitting} keyboard={!submitting}
    footer={<Space wrap><span>已选择 {selected} / 100 人</span><Button actionKey="attendance-records.attendance-record-drawer.1" disabled={submitting} onClick={onClose}>取消</Button>
      <Button actionKey="attendance-records.attendance-record-drawer.2" type="primary" icon={<Save size={16} />} loading={submitting} disabled={loading || classLoading || Boolean(classError) || selected < 1 || selected > 100} onClick={() => void submit()}>提交考勤</Button></Space>}>
    <div className="page-stack attendance-page">
      {(error || classError) && <Alert type="error" showIcon message={error || classError} />}
      {date > shanghaiToday() && <Alert type="warning" message="不能登记未来日期的考勤" />}
      <Form layout="inline" className="attendance-filters">
        <Form.Item label="班级"><Select aria-label="点名班级" className="filter-select" loading={classLoading} disabled={submitting} value={classId} onChange={(value) => { setRoster([]); setDrafts({}); setClassId(value); }} options={classes.map((item) => ({ value: item.classOrganizationId, label: item.className }))} /></Form.Item>
        <Form.Item label="考勤日期"><Input aria-label="考勤日期" type="date" max={shanghaiToday()} disabled={submitting} value={date} onChange={(e) => { setRoster([]); setDrafts({}); setDate(e.target.value); }} /></Form.Item>
        <Form.Item><Button actionKey="attendance-records.attendance-record-drawer.3" icon={<RefreshCw size={16} />} disabled={submitting} onClick={() => setRevision((value) => value + 1)}>重新加载名单</Button></Form.Item>
      </Form>
      <Table rowKey="studentId" loading={loading} dataSource={roster} pagination={false} scroll={{ x: 720, y: 480 }} locale={{ emptyText: classId ? '暂无可登记学生' : '请选择班级' }} columns={[
        { title: '学生', dataIndex: 'studentName', width: 110 },
        { title: '原记录', width: 105, render: (_, row) => row.record ? statusLabels[row.record.status] : '未登记' },
        { title: '本次状态', width: 155, render: (_, row) => <Select aria-label={`${row.studentName}的考勤状态`} style={{ width: '100%' }} allowClear placeholder="未选择" disabled={submitting} value={drafts[row.studentId]?.status} options={Object.entries(statusLabels).map(([value, label]) => ({ value, label }))} onChange={(status) => update(row.studentId, { status, ...(['ABSENT', 'LEAVE'].includes(status) ? { checkinTime: '', checkoutTime: '' } : {}) })} /> },
        ...(['checkinTime', 'checkoutTime'] as const).map((field) => ({ title: field === 'checkinTime' ? '签到时间' : '签退时间', width: 170, key: field, render: (_: unknown, row: AttendanceRosterRow) => <Input type="time" step={60} aria-label={`${row.studentName}的${field === 'checkinTime' ? '签到' : '签退'}时间`} value={drafts[row.studentId]?.[field] ?? ''} disabled={submitting || !drafts[row.studentId]?.status || ['ABSENT', 'LEAVE'].includes(drafts[row.studentId]?.status ?? '')} onChange={(e) => update(row.studentId, { [field]: e.target.value })} /> }))
      ]} />
    </div>
  </Drawer>;
}
function toMessage(e: unknown): string { return e instanceof Error ? e.message : '考勤请求失败，请重试'; }
