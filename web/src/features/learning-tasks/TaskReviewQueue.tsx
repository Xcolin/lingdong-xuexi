import { useEffect, useState } from 'react';
import { Alert, Button, Table, Tag, Tooltip } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { Eye } from 'lucide-react';
import { taskReviewApi } from './reviewApi';
import { TaskReviewDrawer } from './TaskReviewDrawer';
import type { LearningTaskSourceType, TaskReview, TaskReviewPage } from './types';

const PAGE_SIZE = 20;
const sourceLabels: Record<LearningTaskSourceType, string> = {
  FAMILY: '家庭', ORGANIZATION: '机构', TEACHER: '教师'
};

/** 当前审核人自己的待审核队列。 */
export function TaskReviewQueue() {
  const [directory, setDirectory] = useState<TaskReviewPage>({
    items: [], page: 1, pageSize: PAGE_SIZE, total: 0
  });
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedAssignmentId, setSelectedAssignmentId] = useState<string | null>(null);

  useEffect(() => {
    void loadReviews(1);
  }, []);

  async function loadReviews(page: number): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      setDirectory(await taskReviewApi.list(page, PAGE_SIZE));
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      {errorMessage && (
        <Alert
          type="error"
          showIcon
          message={errorMessage}
          action={<Button size="small" onClick={() => void loadReviews(directory.page)}>重试</Button>}
        />
      )}
      <ProCard className="content-panel" bordered={false}>
        <Table<TaskReview>
          rowKey="assignmentId"
          loading={loading}
          dataSource={directory.items}
          locale={{ emptyText: '暂无审核待办' }}
          scroll={{ x: 900 }}
          pagination={{
            current: directory.page,
            pageSize: directory.pageSize,
            total: directory.total,
            showSizeChanger: false,
            onChange: (page) => void loadReviews(page)
          }}
          columns={[
            { title: '任务标题', dataIndex: 'title', key: 'title', width: 220 },
            { title: '学生', dataIndex: 'studentName', key: 'studentName', width: 120 },
            {
              title: '来源', dataIndex: 'sourceType', key: 'sourceType', width: 90,
              render: (source: LearningTaskSourceType) => <Tag>{sourceLabels[source]}</Tag>
            },
            {
              title: '提交次数', key: 'submissionNo', width: 100,
              render: (_, review) => `第 ${review.latestCheckIn.submissionNo} 次`
            },
            {
              title: '提交时间', key: 'submittedAt', width: 180,
              render: (_, review) => formatTime(review.latestCheckIn.submittedAt)
            },
            { title: '当前审核人', dataIndex: 'reviewerDisplayName', key: 'reviewer', width: 130 },
            {
              title: '操作', key: 'actions', fixed: 'right', width: 80,
              render: (_, review) => (
                <Tooltip title="查看审核">
                  <Button
                    type="text"
                    aria-label={`查看审核 ${review.title}`}
                    icon={<Eye size={16} />}
                    onClick={() => setSelectedAssignmentId(review.assignmentId)}
                  />
                </Tooltip>
              )
            }
          ]}
        />
      </ProCard>
      <TaskReviewDrawer
        open={selectedAssignmentId !== null}
        assignmentId={selectedAssignmentId}
        onClose={() => setSelectedAssignmentId(null)}
        onChanged={() => loadReviews(directory.page)}
      />
    </>
  );
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short'
  }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
