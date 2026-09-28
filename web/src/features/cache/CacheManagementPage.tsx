import { Alert, Button, Checkbox, Form, Input, message, Modal, Select, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { Check, RefreshCw, Send, Trash2, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  cacheManagementApi,
  type CacheDomain,
  type CacheOperation,
  type CacheOperationStatus,
  type CacheOperationType,
  type CacheReviewQueueItem,
  type ExecuteCacheOperationInput,
  type SubmitHighRiskCacheOperationInput
} from '../../api/cache-management';

interface CacheManagementPageProps {
  canManage: boolean;
  canReview: boolean;
}

interface DirectFormValues {
  cacheDomain: CacheDomain;
  operationType: CacheOperationType;
  impactDescription: string;
}

interface HighRiskFormValues {
  cacheDomain: 'ALL' | 'USER_SESSION';
  title: string;
  description: string;
  confirmed: boolean;
}

interface ReviewFormValues {
  comment: string;
}

type ReviewAction = 'approve' | 'reject';

export function CacheManagementPage({ canManage, canReview }: CacheManagementPageProps) {
  const [operations, setOperations] = useState<CacheOperation[]>([]);
  const [reviewQueue, setReviewQueue] = useState<CacheReviewQueueItem[]>([]);
  const [activeTab, setActiveTab] = useState('history');
  const [loadingOperations, setLoadingOperations] = useState(true);
  const [loadingReviews, setLoadingReviews] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>();
  const [directModalOpen, setDirectModalOpen] = useState(false);
  const [highRiskModalOpen, setHighRiskModalOpen] = useState(false);
  const [reviewAction, setReviewAction] = useState<ReviewAction>();
  const [selectedReview, setSelectedReview] = useState<CacheReviewQueueItem>();
  const [directForm] = Form.useForm<DirectFormValues>();
  const [highRiskForm] = Form.useForm<HighRiskFormValues>();
  const [reviewForm] = Form.useForm<ReviewFormValues>();

  useEffect(() => { void loadOperations(); }, []);
  useEffect(() => {
    if (canReview && activeTab === 'reviews') void loadReviews();
  }, [activeTab, canReview]);

  async function loadOperations(): Promise<void> {
    setLoadingOperations(true);
    setErrorMessage(undefined);
    try {
      setOperations(await cacheManagementApi.listOperations());
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoadingOperations(false);
    }
  }

  async function loadReviews(): Promise<void> {
    setLoadingReviews(true);
    setErrorMessage(undefined);
    try {
      setReviewQueue(await cacheManagementApi.listReviewQueue());
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoadingReviews(false);
    }
  }

  function openDirectModal(): void {
    directForm.setFieldsValue({
      cacheDomain: 'DICTIONARY',
      operationType: 'REFRESH',
      impactDescription: ''
    });
    setDirectModalOpen(true);
  }

  function openHighRiskModal(): void {
    highRiskForm.setFieldsValue({
      cacheDomain: 'ALL',
      title: '',
      description: '',
      confirmed: false
    });
    setHighRiskModalOpen(true);
  }

  function openReviewModal(item: CacheReviewQueueItem, action: ReviewAction): void {
    setSelectedReview(item);
    setReviewAction(action);
    reviewForm.setFieldsValue({ comment: '' });
  }

  async function executeDirect(values: DirectFormValues): Promise<void> {
    await submit(async () => {
      await cacheManagementApi.executeOperation(values as ExecuteCacheOperationInput);
      setDirectModalOpen(false);
      directForm.resetFields();
      await loadOperations();
    }, '缓存操作已执行');
  }

  async function submitHighRisk(values: HighRiskFormValues): Promise<void> {
    const input: SubmitHighRiskCacheOperationInput = {
      cacheDomain: values.cacheDomain,
      operationType: 'CLEAR',
      title: values.title,
      description: values.description,
      confirmed: values.confirmed
    };
    await submit(async () => {
      await cacheManagementApi.submitHighRisk(input);
      setHighRiskModalOpen(false);
      highRiskForm.resetFields();
      await loadOperations();
    }, '高风险缓存任务已提交审核');
  }

  async function review(values: ReviewFormValues): Promise<void> {
    if (!selectedReview || !reviewAction) return;
    await submit(async () => {
      if (reviewAction === 'approve') {
        await cacheManagementApi.approve(selectedReview.taskId, values.comment);
      } else {
        await cacheManagementApi.reject(selectedReview.taskId, values.comment);
      }
      closeReviewModal();
      await Promise.all([loadReviews(), loadOperations()]);
    }, reviewAction === 'approve' ? '高风险缓存任务已批准并执行' : '高风险缓存任务已驳回');
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

  function closeReviewModal(): void {
    setSelectedReview(undefined);
    setReviewAction(undefined);
    reviewForm.resetFields();
  }

  const historyPanel = (
    <ProCard className="content-panel" bordered={false}>
      <Table<CacheOperation>
        rowKey="id"
        size="small"
        loading={loadingOperations}
        dataSource={operations}
        scroll={{ x: 1080 }}
        pagination={{ pageSize: 20, hideOnSinglePage: true }}
        locale={{ emptyText: '暂无缓存操作记录' }}
        columns={[
          { title: '缓存域', dataIndex: 'cacheDomain', key: 'cacheDomain', width: 120, render: domainLabel },
          { title: '操作', dataIndex: 'operationType', key: 'operationType', width: 90, render: operationTypeLabel },
          { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (status) => <OperationStatusTag status={status} /> },
          { title: '影响说明', dataIndex: 'impactDescription', key: 'impactDescription', ellipsis: true },
          { title: '失败原因', dataIndex: 'failureMessage', key: 'failureMessage', width: 190, ellipsis: true, render: (value) => value ?? '-' },
          { title: '申请人', dataIndex: 'requestedBy', key: 'requestedBy', width: 170 },
          { title: '执行时间', dataIndex: 'executedAt', key: 'executedAt', width: 170, render: formatTime },
          { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatTime }
        ]}
      />
    </ProCard>
  );

  const reviewPanel = (
    <ProCard className="content-panel" bordered={false}>
      <Table<CacheReviewQueueItem>
        rowKey="taskId"
        size="small"
        loading={loadingReviews}
        dataSource={reviewQueue}
        scroll={{ x: 880 }}
        pagination={false}
        locale={{ emptyText: '暂无待审核缓存任务' }}
        columns={[
          { title: '任务标题', dataIndex: 'taskTitle', key: 'taskTitle', width: 180 },
          { title: '缓存域', dataIndex: 'cacheDomain', key: 'cacheDomain', width: 120, render: domainLabel },
          { title: '影响说明', dataIndex: 'impactDescription', key: 'impactDescription', ellipsis: true },
          { title: '提交人', dataIndex: 'submittedBy', key: 'submittedBy', width: 170 },
          { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 170, render: formatTime },
          {
            title: '操作', key: 'actions', width: 112, fixed: 'right',
            render: (_, item) => (
              <Space size={4}>
                <Tooltip title="批准并执行">
                  <Button type="text" icon={<Check size={16} />} aria-label={`批准-${item.taskTitle}`} onClick={() => openReviewModal(item, 'approve')} />
                </Tooltip>
                <Tooltip title="驳回">
                  <Button danger type="text" icon={<X size={16} />} aria-label={`驳回-${item.taskTitle}`} onClick={() => openReviewModal(item, 'reject')} />
                </Tooltip>
              </Space>
            )
          }
        ]}
      />
    </ProCard>
  );

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>缓存管理</h1>
        {canManage && (
          <Space wrap>
            <Button icon={<RefreshCw size={16} />} onClick={openDirectModal}>执行缓存操作</Button>
            <Button danger icon={<Send size={16} />} onClick={openHighRiskModal}>提交高风险操作</Button>
          </Space>
        )}
      </div>
      <Alert type="info" showIcon message="当前真实操作范围：数据字典支持刷新和清除；全部缓存与用户会话仅支持审批后清除。其他缓存域尚无处理器，不提供执行入口。" />
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button size="small" onClick={() => activeTab === 'reviews' ? void loadReviews() : void loadOperations()}>重试</Button>} />}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'history', label: '操作台账', children: historyPanel },
          canReview ? { key: 'reviews', label: '待审核任务', children: reviewPanel } : null
        ].filter((item): item is NonNullable<typeof item> => item !== null)}
      />

      <Modal title="执行缓存操作" open={directModalOpen} footer={null} onCancel={() => setDirectModalOpen(false)} destroyOnHidden>
        <Form form={directForm} layout="vertical" onFinish={executeDirect}>
          <Form.Item label="缓存域" name="cacheDomain" rules={[{ required: true }]}>
            <Select options={[{ value: 'DICTIONARY', label: '数据字典' }]} />
          </Form.Item>
          <Form.Item label="操作类型" name="operationType" rules={[{ required: true }]}>
            <Select options={[{ value: 'REFRESH', label: '刷新' }, { value: 'CLEAR', label: '清除' }]} />
          </Form.Item>
          <Form.Item label="影响说明" name="impactDescription" rules={[{ required: true, message: '请输入影响说明' }, { max: 1000 }]}>
            <Input.TextArea rows={4} maxLength={1000} showCount />
          </Form.Item>
          <ModalActions submitting={submitting} submitText="确认执行" onCancel={() => setDirectModalOpen(false)} />
        </Form>
      </Modal>

      <Modal title="提交高风险操作" open={highRiskModalOpen} footer={null} onCancel={() => setHighRiskModalOpen(false)} destroyOnHidden>
        <Alert type="warning" showIcon message="该操作提交后必须由系统审核员审批，批准后立即执行。" />
        <Alert type="error" showIcon message="真实执行范围：全部已注册缓存；用户会话清除会强制退出所有活动设备会话，包括当前审核会话。" />
        <Form form={highRiskForm} layout="vertical" onFinish={submitHighRisk} className="cache-risk-form">
          <Form.Item label="缓存域" name="cacheDomain" rules={[{ required: true }]}>
            <Select options={[{ value: 'ALL', label: '全部缓存' }, { value: 'USER_SESSION', label: '用户会话' }]} />
          </Form.Item>
          <Form.Item label="任务标题" name="title" rules={[{ required: true, message: '请输入任务标题' }, { max: 100 }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item label="影响说明" name="description" rules={[{ required: true, message: '请输入影响说明' }, { max: 1000 }]}>
            <Input.TextArea rows={4} maxLength={1000} showCount />
          </Form.Item>
          <Form.Item name="confirmed" valuePropName="checked" rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error('请完成风险确认')) }]}>
            <Checkbox>我已确认该操作需要系统审核员审批</Checkbox>
          </Form.Item>
          <ModalActions submitting={submitting} submitText="提交审核" onCancel={() => setHighRiskModalOpen(false)} danger />
        </Form>
      </Modal>

      <Modal
        title={reviewAction === 'approve' ? '批准高风险任务' : '驳回高风险任务'}
        open={Boolean(reviewAction && selectedReview)}
        footer={null}
        onCancel={closeReviewModal}
        destroyOnHidden
      >
        <Form form={reviewForm} layout="vertical" onFinish={review}>
          <Form.Item
            label="审核意见"
            name="comment"
            rules={reviewAction === 'reject' ? [{ required: true, message: '驳回时必须填写审核意见' }, { max: 1000 }] : [{ max: 1000 }]}
          >
            <Input.TextArea rows={4} maxLength={1000} showCount />
          </Form.Item>
          <ModalActions
            submitting={submitting}
            submitText={reviewAction === 'approve' ? '确认批准' : '确认驳回'}
            onCancel={closeReviewModal}
            danger={reviewAction === 'reject'}
          />
        </Form>
      </Modal>
    </div>
  );
}

function ModalActions({ submitting, submitText, onCancel, danger = false }: {
  submitting: boolean;
  submitText: string;
  onCancel: () => void;
  danger?: boolean;
}) {
  return (
    <div className="form-actions">
      <Button onClick={onCancel}>取消</Button>
      <Button type="primary" danger={danger} htmlType="submit" loading={submitting} icon={danger ? <Trash2 size={16} /> : undefined}>{submitText}</Button>
    </div>
  );
}

function OperationStatusTag({ status }: { status: CacheOperationStatus }) {
  const options: Record<CacheOperationStatus, { color: string; label: string }> = {
    PENDING: { color: 'processing', label: '待处理' },
    SUCCEEDED: { color: 'success', label: '成功' },
    FAILED: { color: 'error', label: '失败' },
    REJECTED: { color: 'default', label: '已驳回' }
  };
  const option = options[status];
  return <Tag color={option.color}>{option.label}</Tag>;
}

function domainLabel(value: CacheDomain): string {
  return ({
    PERMISSION: '权限', DICTIONARY: '数据字典', ORGANIZATION: '组织', FEATURE_TOGGLE: '功能开关',
    USER_SESSION: '用户会话', BUSINESS_STATISTICS: '业务统计', ALL: '全部缓存'
  } as Record<CacheDomain, string>)[value];
}

function operationTypeLabel(value: CacheOperationType): string {
  return value === 'CLEAR' ? '清除' : '刷新';
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
