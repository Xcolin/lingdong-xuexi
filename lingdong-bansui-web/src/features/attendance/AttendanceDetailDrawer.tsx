import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Spin, Table } from 'antd';
import { RefreshCw } from 'lucide-react';
import { attendanceApi, type AttendanceDetails } from './api';
import { statusLabels } from './rules';

export function AttendanceDetailDrawer({ id, onClose, onAccessError }: { id: string; onClose: () => void; onAccessError: (e: unknown) => void }) {
  const [details, setDetails] = useState<AttendanceDetails>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true; setLoading(true); setDetails(undefined); setError(undefined);
    void attendanceApi.details(id).then((data) => { if (active) setDetails(data); })
      .catch((e: unknown) => { if (active) { setError(e instanceof Error ? e.message : '详情加载失败'); onAccessError(e); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, revision, onAccessError]);
  const record = details?.record;
  return <Drawer title="考勤详情" open width={880} onClose={onClose}>
    <div className="page-stack">
      {loading && <Spin />}
      {error && <Alert type="error" showIcon message={error} action={<Button icon={<RefreshCw size={16} />} onClick={() => setRevision((value) => value + 1)}>重试</Button>} />}
      {record && <>
        <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small" items={[
          { key: 'id', label: '记录ID', children: record.id }, { key: 'student', label: '学生', children: record.studentName },
          { key: 'class', label: '班级', children: record.className }, { key: 'date', label: '日期', children: record.attendanceDate },
          { key: 'status', label: '状态', children: statusLabels[record.status] }, { key: 'version', label: '版本', children: record.versionNo },
          { key: 'in', label: '签到时间', children: record.checkinTime || '-' }, { key: 'out', label: '签退时间', children: record.checkoutTime || '-' },
          { key: 'source', label: '来源', children: record.source === 'MANUAL' ? '人工登记' : record.source }, { key: 'recorder', label: '最近登记人', children: record.recorderName },
          { key: 'created', label: '创建时间', children: formatTime(record.createdAt) }, { key: 'updated', label: '更新时间', children: formatTime(record.updatedAt) }
        ]} />
        <h2 className="attendance-history-heading">变更历史</h2>
        <Table rowKey="id" dataSource={details.actions} pagination={false} scroll={{ x: 800 }} locale={{ emptyText: '暂无变更历史' }} columns={[
          { title: '动作', dataIndex: 'actionType', render: (value) => value === 'CREATE' ? '首次登记' : '更正' },
          { title: '操作人', dataIndex: 'operatorName' },
          { title: '状态变更', render: (_, row) => `${row.beforeStatus ? statusLabels[row.beforeStatus] : '未登记'} → ${statusLabels[row.afterStatus]}` },
          { title: '签到变更', render: (_, row) => `${row.beforeCheckinTime || '-'} → ${row.afterCheckinTime || '-'}` },
          { title: '签退变更', render: (_, row) => `${row.beforeCheckoutTime || '-'} → ${row.afterCheckoutTime || '-'}` },
          { title: '操作时间', dataIndex: 'createdAt', render: formatTime }
        ]} />
      </>}
    </div>
  </Drawer>;
}
function formatTime(value: string): string { return value.replace('T', ' ').slice(0, 19); }
