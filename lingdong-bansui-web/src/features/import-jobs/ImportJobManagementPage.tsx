import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { Alert, Descriptions, Drawer, Form, Input, message, Progress, Select, Space, Tag, Tooltip, Upload } from 'antd';
import { TableProps } from 'antd';
import { Download, Eye, FilePlus2, KeyRound, Play, RotateCcw, Search, Upload as UploadIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { importJobApi, type ImportJobDetail, type ImportJobErrorPage, type ImportJobOptions, type ImportJobQuery, type ImportJobRecord, type ImportJobStatus } from '../../api/import-jobs';
import { studentImportApi, type StudentImportRecord, type StudentImportRowPage, type StudentImportRowStatus } from '../../api/student-imports';
import { classApi, type ClassOrganization } from '../../api/classes';

const emptyErrorPage: ImportJobErrorPage = { items: [], page: 1, pageSize: 20, total: 0 };
const emptyStudentRows: StudentImportRowPage = { items: [], page: 1, pageSize: 20, total: 0 };

interface ImportJobManagementPageProps {
  canCreate: boolean;
  canExecuteStudentImport?: boolean;
  canReadStudentImport?: boolean;
  canDownloadStudentCredentials?: boolean;
}

export function ImportJobManagementPage({
  canCreate,
  canExecuteStudentImport = false,
  canReadStudentImport = false,
  canDownloadStudentCredentials = false
}: ImportJobManagementPageProps) {
  const [options, setOptions] = useState<ImportJobOptions>({ templates: [], organizations: [] });
  const [page, setPage] = useState({ items: [] as ImportJobRecord[], page: 1, pageSize: 20, total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [file, setFile] = useState<File>();
  const [detail, setDetail] = useState<ImportJobDetail>();
  const [detailErrors, setDetailErrors] = useState<ImportJobErrorPage>(emptyErrorPage);
  const [creating, setCreating] = useState(false);
  const [studentExecution, setStudentExecution] = useState<StudentImportRecord>();
  const [studentRows, setStudentRows] = useState<StudentImportRowPage>(emptyStudentRows);
  const [executeOpen, setExecuteOpen] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [classes, setClasses] = useState<ClassOrganization[]>([]);
  const [filterForm] = Form.useForm<ImportJobQuery>();
  const [createForm] = Form.useForm<{ templateId: string; organizationId?: string }>();
  const [executeForm] = Form.useForm<{ classOrganizationId?: string }>();

  useEffect(() => { void loadInitial(); }, []);

  async function loadInitial() {
    setLoading(true); setError(undefined);
    try {
      const [loadedOptions, loadedPage] = await Promise.all([importJobApi.options(), importJobApi.list({})]);
      setOptions(loadedOptions); setPage(loadedPage);
    } catch (cause) { setError(toMessage(cause)); } finally { setLoading(false); }
  }

  async function search(values: ImportJobQuery) {
    await loadPage({ ...values, page: 1, pageSize: 20 });
  }

  async function loadPage(query: ImportJobQuery) {
    setLoading(true);
    setError(undefined);
    try { setPage(await importJobApi.list(query)); }
    catch (cause) { setError(toMessage(cause)); } finally { setLoading(false); }
  }

  async function create(values: { templateId: string; organizationId?: string }) {
    if (!file) { message.error('请选择 XLSX 文件'); return; }
    setCreating(true);
    try {
      await importJobApi.create({ ...values, file });
      message.success('导入校验作业已进入队列'); setCreateOpen(false); setFile(undefined);
      createForm.resetFields(); await search(filterForm.getFieldsValue());
    } catch (cause) { message.error(toMessage(cause)); } finally { setCreating(false); }
  }

  async function openDetail(id: string) {
    try {
      const [loadedDetail, loadedErrors, executions] = await Promise.all([
        importJobApi.detail(id), importJobApi.errors(id, 1, 20),
        canReadStudentImport ? studentImportApi.list({ page: 1, pageSize: 100 }) : Promise.resolve(undefined)
      ]);
      setDetail(loadedDetail);
      setDetailErrors(loadedErrors);
      const execution = executions?.items.find(item => item.validationJobId === id);
      setStudentExecution(execution);
      if (execution) setStudentRows(await studentImportApi.rows(execution.id, undefined, 1, 20));
    } catch (cause) { message.error(toMessage(cause)); }
  }

  async function loadDetailErrors(pageNumber: number, pageSize: number) {
    if (!detail) return;
    try { setDetailErrors(await importJobApi.errors(detail.job.id, pageNumber, pageSize)); }
    catch (cause) { message.error(toMessage(cause)); }
  }

  function closeDetail() {
    setDetail(undefined);
    setDetailErrors(emptyErrorPage);
    setStudentExecution(undefined);
    setStudentRows(emptyStudentRows);
  }

  async function openStudentExecution() {
    try { setClasses((await classApi.listClasses()).filter(item => item.status === 'ENABLED' && item.effectiveStatus === 'ENABLED')); setExecuteOpen(true); }
    catch (cause) { message.error(toMessage(cause)); }
  }

  async function executeStudentImport(values: { classOrganizationId?: string }) {
    if (!detail) return;
    setExecuting(true);
    try {
      const execution = await studentImportApi.create({ validationJobId: detail.job.id, classOrganizationId: values.classOrganizationId });
      setStudentExecution(execution); setStudentRows(emptyStudentRows); setExecuteOpen(false); executeForm.resetFields();
      message.success('学员导入执行已进入队列');
    } catch (cause) { message.error(toMessage(cause)); } finally { setExecuting(false); }
  }

  async function loadStudentRows(pageNumber: number, pageSize: number, status?: StudentImportRowStatus) {
    if (!studentExecution) return;
    try { setStudentRows(await studentImportApi.rows(studentExecution.id, status, pageNumber, pageSize)); }
    catch (cause) { message.error(toMessage(cause)); }
  }

  async function retryStudentFailures() {
    if (!studentExecution) return;
    try { setStudentExecution(await studentImportApi.retryFailures(studentExecution.id)); message.success('失败行已重新排队'); }
    catch (cause) { message.error(toMessage(cause)); }
  }

  function confirmCredentialDownload() {
    if (!studentExecution) return;
    Modal.confirm({ actionPrefix: 'import-jobs.import-job-management-page.confirm.1', title: '确认下载初始凭证', content: '凭证只能下载一次，下载开始后不能再次获取。请确认当前设备和网络安全。', okText: '确认下载', cancelText: '取消',
      onOk: async () => {
        try {
          const blob = await studentImportApi.downloadCredentials(studentExecution.id);
          const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
          anchor.href = url; anchor.download = `student-credentials-${studentExecution.executionCode}.xlsx`; anchor.click(); URL.revokeObjectURL(url);
          setStudentExecution({ ...studentExecution, credentialStatus: 'CONSUMED', credentialDownloadedAt: new Date().toISOString() });
        } catch (cause) { message.error(toMessage(cause)); }
      } });
  }

  async function downloadError(job: ImportJobRecord) {
    try {
      const blob = await importJobApi.downloadError(job.id);
      const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `import-errors-${job.jobCode}.xlsx`; anchor.click(); URL.revokeObjectURL(url);
    } catch (cause) { message.error(toMessage(cause)); }
  }

  const columns: NonNullable<TableProps<ImportJobRecord>['columns']> = [
    { title: '作业编码', dataIndex: 'jobCode', width: 220 },
    { title: '模板', dataIndex: 'templateName', width: 180 },
    { title: '状态', dataIndex: 'status', width: 120, render: (status: ImportJobStatus) => <Tag color={statusColor(status)}>{statusText(status)}</Tag> },
    { title: '进度', width: 150, render: (_: unknown, job) => <Progress size="small" percent={job.totalRows ? Math.round(job.processedRows * 100 / job.totalRows) : 0} /> },
    { title: '有效/无效', width: 110, render: (_: unknown, job) => `${job.validRows}/${job.invalidRows}` },
    { title: '排队时间', dataIndex: 'queuedAt', width: 170, render: formatTime },
    { title: '操作', fixed: 'right', width: 90, render: (_: unknown, job) => <Space size={2}>
      <Tooltip title="查看详情"><Button actionKey="import-jobs.import-job-management-page.1" type="text" aria-label={`详情-${job.jobCode}`} icon={<Eye size={16} />} onClick={() => void openDetail(job.id)} /></Tooltip>
      <Tooltip title="下载错误文件"><Button actionKey="import-jobs.import-job-management-page.2" type="text" aria-label={`下载错误-${job.jobCode}`} icon={<Download size={16} />} disabled={!job.errorFileId} onClick={() => void downloadError(job)} /></Tooltip>
    </Space> }
  ];

  return <div className="page-stack import-job-page">
    <div className="page-heading"><h1>导入校验作业</h1>{canCreate ? <Button actionKey="import-jobs.import-job-management-page.3" type="primary" icon={<FilePlus2 size={16} />} onClick={() => setCreateOpen(true)}>新建校验作业</Button> : null}</div>
    {error ? <Alert type="error" showIcon message={error} action={<Button actionKey="import-jobs.import-job-management-page.4" size="small" onClick={() => void loadInitial()}>重试</Button>} /> : null}
    <section className="template-filter-panel" aria-label="作业筛选"><Form form={filterForm} layout="inline" className="directory-filters" onFinish={search}>
      <Form.Item label="作业编码" name="jobCode"><Input allowClear /></Form.Item>
      <Form.Item label="模板" name="templateId"><Select allowClear className="filter-select" options={options.templates.map(option)} /></Form.Item>
      <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={['QUEUED','VALIDATING','VALIDATED','VALIDATION_FAILED','SYSTEM_FAILED'].map(value => ({ value, label: statusText(value as ImportJobStatus) }))} /></Form.Item>
      <Form.Item><Space><Button actionKey="import-jobs.import-job-management-page.5" htmlType="submit" icon={<Search size={16} />}>查询</Button><Button actionKey="import-jobs.import-job-management-page.6" icon={<RotateCcw size={16} />} onClick={() => { filterForm.resetFields(); void search({}); }}>重置</Button></Space></Form.Item>
    </Form></section>
    <section className="template-table-panel" aria-label="导入校验作业台账"><Table rowKey="id" size="small" loading={loading} dataSource={page.items} columns={columns} scroll={{ x: 1040 }} pagination={{ current: page.page, pageSize: page.pageSize, total: page.total, showSizeChanger: true }} onChange={(pagination) => void loadPage({ ...filterForm.getFieldsValue(), page: pagination.current ?? 1, pageSize: pagination.pageSize ?? 20 })} /></section>
    <Modal actionPrefix="import-jobs.import-job-management-page.modal.1" open={createOpen} title="新建校验作业" className="import-job-create-modal" okText="提交校验" confirmLoading={creating} onOk={() => createForm.submit()} onCancel={() => { setCreateOpen(false); setFile(undefined); createForm.resetFields(); }} destroyOnHidden>
      <Form form={createForm} layout="vertical" onFinish={create} preserve={false}>
        <Form.Item label="导入模板" name="templateId" rules={[{ required: true, message: '请选择导入模板' }]}><Select options={options.templates.map(option)} /></Form.Item>
        <Form.Item label="组织范围" name="organizationId"><Select allowClear options={options.organizations.map(option)} /></Form.Item>
        <Form.Item label="XLSX 文件" required><Upload maxCount={1} accept=".xlsx" beforeUpload={selected => { setFile(selected); return false; }} onRemove={() => { setFile(undefined); return true; }}><Button actionKey="import-jobs.import-job-management-page.7" icon={<UploadIcon size={16} />}>选择文件</Button></Upload></Form.Item>
      </Form>
    </Modal>
    <Drawer open={Boolean(detail)} title="作业详情" width={680} onClose={closeDetail} destroyOnHidden>
      {detail ? <div className="import-job-detail"><Descriptions size="small" column={1} bordered items={[
        { key: 'code', label: '作业编码', children: detail.job.jobCode }, { key: 'template', label: '模板', children: `${detail.job.templateName}（${detail.job.templateVersion}）` },
        { key: 'status', label: '状态', children: statusText(detail.job.status) }, { key: 'count', label: '校验结果', children: `总计 ${detail.job.totalRows}，有效 ${detail.job.validRows}，无效 ${detail.job.invalidRows}` },
        { key: 'failure', label: '失败摘要', children: detail.job.failureMessage ?? '-' }
      ]} />
      <section aria-label="字段快照"><h2>字段快照</h2><Table size="small" rowKey="fieldCode" pagination={false} scroll={{ x: 480 }} dataSource={detail.fields} columns={[{ title: '表头', dataIndex: 'columnName' }, { title: '字段编码', dataIndex: 'fieldCode' }, { title: '类型', dataIndex: 'dataType' }]} /></section>
      <section aria-label="错误行"><h2>错误行</h2><Table size="small" rowKey="id" dataSource={detailErrors.items} columns={[
        { title: '行号', dataIndex: 'rowNumber', width: 90, render: (value: number) => `第 ${value} 行` },
        { title: '错误摘要', dataIndex: 'errorSummary' },
        { title: '记录时间', dataIndex: 'createdAt', width: 170, render: formatTime }
      ]} pagination={{ current: detailErrors.page, pageSize: detailErrors.pageSize, total: detailErrors.total, hideOnSinglePage: true }} onChange={(pagination) => void loadDetailErrors(pagination.current ?? 1, pagination.pageSize ?? 20)} /></section>
      <section aria-label="学员导入执行"><div className="page-heading"><h2>学员导入执行</h2>{!studentExecution && detail.job.status === 'VALIDATED' && detail.job.organizationId && canExecuteStudentImport
        ? <Button actionKey="import-jobs.import-job-management-page.8" type="primary" icon={<Play size={16} />} onClick={() => void openStudentExecution()}>执行学员导入</Button> : null}</div>
        {studentExecution ? <><Descriptions size="small" column={1} bordered items={[
          { key: 'executionCode', label: '执行编码', children: studentExecution.executionCode },
          { key: 'executionStatus', label: '执行状态', children: studentImportStatusText(studentExecution.status) },
          { key: 'executionCount', label: '处理结果', children: `总计 ${studentExecution.totalRows}，成功 ${studentExecution.succeededRows}，失败 ${studentExecution.failedRows}` },
          { key: 'credentialStatus', label: '凭证状态', children: credentialStatusText(studentExecution.credentialStatus) }
        ]} /><Space className="student-import-actions">
          {studentExecution.failedRows > 0 && canExecuteStudentImport ? <Button actionKey="import-jobs.import-job-management-page.9" onClick={() => void retryStudentFailures()}>重试失败行</Button> : null}
          {studentExecution.credentialStatus === 'AVAILABLE' && canDownloadStudentCredentials ? <Button actionKey="import-jobs.import-job-management-page.10" icon={<KeyRound size={16} />} onClick={confirmCredentialDownload}>下载初始凭证</Button> : null}
        </Space><Table size="small" rowKey="id" dataSource={studentRows.items} scroll={{ x: 560 }} columns={[
          { title: '源行', dataIndex: 'rowNumber', width: 80 }, { title: '状态', dataIndex: 'status', width: 100, render: (value: StudentImportRowStatus) => studentRowStatusText(value) },
          { title: '学员账号', dataIndex: 'studentAccount', width: 120, render: (value: string | null) => value ?? '-' }, { title: '失败摘要', dataIndex: 'failureMessage', render: (value: string | null) => value ?? '-' }
        ]} pagination={{ current: studentRows.page, pageSize: studentRows.pageSize, total: studentRows.total, hideOnSinglePage: true }} onChange={(pagination) => void loadStudentRows(pagination.current ?? 1, pagination.pageSize ?? 20)} /></> : <div className="empty-inline">暂无业务执行</div>}
      </section>
      </div> : null}
    </Drawer>
    <Modal actionPrefix="import-jobs.import-job-management-page.modal.2" open={executeOpen} title="执行学员导入" okText="确认执行" confirmLoading={executing} onOk={() => executeForm.submit()} onCancel={() => { setExecuteOpen(false); executeForm.resetFields(); }} destroyOnHidden>
      <Form form={executeForm} layout="vertical" onFinish={executeStudentImport} preserve={false}>
        <Form.Item label="目标班级（可选）" name="classOrganizationId"><Select allowClear showSearch optionFilterProp="label" options={classes.map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
      </Form>
    </Modal>
  </div>;
}

const option = (item: { id: string; label: string }) => ({ value: item.id, label: item.label });
const statusText = (status: ImportJobStatus) => ({ QUEUED: '排队中', VALIDATING: '校验中', VALIDATED: '校验通过', VALIDATION_FAILED: '校验未通过', SYSTEM_FAILED: '系统失败' }[status]);
const statusColor = (status: ImportJobStatus) => status === 'VALIDATED' ? 'green' : status === 'VALIDATION_FAILED' || status === 'SYSTEM_FAILED' ? 'red' : status === 'VALIDATING' ? 'blue' : 'default';
const studentImportStatusText = (status: StudentImportRecord['status']) => ({ QUEUED: '排队中', RUNNING: '执行中', SUCCEEDED: '全部成功', PARTIAL_SUCCEEDED: '部分成功', FAILED: '执行失败' }[status]);
const credentialStatusText = (status: StudentImportRecord['credentialStatus']) => ({ NONE: '尚未生成', AVAILABLE: '可下载一次', CONSUMED: '已下载', EXPIRED: '已过期' }[status]);
const studentRowStatusText = (status: StudentImportRowStatus) => ({ PENDING: '待处理', SUCCEEDED: '成功', FAILED: '失败' }[status]);
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const toMessage = (error: unknown) => error instanceof Error ? error.message : '请求未能完成';
