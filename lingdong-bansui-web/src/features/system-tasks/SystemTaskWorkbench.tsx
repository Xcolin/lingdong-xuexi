import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Descriptions, Form, Modal, Select, Tag } from 'antd';
import { authApi, type CurrentUser } from '../../api/auth';
import { capabilityApi, type ClientCapabilities } from '../../api/capability';
import { systemTasksApi, type SystemTask, type SystemTaskStatus } from '../../api/system-tasks';
import { formatDateTime } from '../../utils/datetime';

const statuses: Record<SystemTaskStatus, string> = { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已批准', REJECTED: '已驳回', EFFECTIVE: '已生效', VOIDED: '已作废' };
const types: Record<string,string> = {ORGANIZATION_DISABLE:'组织停用',ORGANIZATION_MOVE:'组织移动',ORGANIZATION_DELETE:'组织删除',CACHE_CLEAR:'缓存清除',INTERFACE_SERVICE_CHANGE:'接口服务变更',SENSITIVE_DATA_EXPORT:'敏感数据导出',GLOBAL_FEATURE_TOGGLE:'全局功能开关'};
const scopes: Record<string,string> = {GLOBAL:'全局',REGION:'区域',SCHOOL:'学校',ORGANIZATION:'组织',USER:'用户'};
export function canAccessSystemTasks(user: CurrentUser, capabilities: ClientCapabilities): boolean {
  return user.clientType === 'WEB' && capabilities.client === 'WEB'
    && user.permissionCodes.includes('SYSTEM_TASK_READ');
}
export function systemTaskDestination(task: SystemTask, user: CurrentUser, caps: ClientCapabilities): string | null {
  if (!canAccessSystemTasks(user, caps) || task.submittedBy === user.userId || task.status !== 'PENDING_REVIEW') return null;
  const has = (...codes: string[]) => codes.every(code => user.permissionCodes.includes(code));
  if (task.type === 'GLOBAL_FEATURE_TOGGLE' && has('FEATURE_TOGGLE_READ', 'FEATURE_TOGGLE_REVIEW')) return '/feature-management';
  if (['ORGANIZATION_DISABLE', 'ORGANIZATION_MOVE', 'ORGANIZATION_DELETE'].includes(task.type) && caps.organizationManagementEnabled && has('ORG_NODE_CHANGE_REVIEW')) return '/organizations';
  if (task.type === 'CACHE_CLEAR' && caps.cacheManagementEnabled && has('CACHE_READ', 'CACHE_REVIEW')) return '/cache-management';
  if (task.type === 'INTERFACE_SERVICE_CHANGE' && caps.interfaceServiceManagementEnabled && has('INTERFACE_SERVICE_READ', 'INTERFACE_SERVICE_REVIEW')) return '/interface-services';
  if (task.type === 'SENSITIVE_DATA_EXPORT' && caps.dataExportEnabled && caps.attachmentServiceEnabled && caps.importExportTemplateManagementEnabled && has('EXPORT_SENSITIVE_REVIEW')) return '/export-jobs';
  return null;
}
export function SystemTaskWorkbench({ currentUser, onNavigate, onAccessChange }: { currentUser: CurrentUser; onNavigate: (path: string) => void; onAccessChange?: (user:CurrentUser,caps:ClientCapabilities)=>void }) {
  const [items, setItems] = useState<SystemTask[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<SystemTaskStatus>();
  const [detail, setDetail] = useState<SystemTask | null>(null);
  const [access, setAccess] = useState<{ user: CurrentUser; caps: ClientCapabilities } | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const generation = useRef(0);
  const clear = useCallback(() => { setItems([]); setTotal(0); setDetail(null); setAccess(null); }, []);
  const validate = useCallback(async () => {
    const [user, caps] = await Promise.all([authApi.currentUser(), capabilityApi.web()]);
    if (user.userId !== currentUser.userId || !canAccessSystemTasks(user, caps)) throw new Error('当前会话无系统任务读取权限');
    return { user, caps };
  }, [currentUser.userId]);
  const load = useCallback(async () => {
    const request = ++generation.current;
    clear(); setLoading(true); setError('');
    try {
      const fresh = await validate();
      if (request !== generation.current) return;
      const result = await systemTasksApi.list({ page, pageSize: 20, status });
      if (request !== generation.current) return;
      onAccessChange?.(fresh.user,fresh.caps); setAccess(fresh); setItems(result.items); setTotal(result.total);
    } catch (cause) {
      if (request === generation.current) { clear(); setError(cause instanceof Error ? cause.message : '系统任务查询失败'); }
    } finally { if (request === generation.current) setLoading(false); }
  }, [clear, validate, page, status, onAccessChange]);
  useEffect(() => {
    void load();
    const focus = () => { void load(); };
    window.addEventListener('focus', focus);
    return () => { ++generation.current; window.removeEventListener('focus', focus); };
  }, [load]);
  async function openDetail(id: string) {
    const request = ++generation.current;
    setDetail(null); setAccess(null); setLoading(true); setError('');
    try {
      const fresh = await validate();
      if (request !== generation.current) return;
      const result = await systemTasksApi.detail(id);
      if (request === generation.current) { onAccessChange?.(fresh.user,fresh.caps); setAccess(fresh); setDetail(result); }
    } catch (cause) {
      if (request === generation.current) { clear(); setError(cause instanceof Error ? cause.message : '系统任务详情查询失败'); }
    } finally { if (request === generation.current) setLoading(false); }
  }
  const destination = detail && access ? systemTaskDestination(detail, access.user, access.caps) : null;
  return <div className="page-stack">
    <div className="page-heading"><h1>系统任务工作台</h1></div>
    <Form layout="inline" className="directory-filters"><Form.Item label="任务状态"><Select aria-label="任务状态" allowClear placeholder="全部状态" value={status} options={Object.entries(statuses).map(([value, label]) => ({ value, label }))} onChange={value => { setPage(1); setStatus(value); }} /></Form.Item></Form>
    {error && <Alert type="error" message={error} action={<Button actionKey="system-tasks.system-task-workbench.2" aria-label="重试" onClick={() => void load()}>重试</Button>} />}
    <Table<SystemTask> rowKey="id" loading={loading} dataSource={items} locale={{emptyText:loading?'正在查询系统任务':error?'系统任务未能加载':'暂无可见系统任务'}} scroll={{ x: 900 }} pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, showTotal: count => `共 ${count} 条`, onChange: setPage }} columns={[
      { title: '任务编号', dataIndex: 'code' }, { title: '任务标题', dataIndex: 'title' }, { title: '类型', dataIndex: 'type',render:value=>types[value]||value },
      { title: '影响范围', dataIndex: 'impactScope',render:value=>scopes[value]||value }, { title: '状态', dataIndex: 'status', render: (value: SystemTaskStatus) => <Tag>{statuses[value]}</Tag> },
      { title: '申请人 ID', dataIndex: 'submittedBy' }, { title: '提交时间', dataIndex: 'submittedAt', render: (value: string) => formatDateTime(value) },
      { title: '操作', render: (_, row) => <Button actionKey="system-tasks.system-task-workbench.3" disabled={loading} onClick={() => void openDetail(row.id)}>查看详情</Button> }
    ]} />
    {detail && <Modal title="系统任务详情" open onCancel={() => setDetail(null)} footer={<Button actionKey="system-tasks.system-task-workbench.4" onClick={() => setDetail(null)}>关闭</Button>} width="min(1200px, 92vw)" styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }}>
      {detail && <><Descriptions column={{ xs: 1, sm: 2 }} items={[
        ['任务 ID', detail.id], ['编号', detail.code], ['标题', detail.title], ['类型', types[detail.type]||detail.type], ['状态', statuses[detail.status]],
        ['申请说明', detail.description], ['影响范围', scopes[detail.impactScope||'']||detail.impactScope], ['申请人 ID', detail.submittedBy], ['提交时间', formatDateTime(detail.submittedAt)],
        ['审核人 ID', detail.reviewedBy], ['审核时间', formatDateTime(detail.reviewedAt)], ['审核意见', detail.reviewComment], ['创建时间', formatDateTime(detail.createdAt)], ['更新时间', formatDateTime(detail.updatedAt)]
      ].map(([label, value]) => ({ key: label!, label, children: value || '—' }))} />
        {detail.payload && <>
          <Descriptions title="业务载荷" column={{ xs: 1, sm: 2 }} items={detail.payload.fields.map(field => ({ key: field.label, label: field.label, children: field.value ?? '未记录' }))} />
          {detail.payload.notice && <Alert type="info" message={detail.payload.notice} />}
          {detail.payload.differences.length > 0 && <Table rowKey="label" size="small" pagination={false} dataSource={detail.payload.differences} columns={[
            { title: '变更字段', dataIndex: 'label' },
            { title: '申请前', dataIndex: 'before', render: value => value ?? '未记录' },
            { title: '申请目标', dataIndex: 'after', render: value => value ?? '未设置' }
          ]} />}
          {detail.payload.executionStatus && <Descriptions column={1} items={[{ key: 'execution', label: '执行状态', children: ({ PENDING: '待执行', APPLIED: '已执行', SUCCEEDED: '已成功', FAILED: '执行失败', REJECTED: '已驳回' } as Record<string,string>)[detail.payload.executionStatus] ?? detail.payload.executionStatus }]} />}
          {detail.payload.failureReason && <Alert type="error" message={detail.payload.failureReason} />}
        </>}
        {destination ? <Button actionKey="system-tasks.system-task-workbench.5" type="primary" onClick={() => onNavigate(destination)}>前往领域处理页</Button> : <Alert type="info" message="该任务当前不可在此页执行；无可用的领域处理入口。" />}
      </>}
    </Modal>}
  </div>;
}
