import { ViewportTable as Table } from '../../components/ViewportTable';
import { Alert, Modal, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { learningTaskApi } from './api';
import type { ManagedTaskProgressPage, TaskAssignmentStatus } from './types';
import { formatDateTime as formatTime } from '../../utils/datetime';

interface TaskProgressModalProps {
  taskId: string | null;
  taskTitle: string | null;
  onClose: () => void;
}

const statusNames: Record<TaskAssignmentStatus, string> = {
  PENDING_CLAIM: '待认领',
  IN_PROGRESS: '进行中',
  PENDING_REVIEW: '待审核',
  NEEDS_IMPROVEMENT: '待优化',
  EXEMPT: '免执行',
  COMPLETED: '已完成'
};

/** 展示学生级状态明细，复杂趋势和导出由报表专项承载。 */
export function TaskProgressModal({ taskId, taskTitle, onClose }: TaskProgressModalProps) {
  const [data, setData] = useState<ManagedTaskProgressPage>({
    items: [], page: 1, pageSize: 20, total: 0
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (taskId) void load(1);
  }, [taskId]);

  async function load(page: number): Promise<void> {
    if (!taskId) return;
    setLoading(true);
    setError(null);
    try {
      setData(await learningTaskApi.progress(taskId, page, 20));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '任务进度加载失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal
      title={`${taskTitle || '学习任务'} · 学生进度`}
      open={Boolean(taskId)}
      width="min(1200px, 92vw)"
      styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }}
      footer={null}
      onCancel={onClose}
      destroyOnHidden
    >
      {error && <Alert type="error" showIcon message={error} />}
      <Table
        rowKey="assignmentId"
        loading={loading}
        dataSource={data.items}
        scroll={{ x: 760 }}
        locale={{ emptyText: '暂无学生任务进度' }}
        pagination={{
          current: data.page,
          pageSize: data.pageSize,
          total: data.total,
          showSizeChanger: false,
          onChange: (page) => void load(page)
        }}
        columns={[
          { title: '学生', dataIndex: 'studentName', width: 120 },
          { title: '账号', dataIndex: 'studentAccountMasked', width: 120, render: (v) => v || '-' },
          { title: '班级', dataIndex: 'className', width: 150, render: (v) => v || '-' },
          {
            title: '状态', dataIndex: 'currentStatus', width: 100,
            render: (status: TaskAssignmentStatus) => <Tag>{statusNames[status]}</Tag>
          },
          { title: '计划日期', dataIndex: 'scheduledDate', width: 120 },
          { title: '认领时间', dataIndex: 'claimedAt', width: 170, render: formatTime },
          { title: '完成时间', dataIndex: 'completedAt', width: 170, render: formatTime }
        ]}
      />
    </Modal>
  );
}
