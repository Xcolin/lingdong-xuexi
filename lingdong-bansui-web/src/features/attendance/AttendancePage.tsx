import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Form, Input, Select, Space, Spin, Tag } from 'antd';
import { ClipboardCheck, Eye, RefreshCw, RotateCcw, Search } from 'lucide-react';
import { authApi, type CurrentUser } from '../../api/auth';
import { capabilityApi, type ClientCapabilities } from '../../api/capability';
import { ApiRequestError } from '../../api/http';
import { attendanceApi, type AttendanceClass, type AttendanceQuery, type AttendanceResult, type AttendanceStatus } from './api';
import { canAccessAttendance, canRecordAttendance, normalizeFilters, statusLabels } from './rules';
import { formatDateTime } from '../../utils/datetime';
import { AttendanceRecordDrawer } from './AttendanceRecordDrawer';
import { AttendanceDetailDrawer } from './AttendanceDetailDrawer';
import './attendance.css';

interface Props {
  currentUser: CurrentUser;
  onAccessUpdated?: (user: CurrentUser, capabilities: ClientCapabilities) => void;
}
export function AttendancePage({ currentUser, onAccessUpdated }: Props) {
  const [user, setUser] = useState<CurrentUser>();
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState<string>();
  const generation = useRef(0);
  const checkAccess = useCallback(async (block = false): Promise<CurrentUser | null> => {
    const request = ++generation.current;
    if (block) { setChecking(true); setUser(undefined); }
    try {
      const [freshUser, capabilities] = await Promise.all([authApi.currentUser(), capabilityApi.web()]);
      if (request !== generation.current) return null;
      onAccessUpdated?.(freshUser, capabilities);
      if (!canAccessAttendance(freshUser, capabilities.attendanceManagementEnabled)) {
        setUser(undefined); setError('考勤功能已停用或无访问权限'); return null;
      }
      setUser(freshUser); setError(undefined); return freshUser;
    } catch (e) {
      if (request === generation.current) { setUser(undefined); setError(`考勤访问校验失败：${toMessage(e)}`); }
      return null;
    } finally { if (request === generation.current) setChecking(false); }
  }, [onAccessUpdated]);

  const identity = `${currentUser.userId}:${currentUser.roleCodes.join(',')}:${currentUser.permissionCodes.join(',')}`;
  useEffect(() => {
    // 直达与重新聚焦都重新校验；卸载业务组件同时清除台账、名单、草稿和历史缓存。
    void checkAccess(true);
    const refresh = () => { void checkAccess(true); };
    const visible = () => { if (document.visibilityState === 'visible') refresh(); };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', visible);
    return () => { ++generation.current; window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', visible); };
  }, [identity, checkAccess]);

  const handleAccessError = useCallback((e: unknown) => {
    if (e instanceof ApiRequestError && (e.status === 401 || e.status === 403 || e.code?.includes('DISABLED'))) void checkAccess(true);
  }, [checkAccess]);
  if (checking) return <div className="route-loading"><Spin tip="正在校验考勤权限"><div /></Spin></div>;
  if (!user) return <Alert type="warning" showIcon message={error} action={<Button actionKey="attendance-records.attendance-page.1" icon={<RefreshCw size={16} />} onClick={() => void checkAccess(true)}>重试</Button>} />;
  return <AttendanceLedger key={user.userId} currentUser={user} checkAccess={checkAccess} onAccessError={handleAccessError} />;
}

function AttendanceLedger({ currentUser, checkAccess, onAccessError }: {
  currentUser: CurrentUser; checkAccess: () => Promise<CurrentUser | null>; onAccessError: (e: unknown) => void;
}) {
  const [result, setResult] = useState<AttendanceResult>({ items: [], total: 0, page: 1, pageSize: 20 });
  const [query, setQuery] = useState<AttendanceQuery>({ page: 1, pageSize: 20 });
  const [classes, setClasses] = useState<AttendanceClass[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [classError, setClassError] = useState<string>();
  const [revision, setRevision] = useState(0);
  const [recordOpen, setRecordOpen] = useState(false);
  const [detailId, setDetailId] = useState<string>();
  const [form] = Form.useForm<AttendanceQuery>();
  const canRecord = canRecordAttendance(currentUser);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(undefined); setResult({ items: [], total: 0, page: query.page ?? 1, pageSize: query.pageSize ?? 20 });
    void attendanceApi.list(query).then((data) => { if (active) setResult(data); })
      .catch((e: unknown) => { if (active) { setError(toMessage(e)); onAccessError(e); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [query, revision, onAccessError]);
  useEffect(() => {
    let active = true; setClassError(undefined);
    void attendanceApi.classes().then((data) => { if (active) setClasses(data); })
      .catch((e: unknown) => { if (active) { setClasses([]); setClassError(`班级选项加载失败：${toMessage(e)}`); onAccessError(e); } });
    return () => { active = false; };
  }, [revision, onAccessError]);

  function search(values: AttendanceQuery) {
    try { setQuery({ ...normalizeFilters(values), page: 1, pageSize: query.pageSize }); }
    catch (e) { setError(toMessage(e)); }
  }
  return <div className="page-stack attendance-page">
    <div className="page-heading"><h1>考勤台账</h1><Space wrap>
      {canRecord && <Button actionKey="ATTENDANCE_RECORD" type="primary" icon={<ClipboardCheck size={16} />} onClick={() => setRecordOpen(true)}>班级点名</Button>}
    </Space></div>
    {(error || classError) && <Alert type="error" showIcon message={error || classError} action={<Button actionKey="attendance-records.attendance-page.4" onClick={() => setRevision((value) => value + 1)}>重试</Button>} />}
    <Form form={form} layout="inline" className="directory-filters attendance-filters" onFinish={search}>
      <Form.Item name="classOrganizationId" label="班级"><Select aria-label="筛选班级" allowClear showSearch optionFilterProp="label" className="filter-select" options={classes.map((item) => ({ value: item.classOrganizationId, label: item.className }))} /></Form.Item>
      <Form.Item name="keyword" label="学生关键字"><Input aria-label="学生关键字" allowClear /></Form.Item>
      <Form.Item name="status" label="状态"><Select aria-label="筛选状态" allowClear className="filter-select" options={Object.entries(statusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="dateFrom" label="开始日期"><Input aria-label="开始日期" type="date" /></Form.Item>
      <Form.Item name="dateTo" label="结束日期"><Input aria-label="结束日期" type="date" /></Form.Item>
      <Form.Item><Space><Button actionKey="attendance-records.attendance-page.5" htmlType="submit" icon={<Search size={16} />}>查询</Button><Button actionKey="attendance-records.attendance-page.6" icon={<RotateCcw size={16} />} onClick={() => { form.resetFields(); setQuery({ page: 1, pageSize: 20 }); }}>重置</Button></Space></Form.Item>
    </Form>
    <Table rowKey="id" loading={loading} dataSource={result.items} scroll={{ x: 1200 }} locale={{ emptyText: '暂无考勤记录' }}
      pagination={{ current: query.page, pageSize: query.pageSize, total: result.total, showSizeChanger: true, pageSizeOptions: [20, 50, 100], onChange: (page, pageSize) => setQuery({ ...query, page: pageSize === query.pageSize ? page : 1, pageSize }) }}
      columns={[
        { title: '日期', dataIndex: 'attendanceDate', width: 115 }, { title: '班级', dataIndex: 'className', width: 150 },
        { title: '学生', dataIndex: 'studentName', width: 110 }, { title: '状态', dataIndex: 'status', width: 90, render: (status: AttendanceStatus) => <Tag>{statusLabels[status]}</Tag> },
        { title: '签到', dataIndex: 'checkinTime', render: empty }, { title: '签退', dataIndex: 'checkoutTime', render: empty },
        { title: '来源', dataIndex: 'source', render: (value) => value === 'MANUAL' ? '人工登记' : value },
        { title: '最近登记人', dataIndex: 'recorderName' }, { title: '更新时间', dataIndex: 'updatedAt', render: formatTime },
        { title: '操作', key: 'action', fixed: 'right', width: 85, render: (_, row) => <Button actionKey="attendance-records.attendance-page.7" type="text" icon={<Eye size={16} />} aria-label={`查看${row.studentName}的考勤详情`} onClick={() => setDetailId(row.id)}>详情</Button> }
      ]} />
    {recordOpen && canRecord && <AttendanceRecordDrawer checkAccess={checkAccess} onAccessError={onAccessError} onClose={() => setRecordOpen(false)} onSaved={() => { setRecordOpen(false); setRevision((value) => value + 1); }} />}
    {detailId && <AttendanceDetailDrawer id={detailId} onClose={() => setDetailId(undefined)} onAccessError={onAccessError} />}
  </div>;
}
export function toMessage(e: unknown): string { return e instanceof Error ? e.message : '请求未能完成，请重试'; }
export function empty(value: string | null): string { return value || '-'; }
export function formatTime(value: string): string { return formatDateTime(value); }
