import { Modal, Table, Tag } from 'antd';
import type { BatchPublishResult } from './types';

interface BatchPublishResultModalProps {
  result: BatchPublishResult | null;
  onClose: () => void;
}

/** 清晰区分批量发布中的成功项和失败项。 */
export function BatchPublishResultModal({ result, onClose }: BatchPublishResultModalProps) {
  return (
    <Modal
      title="批量发布结果"
      open={Boolean(result)}
      onCancel={onClose}
      onOk={onClose}
      cancelButtonProps={{ style: { display: 'none' } }}
      okText="关闭"
      width={680}
    >
      {result && (
        <>
          <div className="batch-result-summary">
            <span>成功 <strong>{result.successCount}</strong> 项</span>
            <span>失败 <strong>{result.failureCount}</strong> 项</span>
          </div>
          <Table
            rowKey="taskId"
            size="small"
            pagination={false}
            dataSource={result.items}
            columns={[
              { title: '任务标识', dataIndex: 'taskId', key: 'taskId', width: 200 },
              {
                title: '结果', dataIndex: 'success', key: 'success', width: 90,
                render: (success: boolean) => (
                  <Tag color={success ? 'green' : 'red'}>{success ? '成功' : '失败'}</Tag>
                )
              },
              {
                title: '明细', key: 'detail',
                render: (_, item) => item.success
                  ? `生成 ${item.assignmentCount ?? 0} 个学生任务`
                  : item.failureReason
              }
            ]}
          />
        </>
      )}
    </Modal>
  );
}
