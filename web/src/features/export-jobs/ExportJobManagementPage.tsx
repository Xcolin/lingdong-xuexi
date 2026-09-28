import { Alert, Button, Descriptions, Drawer, Form, Progress, Select, Space, Table, Tabs, Tag, Tooltip, message } from 'antd';
import type { TableProps } from 'antd';
import { Download, Eye, Plus, RefreshCw, RotateCcw, Search } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import {
  exportJobApi,
  type ExportJobDetail,
  type ExportJobPage,
  type ExportJobQuery,
  type ExportJobRecord,
  type ExportJobStatus,
  type ExportJobType
} from '../../api/export-jobs';
import { CreateExportJobModal } from './CreateExportJobModal';
import { ExportJobReviewPanel } from './ExportJobReviewPanel';

interface ExportJobManagementPageProps {
  accessRevision?: string;
  canRead: boolean;
  canCreateOrdinary: boolean;
  canSubmitSensitive: boolean;
  canExportDictionary?: boolean;
  canExportTemplate?: boolean;
  canExportInterface?: boolean;
  canExportCache?: boolean;
  canExportSystemTasks?: boolean;
  canExportRewards?: boolean;
  canExportExceptions?: boolean;
  canExportAttachments?: boolean;
  canReview: boolean;
}

const emptyPage: ExportJobPage = { items: [], page: 1, pageSize: 20, total: 0 };

export function ExportJobManagementPage(props: ExportJobManagementPageProps) {
  const [page, setPage] = useState(emptyPage);
  const [loading, setLoading] = useState(props.canRead);
  const [error, setError] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<ExportJobDetail>();
  const [filterForm] = Form.useForm<ExportJobQuery>();
  const contentVersion = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    contentVersion.current++;
    setDetail(current => current && ['SYSTEM_TASK_LEDGER', 'REWARD_EXCHANGE_LEDGER', 'EXCEPTION_REPORT_LEDGER', 'ATTACHMENT_LEDGER'].includes(current.job.exportType) ? undefined : current);
  }, [props.accessRevision]);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; contentVersion.current++; };
  }, []);

  useEffect(() => {
    contentVersion.current++;
    if (!props.canExportSystemTasks) setDetail(current => current?.job.exportType === 'SYSTEM_TASK_LEDGER' ? undefined : current);
    if (!props.canExportAttachments) setDetail(current => current?.job.exportType === 'ATTACHMENT_LEDGER' ? undefined : current);
    if (!props.canExportExceptions) setDetail(current => current?.job.exportType === 'EXCEPTION_REPORT_LEDGER' ? undefined : current);
    if (!props.canExportRewards) setDetail(current => current?.job.exportType === 'REWARD_EXCHANGE_LEDGER' ? undefined : current);
    if (!props.canExportCache) setDetail(current => current?.job.exportType === 'CACHE_OPERATION_LOG' ? undefined : current);
    if (!props.canRead || !props.canExportInterface) {
      setDetail(current => !props.canRead || current?.job.exportType === 'INTERFACE_SERVICE_LEDGER' ? undefined : current);
    }
    if (!props.canCreateOrdinary && !props.canSubmitSensitive && !props.canExportDictionary && !props.canExportTemplate && !props.canExportInterface && !props.canExportCache && !props.canExportSystemTasks && !props.canExportRewards && !props.canExportExceptions && !props.canExportAttachments) {
      setCreateOpen(false);
    }
  }, [props.canRead, props.canExportInterface, props.canCreateOrdinary, props.canSubmitSensitive, props.canExportDictionary, props.canExportTemplate, props.canExportCache, props.canExportSystemTasks, props.canExportRewards, props.canExportExceptions, props.canExportAttachments]);

  useEffect(() => {
    if (props.canRead) void loadPage({ page: 1, pageSize: 20 });
  }, [props.canRead]);

  useEffect(() => {
    if (!props.canRead || !page.items.some(item => ['QUEUED', 'EXPORTING'].includes(item.status))) return;
    const timer = window.setInterval(() => void loadPage({
      ...filterForm.getFieldsValue(), page: page.page, pageSize: page.pageSize
    }, false), 5000);
    return () => window.clearInterval(timer);
  }, [props.canRead, page.items, page.page, page.pageSize, filterForm]);

  async function loadPage(query: ExportJobQuery, manageLoading = true) {
    if (manageLoading) setLoading(true);
    setError(undefined);
    try { setPage(await exportJobApi.list(query)); }
    catch (cause) { setError(toMessage(cause)); }
    finally { if (manageLoading) setLoading(false); }
  }

  async function openDetail(id: string) {
    const version = contentVersion.current;
    try {
      const loaded = await exportJobApi.detail(id);
      if (mounted.current && version === contentVersion.current) setDetail(loaded);
    }
    catch (cause) { message.error(toMessage(cause)); }
  }

  async function download(job: ExportJobRecord) {
    const version = contentVersion.current;
    try {
      const blob = await exportJobApi.download(job.id);
      if (!mounted.current || version !== contentVersion.current) return;
      if (typeof URL.createObjectURL !== 'function') return;
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `${job.jobCode}.xlsx`; anchor.click();
      URL.revokeObjectURL(url);
    } catch (cause) { message.error(toMessage(cause)); }
  }

  function created() {
    setCreateOpen(false);
    void loadPage({ ...filterForm.getFieldsValue(), page: 1, pageSize: page.pageSize });
  }

  const ownJobs = <OwnExportJobs
    page={page} loading={loading} error={error} filterForm={filterForm}
    canExportDictionary={props.canExportDictionary}
    canExportTemplate={props.canExportTemplate}
    canExportInterface={props.canExportInterface}
    canExportCache={props.canExportCache}
    canExportSystemTasks={props.canExportSystemTasks}
    canExportRewards={props.canExportRewards}
    canExportAttachments={props.canExportAttachments}
    canExportExceptions={props.canExportExceptions}
    onLoad={loadPage} onDetail={openDetail} onDownload={download}
  />;
  const tabs = [
    ...(props.canRead ? [{ key: 'mine', label: '我的导出', children: ownJobs }] : []),
    ...(props.canReview ? [{ key: 'review', label: '敏感导出审核', children: <ExportJobReviewPanel /> }] : [])
  ];

  return <div className="page-stack export-job-page">
    <div className="page-heading">
      <h1>数据导出中心</h1>
      {props.canCreateOrdinary || props.canSubmitSensitive || props.canExportDictionary || props.canExportTemplate || props.canExportInterface || props.canExportCache || props.canExportSystemTasks || props.canExportRewards || props.canExportExceptions || props.canExportAttachments ? <Button type="primary" icon={<Plus size={16} />} onClick={() => setCreateOpen(true)}>新建导出</Button> : null}
    </div>
    {tabs.length > 1 ? <Tabs className="export-job-tabs" items={tabs} /> : tabs[0]?.children}
    <CreateExportJobModal
      accessRevision={props.accessRevision}
      open={createOpen}
      canCreateOrdinary={props.canCreateOrdinary}
      canSubmitSensitive={props.canSubmitSensitive}
      canExportDictionary={props.canExportDictionary}
    canExportTemplate={props.canExportTemplate}
    canExportInterface={props.canExportInterface}
    canExportCache={props.canExportCache}
    canExportSystemTasks={props.canExportSystemTasks}
    canExportRewards={props.canExportRewards}
    canExportAttachments={props.canExportAttachments}
    canExportExceptions={props.canExportExceptions}
      onCancel={() => setCreateOpen(false)}
      onCreated={created}
    />
    <ExportDetailDrawer detail={!props.canRead || (detail?.job.exportType === 'INTERFACE_SERVICE_LEDGER' && !props.canExportInterface) || (detail?.job.exportType === 'CACHE_OPERATION_LOG' && !props.canExportCache) || (detail?.job.exportType === 'SYSTEM_TASK_LEDGER' && !props.canExportSystemTasks) || (detail?.job.exportType === 'REWARD_EXCHANGE_LEDGER' && !props.canExportRewards) || (detail?.job.exportType === 'ATTACHMENT_LEDGER' && !props.canExportAttachments) || (detail?.job.exportType === 'EXCEPTION_REPORT_LEDGER' && !props.canExportExceptions) ? undefined : detail} onClose={() => setDetail(undefined)} />
  </div>;
}

interface OwnExportJobsProps {
  canExportDictionary?: boolean;
  canExportTemplate?: boolean;
  canExportInterface?: boolean;
  canExportCache?: boolean;
  canExportSystemTasks?: boolean;
  canExportRewards?: boolean;
  canExportExceptions?: boolean;
  canExportAttachments?: boolean;
  page: ExportJobPage;
  loading: boolean;
  error?: string;
  filterForm: ReturnType<typeof Form.useForm<ExportJobQuery>>[0];
  onLoad: (query: ExportJobQuery) => Promise<void>;
  onDetail: (id: string) => Promise<void>;
  onDownload: (job: ExportJobRecord) => Promise<void>;
}

function OwnExportJobs(props: OwnExportJobsProps) {
  const columns: NonNullable<TableProps<ExportJobRecord>['columns']> = [
    { title: '作业编码', dataIndex: 'jobCode', width: 220 },
    { title: '数据集', dataIndex: 'exportType', width: 150, render: exportTypeText },
    { title: '状态', dataIndex: 'status', width: 110, render: (status: ExportJobStatus) => <Tag color={statusColor(status)}>{statusText(status)}</Tag> },
    { title: '进度', width: 170, render: (_: unknown, job) => <Progress
      size="small" percent={progress(job)} format={() => `${job.processedRows} / ${job.totalRows}`}
      status={job.status === 'FAILED' ? 'exception' : job.status === 'SUCCEEDED' ? 'success' : 'active'}
    /> },
    { title: '申请原因', dataIndex: 'requestReason', width: 220, ellipsis: true },
    { title: '申请时间', dataIndex: 'requestedAt', width: 170, render: formatTime },
    { title: '操作', fixed: 'right', width: 96, render: (_: unknown, job) => (job.exportType === 'DICTIONARY_LEDGER' && !props.canExportDictionary) || (job.exportType === 'TEMPLATE_LEDGER' && !props.canExportTemplate) || (job.exportType === 'INTERFACE_SERVICE_LEDGER' && !props.canExportInterface) || (job.exportType === 'CACHE_OPERATION_LOG' && !props.canExportCache) || (job.exportType === 'SYSTEM_TASK_LEDGER' && !props.canExportSystemTasks) || (job.exportType === 'REWARD_EXCHANGE_LEDGER' && !props.canExportRewards) || (job.exportType === 'ATTACHMENT_LEDGER' && !props.canExportAttachments) || (job.exportType === 'EXCEPTION_REPORT_LEDGER' && !props.canExportExceptions) ? '-' : <Space size={2}>
      <Tooltip title="查看详情"><Button type="text" aria-label={`详情-${job.jobCode}`} icon={<Eye size={16} />} onClick={() => void props.onDetail(job.id)} /></Tooltip>
      <Tooltip title={job.status === 'SUCCEEDED' ? '下载结果' : '作业成功后可下载'}>
        <Button type="text" aria-label={`下载-${job.jobCode}`} icon={<Download size={16} />} disabled={job.status !== 'SUCCEEDED'} onClick={() => void props.onDownload(job)} />
      </Tooltip>
    </Space> }
  ];

  return <>
    <section className="export-filter-panel" aria-label="导出作业筛选">
      <Form form={props.filterForm} layout="inline" className="directory-filters" onFinish={(values) => void props.onLoad({ ...values, page: 1, pageSize: props.page.pageSize })}>
        <Form.Item label="数据集" name="exportType"><Select allowClear className="filter-select" options={exportTypeOptions} /></Form.Item>
        <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={statusOptions} /></Form.Item>
        <Form.Item><Space>
          <Button htmlType="submit" icon={<Search size={16} />}>查询</Button>
          <Button icon={<RotateCcw size={16} />} onClick={() => { props.filterForm.resetFields(); void props.onLoad({ page: 1, pageSize: props.page.pageSize }); }}>重置</Button>
          <Tooltip title="刷新作业"><Button aria-label="刷新作业" icon={<RefreshCw size={16} />} onClick={() => void props.onLoad({ ...props.filterForm.getFieldsValue(), page: props.page.page, pageSize: props.page.pageSize })} /></Tooltip>
        </Space></Form.Item>
      </Form>
    </section>
    {props.error ? <Alert type="error" showIcon message={props.error} /> : null}
    <section className="export-table-panel" aria-label="本人导出作业">
      <Table rowKey="id" size="small" loading={props.loading} dataSource={props.page.items} columns={columns} scroll={{ x: 1130 }}
        pagination={{ current: props.page.page, pageSize: props.page.pageSize, total: props.page.total, showSizeChanger: true }}
        onChange={(pagination) => void props.onLoad({ ...props.filterForm.getFieldsValue(), page: pagination.current ?? 1, pageSize: pagination.pageSize ?? 20 })}
      />
    </section>
  </>;
}

function ExportDetailDrawer({ detail, onClose }: { detail?: ExportJobDetail; onClose: () => void }) {
  return <Drawer open={Boolean(detail)} title="导出作业详情" width={680} onClose={onClose} destroyOnHidden>
    {detail ? <div className="export-job-detail">
      <Descriptions size="small" bordered column={1} items={[
        { key: 'code', label: '作业编码', children: detail.job.jobCode },
        { key: 'type', label: '数据集', children: exportTypeText(detail.job.exportType) },
        { key: 'status', label: '状态', children: statusText(detail.job.status) },
        { key: 'scope', label: '数据范围', children: detail.scopeSummary },
        { key: 'template', label: '模板', children: `${detail.job.templateName}（${detail.job.templateVersion}）` },
        { key: 'columns', label: '导出列', children: detail.columns.map(column => column.header).join('、') },
        { key: 'failure', label: '失败摘要', children: detail.job.failureMessage ?? '-' }
      ]} />
      <section aria-label="作业事件"><h2>处理记录</h2><Table size="small" rowKey="id" pagination={false} dataSource={detail.events} columns={[
        { title: '时间', dataIndex: 'occurredAt', width: 170, render: formatTime },
        { title: '事件', dataIndex: 'summary' }
      ]} /></section>
    </div> : null}
  </Drawer>;
}

const exportTypeOptions = [
  { value: 'GROWTH_POINT_LEDGER', label: '积分台账' },
  { value: 'IAM_CHANGE_AUDIT', label: '权限变更审计' },
  { value: 'DICTIONARY_LEDGER', label: '数据字典台账' },
  { value: 'TEMPLATE_LEDGER', label: '导入导出模板台账' },
  { value: 'INTERFACE_SERVICE_LEDGER', label: '接口服务台账' },
  { value: 'CACHE_OPERATION_LOG', label: '缓存操作日志' },
  { value: 'SYSTEM_TASK_LEDGER', label: '系统任务审批台账' },
  { value: 'REWARD_EXCHANGE_LEDGER', label: '奖励兑换报表' },
  { value: 'ATTACHMENT_LEDGER', label: '附件管理台账' },
  { value: 'EXCEPTION_REPORT_LEDGER', label: '异常报备台账' }
];
const exportTypeText = (type: ExportJobType) => ({ GROWTH_POINT_LEDGER: '积分台账', IAM_CHANGE_AUDIT: '权限变更审计', DICTIONARY_LEDGER: '数据字典台账', TEMPLATE_LEDGER: '导入导出模板台账', INTERFACE_SERVICE_LEDGER: '接口服务台账', CACHE_OPERATION_LOG: '缓存操作日志', SYSTEM_TASK_LEDGER: '系统任务审批台账', REWARD_EXCHANGE_LEDGER: '奖励兑换报表', ATTACHMENT_LEDGER: '附件管理台账', EXCEPTION_REPORT_LEDGER: '异常报备台账' })[type];
const statusText = (status: ExportJobStatus) => ({
  PENDING_REVIEW: '待审核', QUEUED: '排队中', EXPORTING: '导出中',
  SUCCEEDED: '已完成', FAILED: '失败', REJECTED: '已驳回'
}[status]);
const statusOptions = ['PENDING_REVIEW', 'QUEUED', 'EXPORTING', 'SUCCEEDED', 'FAILED', 'REJECTED']
  .map(value => ({ value, label: statusText(value as ExportJobStatus) }));
const statusColor = (status: ExportJobStatus) => status === 'SUCCEEDED' ? 'green'
  : status === 'FAILED' || status === 'REJECTED' ? 'red'
    : status === 'EXPORTING' ? 'blue' : status === 'PENDING_REVIEW' ? 'gold' : 'default';
const progress = (job: ExportJobRecord) => job.totalRows > 0
  ? Math.min(100, Math.round(job.processedRows * 100 / job.totalRows))
  : job.status === 'SUCCEEDED' ? 100 : 0;
const formatTime = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
const toMessage = (error: unknown) => error instanceof Error ? error.message : '请求未能完成';
