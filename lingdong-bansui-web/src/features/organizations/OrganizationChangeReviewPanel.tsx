import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useState } from 'react';
import { Alert, Form, Input, Modal, Space, Tag, message } from 'antd';
import { Check, X } from 'lucide-react';
import { organizationApi, type OrganizationChange } from '../../api/organization';
import { formatDateTime } from '../../utils/datetime';

interface OrganizationChangeReviewPanelProps {
  canReview: boolean;
  refreshKey?: number;
}

interface ReviewFormValue {
  comment?: string;
}

type ReviewAction = 'APPROVE' | 'REJECT';

const changeTypeLabels: Record<OrganizationChange['changeType'], string> = {
  DISABLE: '停用', MOVE: '移动', DELETE: '删除'
};

const taskStatusLabels: Record<OrganizationChange['taskStatus'], string> = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已批准',
  REJECTED: '已驳回', EFFECTIVE: '已生效'
};

/** 系统管理员查看本人申请，系统审核员在同一清单完成审核。 */
export function OrganizationChangeReviewPanel({ canReview, refreshKey = 0 }: OrganizationChangeReviewPanelProps) {
  const [items, setItems] = useState<OrganizationChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reviewing, setReviewing] = useState<{ item: OrganizationChange; action: ReviewAction } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<ReviewFormValue>();

  useEffect(() => { void load(); }, [refreshKey]);

  async function load(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      setItems(await organizationApi.listChanges());
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  function openReview(item: OrganizationChange, action: ReviewAction): void {
    form.resetFields();
    setReviewing({ item, action });
  }

  async function submitReview(values: ReviewFormValue): Promise<void> {
    if (!reviewing) return;
    const comment = values.comment?.trim();
    setSubmitting(true);
    try {
      if (reviewing.action === 'APPROVE') {
        await organizationApi.approveChange(reviewing.item.taskId, { comment });
        message.success('组织变更已批准并执行');
      } else {
        await organizationApi.rejectChange(reviewing.item.taskId, { comment });
        message.success('组织变更已驳回');
      }
      setReviewing(null);
      await load();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  const columns = [
    {
      title: '组织快照', key: 'organization',
      render: (_: unknown, item: OrganizationChange) => (
        <div><strong>{item.organizationNameSnapshot}</strong><div className="secondary-text">{item.organizationCodeSnapshot}</div></div>
      )
    },
    { title: '变更', dataIndex: 'changeType', key: 'changeType', width: 88, render: (value: OrganizationChange['changeType']) => changeTypeLabels[value] },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '状态', dataIndex: 'taskStatus', key: 'taskStatus', width: 100,
      render: (value: OrganizationChange['taskStatus']) => <Tag color={value === 'PENDING_REVIEW' ? 'gold' : value === 'EFFECTIVE' ? 'green' : 'default'}>{taskStatusLabels[value]}</Tag>
    },
    { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 180, render: formatDateTime },
    ...(canReview ? [{
      title: '操作', key: 'actions', width: 154,
      render: (_: unknown, item: OrganizationChange) => item.taskStatus === 'PENDING_REVIEW' ? (
        <Space size={4}>
          <Button actionKey="ORG_NODE_CHANGE_REVIEW" size="small" type="primary" icon={<Check size={14} />} onClick={() => openReview(item, 'APPROVE')}>批准</Button>
          <Button actionKey="organizations.organization-change-review-panel.2" size="small" danger icon={<X size={14} />} onClick={() => openReview(item, 'REJECT')}>驳回</Button>
        </Space>
      ) : null
    }] : [])
  ];

  return (
    <section className="organization-change-panel">
      <div className="section-heading">
        <h2>{canReview ? '组织变更审核' : '我的变更申请'}</h2>
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
      <Table<OrganizationChange>
        rowKey="changeId" columns={columns} dataSource={items} loading={loading}
        pagination={{ pageSize: 10, hideOnSinglePage: true }} scroll={{ x: 760 }}
        locale={{ emptyText: canReview ? '暂无组织变更待办' : '暂无组织变更申请' }}
      />

      <Modal
        title={reviewing?.action === 'APPROVE' ? '批准组织变更' : '驳回组织变更'}
        open={reviewing !== null} footer={null} width="min(520px, 92vw)" onCancel={() => setReviewing(null)} destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={submitReview}>
          <Form.Item
            name="comment" label="审核意见"
            rules={reviewing?.action === 'REJECT'
              ? [{ required: true, whitespace: true, message: '驳回时必须填写审核意见' }, { max: 500 }]
              : [{ max: 500 }]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
          <div className="form-actions">
            <Button actionKey="organizations.organization-change-review-panel.4" onClick={() => setReviewing(null)}>取消</Button>
            <Button actionKey="organizations.organization-change-review-panel.5" type="primary" danger={reviewing?.action === 'REJECT'} htmlType="submit" loading={submitting}>
              {reviewing?.action === 'APPROVE' ? '确认批准' : '确认驳回'}
            </Button>
          </div>
        </Form>
      </Modal>
    </section>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
