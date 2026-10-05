import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { Alert, Form, Input, message, Modal, Select, Space, Tabs, Tag, Tooltip } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { Check, Pencil, Plus, Power, PowerOff, Search, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { formatDateTime as formatTime } from '../../utils/datetime';
import {
  interfaceServiceManagementApi,
  type InterfaceAuthorizationScope,
  type InterfaceCallResult,
  type InterfacePurpose,
  type InterfaceServiceCallLog,
  type InterfaceServiceChangeRecord,
  type InterfaceServiceChangeType,
  type InterfaceServiceQuery,
  type InterfaceServiceRecord,
  type InterfaceServiceStatus,
  type RegisterInterfaceServiceInput,
  type SystemTaskStatus
} from '../../api/interface-services';

interface InterfaceServiceManagementPageProps {
  canManage: boolean;
  canReview: boolean;
}

interface RegistrationFormValues extends RegisterInterfaceServiceInput { }
interface StatusFormValues { title: string; description: string }
interface AuthorizationFormValues {
  authorizationScope: InterfaceAuthorizationScope;
  authorizationScopeValue?: string;
  title: string;
  description: string;
}
interface ReviewFormValues { comment: string }
type ReviewAction = 'approve' | 'reject';

export function InterfaceServiceManagementPage({ canManage, canReview }: InterfaceServiceManagementPageProps) {
  const [services, setServices] = useState<InterfaceServiceRecord[]>([]);
  const [changes, setChanges] = useState<InterfaceServiceChangeRecord[]>([]);
  const [reviews, setReviews] = useState<InterfaceServiceChangeRecord[]>([]);
  const [callLogs, setCallLogs] = useState<InterfaceServiceCallLog[]>([]);
  const [activeTab, setActiveTab] = useState('services');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>();
  const [registrationOpen, setRegistrationOpen] = useState(false);
  const [statusTarget, setStatusTarget] = useState<InterfaceServiceRecord>();
  const [authorizationTarget, setAuthorizationTarget] = useState<InterfaceServiceRecord>();
  const [reviewTarget, setReviewTarget] = useState<InterfaceServiceChangeRecord>();
  const [reviewAction, setReviewAction] = useState<ReviewAction>();
  const [serviceQuery, setServiceQuery] = useState<InterfaceServiceQuery>({});
  const [filterForm] = Form.useForm<InterfaceServiceQuery>();
  const [registrationForm] = Form.useForm<RegistrationFormValues>();
  const [statusForm] = Form.useForm<StatusFormValues>();
  const [authorizationForm] = Form.useForm<AuthorizationFormValues>();
  const [reviewForm] = Form.useForm<ReviewFormValues>();

  useEffect(() => { void loadServices(serviceQuery); }, [serviceQuery]);
  useEffect(() => {
    if (activeTab === 'changes') void loadChanges();
    if (activeTab === 'reviews' && canReview) void loadReviews();
    if (activeTab === 'calls') void loadCallLogs();
  }, [activeTab, canReview]);
  useEffect(() => {
    if (!registrationOpen) return;
    registrationForm.setFieldsValue({
      serviceName: '', direction: 'OUTBOUND', purpose: 'DATA_SYNC', callerName: '',
      authorizationScope: 'GLOBAL', authorizationScopeValue: undefined,
      ownerId: '', title: '', description: ''
    });
  }, [registrationOpen, registrationForm]);
  useEffect(() => {
    if (!statusTarget) return;
    const action = statusTarget.status === 'ENABLED' ? '停用' : '启用';
    statusForm.setFieldsValue({ title: `${action}${statusTarget.serviceName}`, description: '' });
  }, [statusTarget, statusForm]);
  useEffect(() => {
    if (!authorizationTarget) return;
    authorizationForm.setFieldsValue({
      authorizationScope: authorizationTarget.authorizationScope,
      authorizationScopeValue: authorizationTarget.authorizationScopeValue ?? undefined,
      title: `调整${authorizationTarget.serviceName}授权范围`,
      description: ''
    });
  }, [authorizationTarget, authorizationForm]);
  useEffect(() => {
    if (reviewTarget && reviewAction) reviewForm.setFieldsValue({ comment: '' });
  }, [reviewTarget, reviewAction, reviewForm]);

  async function loadServices(query: InterfaceServiceQuery): Promise<void> {
    await load(() => interfaceServiceManagementApi.listServices(query), setServices);
  }

  async function loadChanges(): Promise<void> {
    await load(interfaceServiceManagementApi.listChanges, setChanges);
  }

  async function loadReviews(): Promise<void> {
    await load(interfaceServiceManagementApi.listReviewQueue, setReviews);
  }

  async function loadCallLogs(): Promise<void> {
    await load(() => interfaceServiceManagementApi.listCallLogs(), setCallLogs);
  }

  async function reloadActiveTab(): Promise<void> {
    if (activeTab === 'services') await loadServices(serviceQuery);
    if (activeTab === 'changes') await loadChanges();
    if (activeTab === 'reviews' && canReview) await loadReviews();
    if (activeTab === 'calls') await loadCallLogs();
  }

  async function load<T>(request: () => Promise<T[]>, setter: (value: T[]) => void): Promise<void> {
    setLoading(true);
    setErrorMessage(undefined);
    try {
      setter(await request());
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  function openRegistration(): void {
    setRegistrationOpen(true);
  }

  function openStatus(service: InterfaceServiceRecord): void {
    setStatusTarget(service);
  }

  function openAuthorization(service: InterfaceServiceRecord): void {
    setAuthorizationTarget(service);
  }

  function openReview(item: InterfaceServiceChangeRecord, action: ReviewAction): void {
    setReviewTarget(item);
    setReviewAction(action);
  }

  async function submitRegistration(values: RegistrationFormValues): Promise<void> {
    const input = {
      ...values,
      authorizationScopeValue: values.authorizationScope === 'GLOBAL'
        ? undefined
        : values.authorizationScopeValue?.trim()
    };
    await submit(async () => {
      await interfaceServiceManagementApi.submitRegistration(input);
      setRegistrationOpen(false);
      registrationForm.resetFields();
      await loadServices(serviceQuery);
    }, '接口服务登记任务已提交审核');
  }

  async function submitStatus(values: StatusFormValues): Promise<void> {
    if (!statusTarget) return;
    await submit(async () => {
      if (statusTarget.status === 'ENABLED') {
        await interfaceServiceManagementApi.submitDisable(statusTarget.id, values.title, values.description);
      } else {
        await interfaceServiceManagementApi.submitEnable(statusTarget.id, values.title, values.description);
      }
      setStatusTarget(undefined);
      statusForm.resetFields();
      await loadServices(serviceQuery);
    }, '接口服务启停任务已提交审核');
  }

  async function submitAuthorization(values: AuthorizationFormValues): Promise<void> {
    if (!authorizationTarget) return;
    await submit(async () => {
      await interfaceServiceManagementApi.submitAuthorization(authorizationTarget.id, {
        ...values,
        authorizationScopeValue: values.authorizationScope === 'GLOBAL'
          ? undefined
          : values.authorizationScopeValue?.trim()
      });
      setAuthorizationTarget(undefined);
      authorizationForm.resetFields();
      await loadServices(serviceQuery);
    }, '授权范围变更任务已提交审核');
  }

  async function submitReview(values: ReviewFormValues): Promise<void> {
    if (!reviewTarget || !reviewAction) return;
    await submit(async () => {
      if (reviewAction === 'approve') {
        await interfaceServiceManagementApi.approve(reviewTarget.taskId, values.comment);
      } else {
        await interfaceServiceManagementApi.reject(reviewTarget.taskId, values.comment);
      }
      closeReview();
      await Promise.all([loadReviews(), loadChanges(), loadServices(serviceQuery)]);
    }, reviewAction === 'approve' ? '接口服务变更已批准并生效' : '接口服务变更已驳回');
  }

  async function submit(action: () => Promise<void>, successText: string): Promise<void> {
    setSubmitting(true);
    try {
      await action();
      message.success(successText);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  function closeReview(): void {
    setReviewTarget(undefined);
    setReviewAction(undefined);
    reviewForm.resetFields();
  }

  const servicePanel = (
    <div className="page-stack compact-stack">
      <ProCard className="content-panel" bordered={false}>
        <Form name="interface-service-filter" form={filterForm} layout="inline" onFinish={(values) => setServiceQuery(cleanQuery(values))}>
          <Form.Item label="服务名称" name="serviceName"><Input allowClear /></Form.Item>
          <Form.Item label="调用方" name="callerName"><Input allowClear /></Form.Item>
          <Form.Item label="状态" name="status">
            <Select allowClear options={statusOptions} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item><Button actionKey="interface-services.interface-service-management-page.1" htmlType="submit" icon={<Search size={16} />}>查询</Button></Form.Item>
        </Form>
      </ProCard>
      <ProCard className="content-panel" bordered={false}>
        <Table<InterfaceServiceRecord>
          rowKey="id" size="small" loading={loading} dataSource={services}
          scroll={{ x: 1260 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
          locale={{ emptyText: '暂无已生效接口服务' }}
          columns={[
            { title: '服务名称', dataIndex: 'serviceName', key: 'serviceName', width: 170 },
            { title: '方向', dataIndex: 'direction', key: 'direction', width: 140, render: directionLabel },
            { title: '用途', dataIndex: 'purpose', key: 'purpose', width: 110, render: purposeLabel },
            { title: '调用方', dataIndex: 'callerName', key: 'callerName', width: 180 },
            { title: '授权范围', key: 'scope', width: 190, render: (_: unknown, item: InterfaceServiceRecord) => scopeLabel(item.authorizationScope, item.authorizationScopeValue) },
            { title: '责任人', dataIndex: 'ownerId', key: 'ownerId', width: 170 },
            { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (value: InterfaceServiceStatus) => <ServiceStatusTag status={value} /> },
            { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170, render: formatTime },
            canManage ? {
              title: '操作', key: 'actions', width: 112, fixed: 'right' as const,
              render: (_: unknown, item: InterfaceServiceRecord) => (
                <Space size={4}>
                  <Tooltip title="调整授权范围">
                    <Button actionKey="interface-services.interface-service-management-page.2" type="text" icon={<Pencil size={16} />} aria-label={`调整授权-${item.serviceName}`} onClick={() => openAuthorization(item)} />
                  </Tooltip>
                  <Tooltip title={item.status === 'ENABLED' ? '提交停用审核' : '提交启用审核'}>
                    <Button actionKey="interface-services.interface-service-management-page.3"
                      type="text" danger={item.status === 'ENABLED'}
                      icon={item.status === 'ENABLED' ? <PowerOff size={16} /> : <Power size={16} />}
                      aria-label={`${item.status === 'ENABLED' ? '停用' : '启用'}-${item.serviceName}`}
                      onClick={() => openStatus(item)}
                    />
                  </Tooltip>
                </Space>
              )
            } : null
          ].filter(Boolean) as never}
        />
      </ProCard>
    </div>
  );

  const changesPanel = <ChangeTable items={changes} loading={loading} />;
  const reviewPanel = (
    <ProCard className="content-panel" bordered={false}>
      <Table<InterfaceServiceChangeRecord>
        rowKey="taskId" size="small" loading={loading} dataSource={reviews}
        scroll={{ x: 1050 }} pagination={false} locale={{ emptyText: '暂无待审核接口服务变更' }}
        columns={[
          { title: '任务标题', dataIndex: 'taskTitle', key: 'taskTitle', width: 210 },
          { title: '变更类型', dataIndex: 'changeType', key: 'changeType', width: 130, render: changeTypeLabel },
          { title: '服务', dataIndex: 'serviceName', key: 'serviceName', width: 170, render: (value) => value ?? '-' },
          { title: '任务说明', dataIndex: 'taskDescription', key: 'taskDescription', ellipsis: true },
          { title: '提交人', dataIndex: 'submittedBy', key: 'submittedBy', width: 170 },
          { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 170, render: formatTime },
          {
            title: '操作', key: 'actions', width: 112, fixed: 'right',
            render: (_, item) => (
              <Space size={4}>
                <Tooltip title="批准并执行">
                  <Button actionKey="INTERFACE_SERVICE_REVIEW" type="text" icon={<Check size={16} />} aria-label={`批准-${item.taskTitle}`} onClick={() => openReview(item, 'approve')} />
                </Tooltip>
                <Tooltip title="驳回">
                  <Button actionKey="interface-services.interface-service-management-page.5" danger type="text" icon={<X size={16} />} aria-label={`驳回-${item.taskTitle}`} onClick={() => openReview(item, 'reject')} />
                </Tooltip>
              </Space>
            )
          }
        ]}
      />
    </ProCard>
  );
  const callsPanel = (
    <ProCard className="content-panel" bordered={false}>
      <Table<InterfaceServiceCallLog>
        rowKey="id" size="small" loading={loading} dataSource={callLogs}
        scroll={{ x: 1100 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
        locale={{ emptyText: '暂无接口调用结果' }}
        columns={[
          { title: '服务', dataIndex: 'serviceName', key: 'serviceName', width: 180 },
          { title: '调用方', dataIndex: 'callerName', key: 'callerName', width: 180 },
          { title: '结果', dataIndex: 'result', key: 'result', width: 90, render: (value: InterfaceCallResult) => <Tag color={value === 'SUCCEEDED' ? 'success' : 'error'}>{value === 'SUCCEEDED' ? '成功' : '失败'}</Tag> },
          { title: '异常摘要', dataIndex: 'errorSummary', key: 'errorSummary', ellipsis: true, render: (value) => value ?? '-' },
          { title: '追踪标识', dataIndex: 'traceId', key: 'traceId', width: 180, render: (value) => value ?? '-' },
          { title: '发生时间', dataIndex: 'occurredAt', key: 'occurredAt', width: 170, render: formatTime }
        ]}
      />
    </ProCard>
  );

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>接口服务管理</h1>
        {canManage && <Button actionKey="INTERFACE_SERVICE_MANAGE" type="primary" icon={<Plus size={16} />} onClick={openRegistration}>登记接口服务</Button>}
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="interface-services.interface-service-management-page.7" size="small" onClick={() => void reloadActiveTab()}>重试</Button>} />}
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        { key: 'services', label: '服务登记', children: servicePanel },
        { key: 'changes', label: '变更记录', children: changesPanel },
        canReview ? { key: 'reviews', label: '变更审核', children: reviewPanel } : null,
        { key: 'calls', label: '调用台账', children: callsPanel }
      ].filter((item): item is NonNullable<typeof item> => item !== null)} />

      <Modal title="登记接口服务" open={registrationOpen} footer={null} onCancel={() => setRegistrationOpen(false)} destroyOnHidden width="min(880px, 92vw)" styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }}>
        <Alert type="warning" showIcon message="登记提交后必须由系统审核员审批，审批通过后服务才会生效。" />
        <Form name="interface-service-registration" form={registrationForm} layout="vertical" onFinish={submitRegistration} className="cache-risk-form">
          <div className="responsive-form-grid">
            <Form.Item label="服务名称" name="serviceName" rules={[required('请输入服务名称'), { max: 100 }]}><Input maxLength={100} /></Form.Item>
            <Form.Item label="调用方名称" name="callerName" rules={[required('请输入调用方名称'), { max: 100 }]}><Input maxLength={100} /></Form.Item>
            <Form.Item label="接口方向" name="direction" rules={[{ required: true }]}><Select options={directionOptions} /></Form.Item>
            <Form.Item label="接口用途" name="purpose" rules={[{ required: true }]}><Select options={purposeOptions} /></Form.Item>
            <Form.Item label="授权范围" name="authorizationScope" rules={[{ required: true }]}><Select options={scopeOptions} /></Form.Item>
            <Form.Item noStyle shouldUpdate={(previous, current) => previous.authorizationScope !== current.authorizationScope}>
              {({ getFieldValue }) => getFieldValue('authorizationScope') === 'GLOBAL' ? <div /> : (
                <Form.Item label="授权范围值" name="authorizationScopeValue" rules={[required('请输入授权范围值'), { max: 128 }]}><Input maxLength={128} /></Form.Item>
              )}
            </Form.Item>
            <Form.Item label="责任人标识" name="ownerId" rules={[required('请输入责任人标识'), { pattern: /^\d{19}$/, message: '责任人标识必须为19位数字' }]}><Input maxLength={19} /></Form.Item>
            <Form.Item label="任务标题" name="title" rules={[required('请输入任务标题'), { max: 100 }]}><Input maxLength={100} /></Form.Item>
          </div>
          <Form.Item label="任务说明" name="description" rules={[required('请输入任务说明'), { max: 1000 }]}><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item>
          <ModalActions submitting={submitting} submitText="提交审核" onCancel={() => setRegistrationOpen(false)} />
        </Form>
      </Modal>

      <Modal title={statusTarget?.status === 'ENABLED' ? '提交停用审核' : '提交启用审核'} open={Boolean(statusTarget)} footer={null} onCancel={() => setStatusTarget(undefined)} destroyOnHidden width="min(880px, 92vw)">
        <Form name="interface-service-status" form={statusForm} layout="vertical" onFinish={submitStatus}>
          <Form.Item label="任务标题" name="title" rules={[required('请输入任务标题'), { max: 100 }]}><Input maxLength={100} /></Form.Item>
          <Form.Item label="任务说明" name="description" rules={[required('请输入任务说明'), { max: 1000 }]}><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item>
          <ModalActions submitting={submitting} submitText="提交审核" onCancel={() => setStatusTarget(undefined)} danger={statusTarget?.status === 'ENABLED'} />
        </Form>
      </Modal>

      <Modal title="调整授权范围" open={Boolean(authorizationTarget)} footer={null} onCancel={() => setAuthorizationTarget(undefined)} destroyOnHidden width="min(880px, 92vw)">
        <Form name="interface-service-authorization" form={authorizationForm} layout="vertical" onFinish={submitAuthorization}>
          <Form.Item label="授权范围" name="authorizationScope" rules={[{ required: true }]}><Select options={scopeOptions} /></Form.Item>
          <Form.Item noStyle shouldUpdate={(previous, current) => previous.authorizationScope !== current.authorizationScope}>
            {({ getFieldValue }) => getFieldValue('authorizationScope') === 'GLOBAL' ? <div /> : (
              <Form.Item label="授权范围值" name="authorizationScopeValue" rules={[required('请输入授权范围值'), { max: 128 }]}><Input maxLength={128} /></Form.Item>
            )}
          </Form.Item>
          <Form.Item label="任务标题" name="title" rules={[required('请输入任务标题'), { max: 100 }]}><Input maxLength={100} /></Form.Item>
          <Form.Item label="任务说明" name="description" rules={[required('请输入任务说明'), { max: 1000 }]}><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item>
          <ModalActions submitting={submitting} submitText="提交审核" onCancel={() => setAuthorizationTarget(undefined)} />
        </Form>
      </Modal>

      <Modal title={reviewAction === 'approve' ? '批准接口服务变更' : '驳回接口服务变更'} open={Boolean(reviewTarget && reviewAction)} footer={null} onCancel={closeReview} destroyOnHidden width="min(520px, 92vw)">
        <Form name="interface-service-review" form={reviewForm} layout="vertical" onFinish={submitReview}>
          <Form.Item label="审核意见" name="comment" rules={reviewAction === 'reject' ? [required('驳回时必须填写审核意见'), { max: 500 }] : [{ max: 500 }]}>
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
          <ModalActions submitting={submitting} submitText={reviewAction === 'approve' ? '确认批准' : '确认驳回'} onCancel={closeReview} danger={reviewAction === 'reject'} />
        </Form>
      </Modal>
    </div>
  );
}

function ChangeTable({ items, loading }: { items: InterfaceServiceChangeRecord[]; loading: boolean }) {
  return (
    <ProCard className="content-panel" bordered={false}>
      <Table<InterfaceServiceChangeRecord>
        rowKey="changeId" size="small" loading={loading} dataSource={items}
        scroll={{ x: 1180 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
        locale={{ emptyText: '暂无接口服务变更记录' }}
        columns={[
          { title: '任务标题', dataIndex: 'taskTitle', key: 'taskTitle', width: 210 },
          { title: '变更类型', dataIndex: 'changeType', key: 'changeType', width: 130, render: changeTypeLabel },
          { title: '服务', dataIndex: 'serviceName', key: 'serviceName', width: 170, render: (value) => value ?? '-' },
          { title: '任务状态', dataIndex: 'taskStatus', key: 'taskStatus', width: 110, render: (value) => <TaskStatusTag status={value} /> },
          { title: '执行状态', dataIndex: 'executionStatus', key: 'executionStatus', width: 110, render: executionStatusTag },
          { title: '失败原因', dataIndex: 'failureReason', key: 'failureReason', width: 200, ellipsis: true, render: (value) => value ?? '-' },
          { title: '任务说明', dataIndex: 'taskDescription', key: 'taskDescription', ellipsis: true },
          { title: '审核意见', dataIndex: 'reviewComment', key: 'reviewComment', width: 180, ellipsis: true, render: (value) => value ?? '-' },
          { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 170, render: formatTime },
          { title: '审核时间', dataIndex: 'reviewedAt', key: 'reviewedAt', width: 170, render: formatTime }
        ]}
      />
    </ProCard>
  );
}

function ModalActions({ submitting, submitText, onCancel, danger = false }: { submitting: boolean; submitText: string; onCancel: () => void; danger?: boolean }) {
  return <div className="form-actions"><Button actionKey="interface-services.interface-service-management-page.8" onClick={onCancel}>取消</Button><Button actionKey="interface-services.interface-service-management-page.9" type="primary" danger={danger} htmlType="submit" loading={submitting}>{submitText}</Button></div>;
}

function ServiceStatusTag({ status }: { status: InterfaceServiceStatus }) {
  return <Tag color={status === 'ENABLED' ? 'success' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>;
}

function TaskStatusTag({ status }: { status: SystemTaskStatus }) {
  const labels: Record<SystemTaskStatus, string> = { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', REJECTED: '已驳回', EFFECTIVE: '已生效', VOIDED: '已作废' };
  const colors: Record<SystemTaskStatus, string> = { DRAFT: 'default', PENDING_REVIEW: 'processing', APPROVED: 'warning', REJECTED: 'error', EFFECTIVE: 'success', VOIDED: 'default' };
  return <Tag color={colors[status]}>{labels[status]}</Tag>;
}

function executionStatusTag(status: InterfaceServiceChangeRecord['executionStatus']) {
  const labels = { PENDING: '待执行', APPLIED: '已执行', FAILED: '执行失败' };
  const colors = { PENDING: 'default', APPLIED: 'success', FAILED: 'error' };
  return <Tag color={colors[status]}>{labels[status]}</Tag>;
}

const directionOptions = [{ value: 'OUTBOUND', label: '本系统调用第三方' }, { value: 'INBOUND', label: '第三方调用本系统' }];
const purposeOptions = [
  { value: 'WECHAT', label: '微信' }, { value: 'MAP', label: '地图' }, { value: 'SMS', label: '短信' },
  { value: 'SCHOOL', label: '学校系统' }, { value: 'DATA_SYNC', label: '数据同步' }, { value: 'OTHER', label: '其他' }
];
const scopeOptions = [
  { value: 'GLOBAL', label: '全局' }, { value: 'REGION', label: '区域' }, { value: 'SCHOOL', label: '学校' },
  { value: 'INSTITUTION', label: '机构' }, { value: 'SPECIFIED_CALLER', label: '指定调用方' }
];
const statusOptions = [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }];

function directionLabel(value: string): string { return value === 'INBOUND' ? '第三方调用本系统' : '本系统调用第三方'; }
function purposeLabel(value: InterfacePurpose): string { return ({ WECHAT: '微信', MAP: '地图', SMS: '短信', SCHOOL: '学校系统', DATA_SYNC: '数据同步', OTHER: '其他' } as Record<InterfacePurpose, string>)[value]; }
function changeTypeLabel(value: InterfaceServiceChangeType): string { return ({ CREATE: '登记', ENABLE: '启用', DISABLE: '停用', CHANGE_AUTHORIZATION: '调整授权' } as Record<InterfaceServiceChangeType, string>)[value]; }
function scopeLabel(scope: InterfaceAuthorizationScope, value?: string | null): string { const label = ({ GLOBAL: '全局', REGION: '区域', SCHOOL: '学校', INSTITUTION: '机构', SPECIFIED_CALLER: '指定调用方' } as Record<InterfaceAuthorizationScope, string>)[scope]; return value ? `${label}：${value}` : label; }
function toMessage(error: unknown): string { return error instanceof Error ? error.message : '请求未能完成'; }
function required(message: string) { return { required: true, message }; }
function cleanQuery(values: InterfaceServiceQuery): InterfaceServiceQuery { return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined && value !== '')) as InterfaceServiceQuery; }
