import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip
} from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { Check, Eye, EyeOff, Pencil, Plus, RefreshCw, Trash2, X } from 'lucide-react';
import { rewardApi } from './api';
import type {
  GrowthReward,
  GrowthRewardExchange,
  GrowthRewardExchangeStatus,
  GrowthRewardStatus,
  RewardStudent,
  SaveGrowthRewardInput
} from './types';

interface RewardFormValues {
  rewardName: string;
  requiredPoints: number;
  description?: string;
  expiresAt?: string;
  status: GrowthRewardStatus;
}

interface RejectFormValues {
  rejectReason: string;
}

const exchangeStatusLabels: Record<GrowthRewardExchangeStatus, string> = {
  PENDING_APPROVAL: '待审批',
  PENDING_VERIFICATION: '待核销',
  REJECTED: '已驳回',
  AUTO_REJECTED: '超时自动驳回',
  EXPIRED: '已过期',
  VERIFIED: '已核销'
};

const exchangeStatusColors: Record<GrowthRewardExchangeStatus, string> = {
  PENDING_APPROVAL: 'processing',
  PENDING_VERIFICATION: 'warning',
  REJECTED: 'error',
  AUTO_REJECTED: 'default',
  EXPIRED: 'default',
  VERIFIED: 'success'
};

export function RewardManagementPage() {
  const { message: messageApi, modal } = AntdApp.useApp();
  const [students, setStudents] = useState<RewardStudent[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<string>();
  const [rewards, setRewards] = useState<GrowthReward[]>([]);
  const [exchanges, setExchanges] = useState<GrowthRewardExchange[]>([]);
  const [activeTab, setActiveTab] = useState('rewards');
  const [exchangeStatus, setExchangeStatus] = useState<GrowthRewardExchangeStatus | 'ALL'>('ALL');
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [editingReward, setEditingReward] = useState<GrowthReward | 'NEW' | null>(null);
  const [rewardSubmitting, setRewardSubmitting] = useState(false);
  const [rejectingExchange, setRejectingExchange] = useState<GrowthRewardExchange | null>(null);
  const [exchangeSubmitting, setExchangeSubmitting] = useState(false);

  const filteredExchanges = useMemo(
    () => exchangeStatus === 'ALL'
      ? exchanges
      : exchanges.filter((exchange) => exchange.status === exchangeStatus),
    [exchangeStatus, exchanges]
  );

  useEffect(() => {
    void loadInitial();
  }, []);

  async function loadInitial(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const options = await rewardApi.listStudents();
      setStudents(options);
      if (options.length === 0) {
        setSelectedStudentId(undefined);
        setRewards([]);
        setExchanges([]);
        return;
      }
      const firstStudentId = options[0].id;
      setSelectedStudentId(firstStudentId);
      await loadStudentData(firstStudentId, false);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function loadStudentData(studentId: string, manageLoading = true): Promise<void> {
    if (manageLoading) {
      setLoading(true);
    }
    setErrorMessage(null);
    try {
      const [nextRewards, nextExchanges] = await Promise.all([
        rewardApi.listRewards(studentId),
        rewardApi.listExchanges(studentId)
      ]);
      setRewards(nextRewards);
      setExchanges(nextExchanges);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      if (manageLoading) {
        setLoading(false);
      }
    }
  }

  async function reloadRewards(): Promise<void> {
    if (!selectedStudentId) return;
    setRewards(await rewardApi.listRewards(selectedStudentId));
  }

  async function reloadExchanges(): Promise<void> {
    if (!selectedStudentId) return;
    setExchanges(await rewardApi.listExchanges(selectedStudentId));
  }

  function changeStudent(studentId: string): void {
    setSelectedStudentId(studentId);
    setRewards([]);
    setExchanges([]);
    setExchangeStatus('ALL');
    void loadStudentData(studentId);
  }

  async function saveReward(values: RewardFormValues): Promise<void> {
    if (!selectedStudentId || !editingReward) return;
    const input: SaveGrowthRewardInput = {
      rewardName: values.rewardName.trim(),
      requiredPoints: values.requiredPoints,
      description: values.description?.trim() || null,
      expiresAt: values.expiresAt || null,
      status: values.status
    };
    setRewardSubmitting(true);
    try {
      if (editingReward === 'NEW') {
        await rewardApi.createReward(selectedStudentId, input);
        messageApi.success('奖励已创建');
      } else {
        await rewardApi.updateReward(editingReward.id, input);
        messageApi.success('奖励已更新');
      }
      setEditingReward(null);
      await reloadRewards();
    } catch (error) {
      messageApi.error(toMessage(error));
    } finally {
      setRewardSubmitting(false);
    }
  }

  function toggleReward(reward: GrowthReward): void {
    const nextStatus: GrowthRewardStatus = reward.status === 'ONLINE' ? 'OFFLINE' : 'ONLINE';
    modal.confirm({
      title: nextStatus === 'ONLINE' ? '确认上架奖励' : '确认下架奖励',
      content: nextStatus === 'OFFLINE' ? '下架后学生端将不再显示此奖励。' : '上架后学生可申请兑换此奖励。',
      okText: nextStatus === 'ONLINE' ? '确认上架' : '确认下架',
      cancelText: '取消',
      async onOk() {
        await rewardApi.updateReward(reward.id, {
          rewardName: reward.rewardName,
          requiredPoints: reward.requiredPoints,
          description: reward.description,
          expiresAt: reward.expiresAt,
          status: nextStatus
        });
        messageApi.success(nextStatus === 'ONLINE' ? '奖励已上架' : '奖励已下架');
        await reloadRewards();
      }
    });
  }

  function removeReward(reward: GrowthReward): void {
    modal.confirm({
      title: '确认删除奖励',
      content: `删除后“${reward.rewardName}”将不再展示，历史兑换记录仍会保留。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      async onOk() {
        await rewardApi.deleteReward(reward.id);
        messageApi.success('奖励已删除');
        await reloadRewards();
      }
    });
  }

  function approveExchange(exchange: GrowthRewardExchange): void {
    modal.confirm({
      title: '同意奖励兑换',
      content: `确认同意“${exchange.rewardName}”，系统将扣减 ${exchange.requiredPoints} 可用积分。`,
      okText: '确认同意',
      cancelText: '取消',
      async onOk() {
        await rewardApi.approveExchange(exchange.id);
        messageApi.success('兑换已同意，等待核销');
        await reloadExchanges();
      }
    });
  }

  async function rejectExchange(values: RejectFormValues): Promise<void> {
    if (!rejectingExchange) return;
    setExchangeSubmitting(true);
    try {
      await rewardApi.rejectExchange(rejectingExchange.id, values.rejectReason.trim());
      setRejectingExchange(null);
      messageApi.success('兑换已驳回');
      await reloadExchanges();
    } catch (error) {
      messageApi.error(toMessage(error));
    } finally {
      setExchangeSubmitting(false);
    }
  }

  function verifyExchange(exchange: GrowthRewardExchange): void {
    modal.confirm({
      title: '确认奖励已兑现',
      content: `确认“${exchange.rewardName}”已实际交付给孩子。`,
      okText: '确认核销',
      cancelText: '取消',
      async onOk() {
        await rewardApi.verifyExchange(exchange.id);
        messageApi.success('奖励已核销');
        await reloadExchanges();
      }
    });
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>奖励管理</h1>
        <Space wrap>
          <Select
            aria-label="选择孩子"
            className="reward-student-select"
            value={selectedStudentId}
            placeholder="选择孩子"
            loading={loading && students.length === 0}
            disabled={loading}
            options={students.map((student) => ({ value: student.id, label: student.studentName }))}
            onChange={changeStudent}
          />
          <Tooltip title="刷新奖励数据">
            <Button
              aria-label="刷新奖励数据"
              icon={<RefreshCw size={16} />}
              loading={loading}
              disabled={!selectedStudentId}
              onClick={() => selectedStudentId && void loadStudentData(selectedStudentId)}
            />
          </Tooltip>
          <Button
            type="primary"
            icon={<Plus size={16} />}
            disabled={!selectedStudentId}
            onClick={() => setEditingReward('NEW')}
          >新建奖励</Button>
        </Space>
      </div>

      {errorMessage && (
        <Alert
          type="error"
          showIcon
          message={errorMessage}
          action={<Button size="small" onClick={() => void loadInitial()}>重试</Button>}
        />
      )}

      {students.length === 0 && !loading ? (
        <div className="reward-empty"><Empty description="暂无可管理奖励的孩子" /></div>
      ) : (
        <ProCard className="content-panel reward-workspace" bordered={false}>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'rewards',
                label: '奖励库',
                children: (
                  <Table<GrowthReward>
                    rowKey="id"
                    loading={loading}
                    dataSource={rewards}
                    locale={{ emptyText: '暂无奖励' }}
                    scroll={{ x: 900 }}
                    pagination={false}
                    columns={[
                      { title: '奖励名称', dataIndex: 'rewardName', key: 'rewardName', width: 180 },
                      {
                        title: '所需积分', dataIndex: 'requiredPoints', key: 'requiredPoints', width: 110,
                        render: (value: number) => <strong className="reward-points">{value}</strong>
                      },
                      {
                        title: '说明', dataIndex: 'description', key: 'description',
                        render: (value: string | null) => value || '-'
                      },
                      {
                        title: '有效期至', dataIndex: 'expiresAt', key: 'expiresAt', width: 170,
                        render: (value: string | null) => value ? formatDateTime(value) : '长期有效'
                      },
                      {
                        title: '状态', dataIndex: 'status', key: 'status', width: 100,
                        render: (status: GrowthRewardStatus) => (
                          <Tag color={status === 'ONLINE' ? 'success' : 'default'}>
                            {status === 'ONLINE' ? '已上架' : '已下架'}
                          </Tag>
                        )
                      },
                      {
                        title: '操作', key: 'actions', width: 150, fixed: 'right',
                        render: (_, reward) => (
                          <Space size={4}>
                            <Tooltip title="编辑奖励">
                              <Button
                                type="text"
                                aria-label={`编辑奖励 ${reward.rewardName}`}
                                icon={<Pencil size={16} />}
                                onClick={() => setEditingReward(reward)}
                              />
                            </Tooltip>
                            <Tooltip title={reward.status === 'ONLINE' ? '下架奖励' : '上架奖励'}>
                              <Button
                                type="text"
                                aria-label={`${reward.status === 'ONLINE' ? '下架' : '上架'}奖励 ${reward.rewardName}`}
                                icon={reward.status === 'ONLINE' ? <EyeOff size={16} /> : <Eye size={16} />}
                                onClick={() => toggleReward(reward)}
                              />
                            </Tooltip>
                            <Tooltip title="删除奖励">
                              <Button
                                type="text"
                                danger
                                aria-label={`删除奖励 ${reward.rewardName}`}
                                icon={<Trash2 size={16} />}
                                onClick={() => removeReward(reward)}
                              />
                            </Tooltip>
                          </Space>
                        )
                      }
                    ]}
                  />
                )
              },
              {
                key: 'exchanges',
                label: '兑换处理',
                children: (
                  <>
                    <div className="reward-filters">
                      <Select
                        aria-label="兑换状态"
                        value={exchangeStatus}
                        onChange={setExchangeStatus}
                        options={[
                          { value: 'ALL', label: '全部状态' },
                          ...Object.entries(exchangeStatusLabels).map(([value, label]) => ({ value, label }))
                        ]}
                      />
                    </div>
                    <Table<GrowthRewardExchange>
                      rowKey="id"
                      loading={loading}
                      dataSource={filteredExchanges}
                      locale={{ emptyText: '暂无兑换记录' }}
                      scroll={{ x: 1050 }}
                      pagination={{ pageSize: 20, showSizeChanger: false }}
                      columns={[
                        { title: '奖励名称', dataIndex: 'rewardName', key: 'rewardName', width: 170 },
                        {
                          title: '兑换积分', dataIndex: 'requiredPoints', key: 'requiredPoints', width: 110,
                          render: (value: number) => <strong className="reward-points">{value}</strong>
                        },
                        {
                          title: '申请时间', dataIndex: 'requestedAt', key: 'requestedAt', width: 170,
                          render: (value: string) => formatDateTime(value)
                        },
                        {
                          title: '审批截止', dataIndex: 'approvalDeadline', key: 'approvalDeadline', width: 170,
                          render: (value: string) => formatDateTime(value)
                        },
                        {
                          title: '状态', dataIndex: 'status', key: 'status', width: 130,
                          render: (status: GrowthRewardExchangeStatus) => (
                            <Tag color={exchangeStatusColors[status]}>{exchangeStatusLabels[status]}</Tag>
                          )
                        },
                        {
                          title: '驳回原因', dataIndex: 'rejectReason', key: 'rejectReason',
                          render: (value: string | null) => value || '-'
                        },
                        {
                          title: '操作', key: 'actions', width: 190, fixed: 'right',
                          render: (_, exchange) => exchange.status === 'PENDING_APPROVAL' ? (
                            <Space size={4}>
                              <Button
                                type="link"
                                size="small"
                                icon={<Check size={15} />}
                                aria-label={`同意兑换 ${exchange.rewardName}`}
                                onClick={() => approveExchange(exchange)}
                              >同意</Button>
                              <Button
                                type="link"
                                danger
                                size="small"
                                icon={<X size={15} />}
                                aria-label={`驳回兑换 ${exchange.rewardName}`}
                                onClick={() => setRejectingExchange(exchange)}
                              >驳回</Button>
                            </Space>
                          ) : exchange.status === 'PENDING_VERIFICATION' ? (
                            <Button
                              type="link"
                              size="small"
                              icon={<Check size={15} />}
                              aria-label={`确认核销 ${exchange.rewardName}`}
                              onClick={() => verifyExchange(exchange)}
                            >确认核销</Button>
                          ) : '-'
                        }
                      ]}
                    />
                  </>
                )
              }
            ]}
          />
        </ProCard>
      )}

      <Modal
        title={editingReward === 'NEW' ? '新建奖励' : '编辑奖励'}
        open={editingReward !== null}
        footer={null}
        destroyOnHidden
        maskClosable={!rewardSubmitting}
        onCancel={() => !rewardSubmitting && setEditingReward(null)}
      >
        {editingReward && (
          <Form<RewardFormValues>
            layout="vertical"
            initialValues={editingReward === 'NEW' ? { status: 'ONLINE' } : {
              rewardName: editingReward.rewardName,
              requiredPoints: editingReward.requiredPoints,
              description: editingReward.description ?? undefined,
              expiresAt: editingReward.expiresAt ?? undefined,
              status: editingReward.status
            }}
            onFinish={(values) => void saveReward(values)}
          >
            <Form.Item
              label="奖励名称"
              name="rewardName"
              rules={[
                { required: true, whitespace: true, message: '请填写奖励名称' },
                { max: 30, message: '奖励名称不能超过 30 个字符' }
              ]}
            >
              <Input maxLength={30} placeholder="例如：周末观影" />
            </Form.Item>
            <Form.Item
              label="所需积分"
              name="requiredPoints"
              rules={[{ required: true, message: '请填写所需积分' }]}
            >
              <InputNumber className="full-width" min={1} precision={0} placeholder="请输入正整数" />
            </Form.Item>
            <Form.Item
              label="奖励说明"
              name="description"
              rules={[{ max: 200, message: '奖励说明不能超过 200 个字符' }]}
            >
              <Input.TextArea rows={3} maxLength={200} showCount placeholder="说明兑换条件或兑现方式" />
            </Form.Item>
            <Form.Item label="有效期至" name="expiresAt">
              <Input type="datetime-local" />
            </Form.Item>
            <Form.Item label="状态" name="status" rules={[{ required: true }]}>
              <Select options={[
                { value: 'ONLINE', label: '上架' },
                { value: 'OFFLINE', label: '下架' }
              ]} />
            </Form.Item>
            <div className="form-actions">
              <Button disabled={rewardSubmitting} onClick={() => setEditingReward(null)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={rewardSubmitting}>保存奖励</Button>
            </div>
          </Form>
        )}
      </Modal>

      <Modal
        title="驳回奖励兑换"
        open={rejectingExchange !== null}
        footer={null}
        destroyOnHidden
        maskClosable={!exchangeSubmitting}
        onCancel={() => !exchangeSubmitting && setRejectingExchange(null)}
      >
        <Form<RejectFormValues> layout="vertical" onFinish={(values) => void rejectExchange(values)}>
          <Form.Item
            label="驳回原因"
            name="rejectReason"
            rules={[
              { required: true, whitespace: true, message: '请填写驳回原因' },
              { max: 500, message: '驳回原因不能超过 500 个字符' }
            ]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount placeholder="说明本次不能兑换的原因" />
          </Form.Item>
          <div className="form-actions">
            <Button disabled={exchangeSubmitting} onClick={() => setRejectingExchange(null)}>取消</Button>
            <Button type="primary" danger htmlType="submit" loading={exchangeSubmitting}>确认驳回</Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short'
  }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '奖励数据加载失败';
}
