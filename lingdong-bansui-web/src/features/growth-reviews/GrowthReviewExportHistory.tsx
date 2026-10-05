import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useRef, useState } from 'react';
import { Alert, App, Descriptions, Drawer, Select, Space, Tag, Tooltip } from 'antd';
import { Download, Eye, History } from 'lucide-react';
import { growthReviewExportApi as api, type GrowthReviewExportPage, type GrowthReviewExportRecord } from './exportApi';
import type { ExportJobStatus } from '../../api/export-jobs';
import { formatDateTime } from '../../utils/datetime';

const labels: Record<ExportJobStatus, string> = {
  QUEUED: '排队中', EXPORTING: '生成中', SUCCEEDED: '已完成', FAILED: '失败', PENDING_REVIEW: '待审核', REJECTED: '已拒绝'
};
const empty: GrowthReviewExportPage = { items: [], total: 0, page: 1, pageSize: 20 };

/** 父页面以学生标识为 key，撤权或切换孩子时卸载全部历史状态。 */
export function GrowthReviewExportHistory({ studentId, initialOpen = false }: { studentId: string; initialOpen?: boolean }) {
  const { message } = App.useApp();
  const [open, setOpen] = useState(initialOpen);
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<ExportJobStatus>();
  const [refresh, setRefresh] = useState(0);
  const [data, setData] = useState(empty);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [detail, setDetail] = useState<GrowthReviewExportRecord>();
  const [downloading, setDownloading] = useState<string>();
  const epoch = useRef(0);
  const detailRequest = useRef(0);
  useEffect(() => () => { epoch.current++; }, []);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true); setError(undefined); setData(empty); setDetail(undefined); setDownloading(undefined);
    void api.list(studentId, { page, pageSize: 20, status }).then(result => {
      if (active) setData(result);
    }).catch(cause => { if (active) setError(errorText(cause)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, studentId, page, status, refresh]);
  useEffect(() => {
    if (!open || loading || !data.items.some(item => item.status === 'QUEUED' || item.status === 'EXPORTING')) return;
    const timer = window.setTimeout(() => setRefresh(value => value + 1), 5000);
    return () => window.clearTimeout(timer);
  }, [open, loading, data]);

  function close() { epoch.current++; setOpen(false); setDetail(undefined); setData(empty); setDownloading(undefined); }
  async function showDetail(id: string) {
    const request = epoch.current;
    const sequence = ++detailRequest.current;
    setDetail(undefined);
    try { const result = await api.detail(id); if (request === epoch.current && sequence === detailRequest.current) setDetail(result); }
    catch (cause) { if (request === epoch.current && sequence === detailRequest.current) message.error(errorText(cause)); }
  }
  async function download(job: GrowthReviewExportRecord) {
    const request = epoch.current;
    setDownloading(job.id);
    try {
      const result = await api.downloadFile(job.id);
      if (request !== epoch.current) return;
      const url = URL.createObjectURL(result.blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = result.fileName ?? `${job.jobCode}.${result.blob.type === 'application/zip' ? 'zip' : 'pdf'}`;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) { if (request === epoch.current) message.error(errorText(cause)); }
    finally { if (request === epoch.current) setDownloading(undefined); }
  }
  return <>
    <Button actionKey="growth-reviews.growth-review-export-history.1" icon={<History size={16} />} onClick={() => { setPage(1); setOpen(true); }}>导出历史</Button>
    <Drawer title="复盘导出历史" open={open} onClose={close} width="min(1100px, 92vw)">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select aria-label="导出状态" placeholder="全部状态" allowClear value={status} style={{ width: 140 }}
          options={(['QUEUED', 'EXPORTING', 'SUCCEEDED', 'FAILED'] as const).map(value => ({ value, label: labels[value] }))}
          onChange={value => { epoch.current++; setStatus(value); setPage(1); }} />
      </Space>
      {error && <Alert type="error" showIcon message={error} />}
      <Table rowKey="id" size="small" loading={loading} dataSource={data.items} scroll={{ x: 650 }}
        pagination={{ current: page, pageSize: 20, total: data.total, showSizeChanger: false,
          onChange: value => { epoch.current++; setPage(value); } }} columns={[
          { title: '作业编码', dataIndex: 'jobCode', width: 240 },
          { title: '状态', dataIndex: 'status', width: 90, render: (value: ExportJobStatus) => <Tag>{labels[value]}</Tag> },
          { title: '完成份数', width: 90, render: (_, row) => `${row.processedRows}/${row.totalRows}` },
          { title: '申请时间', dataIndex: 'requestedAt', width: 170, render: (value: string) => formatDateTime(value) },
          { title: '操作', fixed: 'right', width: 90, render: (_, row) => <Space size={0}>
            <Tooltip title="查看详情"><Button actionKey="growth-reviews.growth-review-export-history.3" type="text" aria-label={`详情-${row.jobCode}`} icon={<Eye size={16} />} onClick={() => void showDetail(row.id)} /></Tooltip>
            <Tooltip title="下载结果"><Button actionKey="growth-reviews.growth-review-export-history.4" type="text" aria-label={`下载-${row.jobCode}`} icon={<Download size={16} />}
              disabled={row.status !== 'SUCCEEDED' || !!downloading} loading={downloading === row.id} onClick={() => void download(row)} /></Tooltip>
          </Space> }
        ]} />
      {detail && <section aria-label="导出作业详情" style={{ marginTop: 24, overflowWrap: 'anywhere' }}>
        <Descriptions column={{ xs: 1, sm: 2 }} title="作业详情" items={[
          { key: 'code', label: '作业编码', children: detail.jobCode },
          { key: 'template', label: '模板', children: detail.templateName },
          { key: 'version', label: '版本', children: detail.templateVersion },
          { key: 'reason', label: '申请原因', children: detail.requestReason },
          { key: 'status', label: '状态', children: labels[detail.status] },
          { key: 'failure', label: '失败原因', children: detail.failureMessage || '无' }
        ]} />
      </section>}
    </Drawer>
  </>;
}
function errorText(cause: unknown): string { return cause instanceof Error ? cause.message : '复盘导出历史加载失败'; }
