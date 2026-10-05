import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { Alert, Form, Input, Space, Tag, Tooltip, message } from 'antd';
import { Check, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { exportJobApi, type ExportJobReview, type ExportJobReviewPage } from '../../api/export-jobs';
import { formatDateTime as formatTime } from '../../utils/datetime';

const emptyPage: ExportJobReviewPage = { items: [], page: 1, pageSize: 20, total: 0 };

export function ExportJobReviewPanel() {
  const [page, setPage] = useState(emptyPage);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [decision, setDecision] = useState<{ action: 'approve' | 'reject'; item: ExportJobReview }>();
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<{ comment: string }>();

  useEffect(() => { void loadPage(1, 20); }, []);

  async function loadPage(pageNumber: number, pageSize: number) {
    setLoading(true); setError(undefined);
    try { setPage(await exportJobApi.listReviews(pageNumber, pageSize)); }
    catch (cause) { setError(toMessage(cause)); }
    finally { setLoading(false); }
  }

  async function submit(values: { comment: string }) {
    if (!decision) return;
    setSubmitting(true);
    try {
      const comment = values.comment?.trim() ?? '';
      if (decision.action === 'approve') await exportJobApi.approve(decision.item.systemTaskId, comment);
      else await exportJobApi.reject(decision.item.systemTaskId, comment);
      message.success(decision.action === 'approve' ? '敏感导出已批准' : '敏感导出已驳回');
      setDecision(undefined); form.resetFields();
      await loadPage(page.page, page.pageSize);
    } catch (cause) { message.error(toMessage(cause)); }
    finally { setSubmitting(false); }
  }

  function openDecision(action: 'approve' | 'reject', item: ExportJobReview) {
    form.resetFields();
    setDecision({ action, item });
  }

  return <section className="export-table-panel" aria-label="敏感导出审核">
    <div className="export-panel-toolbar">
      <span>仅展示等待当前系统审核员处理的申请</span>
    </div>
    {error ? <Alert type="error" showIcon message={error} /> : null}
    <Table<ExportJobReview>
      rowKey="systemTaskId" size="small" loading={loading} dataSource={page.items} scroll={{ x: 920 }}
      pagination={{ current: page.page, pageSize: page.pageSize, total: page.total, showSizeChanger: true }}
      onChange={(pagination) => void loadPage(pagination.current ?? 1, pagination.pageSize ?? 20)}
      columns={[
        { title: '申请人', dataIndex: 'requesterName', width: 130 },
        { title: '数据范围', dataIndex: 'scopeSummary', width: 250 },
        { title: '申请原因', dataIndex: 'requestReason', width: 220 },
        { title: '申请时间', dataIndex: 'requestedAt', width: 170, render: formatTime },
        { title: '状态', width: 100, render: () => <Tag color="gold">待审核</Tag> },
        { title: '操作', fixed: 'right', width: 96, render: (_: unknown, item) => <Space size={2}>
          <Tooltip title="批准"><Button actionKey="export-jobs.export-job-review-panel.2" type="text" aria-label={`批准-${item.requesterName}`} icon={<Check size={16} />} onClick={() => openDecision('approve', item)} /></Tooltip>
          <Tooltip title="驳回"><Button actionKey="export-jobs.export-job-review-panel.3" danger type="text" aria-label={`驳回-${item.requesterName}`} icon={<X size={16} />} onClick={() => openDecision('reject', item)} /></Tooltip>
        </Space> }
      ]}
    />
    <Modal actionPrefix="export-jobs.export-job-review-panel.modal.1"
      open={Boolean(decision)}
      title={decision?.action === 'approve' ? '批准敏感导出' : '驳回敏感导出'}
      okText={decision?.action === 'approve' ? '确认批准' : '确认驳回'}
      okButtonProps={{ danger: decision?.action === 'reject' }}
      width="min(520px, 92vw)"
      confirmLoading={submitting}
      onOk={() => form.submit()}
      onCancel={() => setDecision(undefined)}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" preserve={false} onFinish={(values) => void submit(values)}>
        <Form.Item label="审核意见" name="comment" rules={decision?.action === 'reject' ? [{ required: true, whitespace: true, message: '驳回时请填写审核意见' }] : []}>
          <Input.TextArea rows={4} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  </section>;
}

const toMessage = (error: unknown) => error instanceof Error ? error.message : '待审任务加载失败';
