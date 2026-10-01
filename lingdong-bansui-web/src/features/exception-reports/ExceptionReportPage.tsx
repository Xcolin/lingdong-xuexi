import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useState } from 'react';
import { Alert, Descriptions, Form, Input, List, Select, Space, Tag, message } from 'antd';
import { ClipboardPlus, Eye, RotateCcw, Search, SearchCheck } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import { exceptionReportApi, type ExceptionReport, type ExceptionReportClassOption,
  type ExceptionReportDetails, type ExceptionReportQuery, type ExceptionReportStudentOption,
  type ExceptionReportType } from './api';

const typeLabels: Record<ExceptionReportType, string> = {
  ATTENDANCE: '出勤异常', LEARNING_STATUS: '学习状态异常', MENTAL_STATE: '心态异常'
};

export function ExceptionReportPage({ currentUser }: { currentUser: CurrentUser }) {
  const [items, setItems] = useState<ExceptionReport[]>([]); const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1); const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>(); const [createOpen, setCreateOpen] = useState(false);
  const [classes, setClasses] = useState<ExceptionReportClassOption[]>([]);
  const [students, setStudents] = useState<ExceptionReportStudentOption[]>([]);
  const [details, setDetails] = useState<ExceptionReportDetails>(); const [handleNote, setHandleNote] = useState('');
  const [submitting, setSubmitting] = useState(false); const [form] = Form.useForm();
  const [filters, setFilters] = useState<ExceptionReportQuery>({});
  const [filterForm] = Form.useForm<ExceptionReportQuery>();
  const canCreate = currentUser.permissionCodes.includes('EXCEPTION_REPORT_CREATE');
  const canHandle = currentUser.permissionCodes.includes('EXCEPTION_REPORT_HANDLE');

  useEffect(() => { void load(1, {}); void loadClasses(); }, []);
  async function load(nextPage: number, nextFilters: ExceptionReportQuery = filters): Promise<void> {
    setLoading(true); setError(undefined);
    try { const result = await exceptionReportApi.list({ ...nextFilters, page: nextPage, pageSize: 20 }); setItems(result.items); setTotal(result.total); setPage(result.page); }
    catch (e) { setError(toMessage(e)); } finally { setLoading(false); }
  }
  async function loadClasses(): Promise<void> {
    try { setClasses(await exceptionReportApi.classes()); } catch { setClasses([]); }
  }
  async function search(values: ExceptionReportQuery): Promise<void> {
    const nextFilters = { classOrganizationId: values.classOrganizationId,
      exceptionType: values.exceptionType, status: values.status };
    setFilters(nextFilters); await load(1, nextFilters);
  }
  async function resetFilters(): Promise<void> {
    filterForm.resetFields(); setFilters({}); await load(1, {});
  }
  async function openCreate(): Promise<void> {
    try { if (classes.length === 0) await loadClasses(); setStudents([]); form.resetFields(); setCreateOpen(true); }
    catch (e) { message.error(toMessage(e)); }
  }
  async function classChanged(classId: string): Promise<void> {
    form.setFieldValue('studentId', undefined); setStudents([]);
    try { setStudents(await exceptionReportApi.students(classId)); } catch (e) { message.error(toMessage(e)); }
  }
  async function create(values: { classOrganizationId: string; studentId: string; exceptionType: ExceptionReportType; content: string }): Promise<void> {
    setSubmitting(true);
    try { await exceptionReportApi.create({ ...values, content: values.content.trim(), idempotencyKey: crypto.randomUUID() });
      message.success('异常报备已提交'); setCreateOpen(false); await load(1, filters);
    } catch (e) { message.error(toMessage(e)); } finally { setSubmitting(false); }
  }
  async function openDetails(id: string): Promise<void> {
    try { setDetails(await exceptionReportApi.details(id)); setHandleNote(''); } catch (e) { message.error(toMessage(e)); }
  }
  async function handle(): Promise<void> {
    if (!details || !handleNote.trim()) return void message.warning('请填写处理说明');
    setSubmitting(true);
    try { await exceptionReportApi.handle(details.report.id, details.report.versionNo, handleNote.trim());
      message.success('异常报备已处理'); setDetails(undefined); await load(page, filters);
    } catch (e) { message.error(toMessage(e)); } finally { setSubmitting(false); }
  }

  return <div className="page-stack">
    <div className="page-heading"><div><h1>异常报备</h1><p>教师提交授权班级学生异常，机构管理员按组织范围处理。</p></div>
      {canCreate && <Button actionKey="exception-reports.exception-report-page.1" type="primary" icon={<ClipboardPlus size={16} />} onClick={() => void openCreate()}>新增报备</Button>}
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button actionKey="exception-reports.exception-report-page.2" icon={<SearchCheck size={16} />} onClick={() => void load(page, filters)}>重试</Button>} />}
    <Form form={filterForm} layout="inline" className="directory-filters" onFinish={(values) => void search(values)}>
      <Form.Item label="班级" name="classOrganizationId"><Select allowClear className="filter-select"
        options={classes.map((item) => ({ value: item.classOrganizationId, label: item.className }))} /></Form.Item>
      <Form.Item label="异常类型" name="exceptionType"><Select allowClear className="filter-select"
        options={Object.entries(typeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={[
        { value: 'SUBMITTED', label: '待处理' }, { value: 'HANDLED', label: '已处理' }
      ]} /></Form.Item>
      <Form.Item><Space><Button actionKey="exception-reports.exception-report-page.3" htmlType="submit" icon={<Search size={16} />}>查询</Button>
        <Button actionKey="exception-reports.exception-report-page.4" icon={<RotateCcw size={16} />} onClick={() => void resetFilters()}>重置</Button></Space></Form.Item>
    </Form>
    <Table rowKey="id" loading={loading} dataSource={items} pagination={{ current: page, pageSize: 20, total, onChange: (value) => void load(value, filters) }}
      scroll={{ x: 900 }} columns={[
        { title: '学生', key: 'student', render: (_, row) => <>{row.studentName}<br /><small>{row.studentAccountMasked}</small></> },
        { title: '班级', dataIndex: 'className' },
        { title: '异常类型', dataIndex: 'exceptionType', render: (value: ExceptionReportType) => typeLabels[value] },
        { title: '事实描述', dataIndex: 'content', ellipsis: true },
        { title: '报备教师', dataIndex: 'reporterName' },
        { title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'HANDLED' ? 'green' : 'gold'}>{value === 'HANDLED' ? '已处理' : '待处理'}</Tag> },
        { title: '报备时间', dataIndex: 'reportedAt', render: formatTime },
        { title: '操作', key: 'action', fixed: 'right', render: (_, row) => <Button actionKey="exception-reports.exception-report-page.5" type="text" icon={<Eye size={16} />} onClick={() => void openDetails(row.id)}>详情</Button> }
      ]} />
    <Modal actionPrefix="exception-reports.exception-report-page.modal.1" title="新增异常报备" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => form.submit()} confirmLoading={submitting} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={(values) => void create(values)}>
        <Form.Item name="classOrganizationId" label="班级" rules={[{ required: true, message: '请选择班级' }]}>
          <Select options={classes.map((item) => ({ value: item.classOrganizationId, label: item.className }))} onChange={(value) => void classChanged(value)} />
        </Form.Item>
        <Form.Item name="studentId" label="学生" rules={[{ required: true, message: '请选择学生' }]}>
          <Select options={students.map((item) => ({ value: item.studentId, label: `${item.studentName} ${item.studentAccountMasked}` }))} />
        </Form.Item>
        <Form.Item name="exceptionType" label="异常类型" rules={[{ required: true, message: '请选择异常类型' }]}>
          <Select options={Object.entries(typeLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
        <Form.Item name="content" label="事实描述" rules={[{ required: true, whitespace: true, message: '请填写事实描述' }, { max: 1000 }]}>
          <Input.TextArea rows={5} showCount maxLength={1000} />
        </Form.Item>
      </Form>
    </Modal>
    <Modal title="异常报备详情" open={Boolean(details)} onCancel={() => setDetails(undefined)} footer={details?.report.status === 'SUBMITTED' && canHandle
      ? <Space><Button actionKey="exception-reports.exception-report-page.6" onClick={() => setDetails(undefined)}>取消</Button><Button actionKey="exception-reports.exception-report-page.7" type="primary" loading={submitting} onClick={() => void handle()}>确认处理</Button></Space>
      : <Button actionKey="exception-reports.exception-report-page.8" onClick={() => setDetails(undefined)}>关闭</Button>} width={720}>
      {details && <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small" items={[
          { key: 'student', label: '学生', children: `${details.report.studentName} ${details.report.studentAccountMasked}` },
          { key: 'class', label: '班级', children: details.report.className },
          { key: 'type', label: '异常类型', children: typeLabels[details.report.exceptionType] },
          { key: 'status', label: '状态', children: details.report.status === 'HANDLED' ? '已处理' : '待处理' },
          { key: 'content', label: '事实描述', children: details.report.content, span: 2 }
        ]} />
        <List header="处理历史" dataSource={details.actions} renderItem={(item) => <List.Item>{formatTime(item.createdAt)}　{item.operatorName}　{item.actionNote || item.actionType}</List.Item>} />
        {details.report.status === 'SUBMITTED' && canHandle && <Input.TextArea value={handleNote} onChange={(e) => setHandleNote(e.target.value)} rows={4} maxLength={1000} showCount placeholder="填写处理说明" />}
      </Space>}
    </Modal>
  </div>;
}

function formatTime(value?: string): string { return value ? value.replace('T', ' ').slice(0, 16) : '-'; }
function toMessage(error: unknown): string { return error instanceof Error ? error.message : '请求未能完成'; }
