import { useEffect, useState } from 'react';
import { Alert, Button, DatePicker, Modal, Space, Table, Tag, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { CalendarClock, RefreshCw } from 'lucide-react';
import { taskDeferApi } from './taskDeferApi';
import type { ManagedDeferCandidate, ManagedDeferCandidatePage } from './types';

const PAGE_SIZE = 20;
const sourceLabels = { FAMILY: '家庭', ORGANIZATION: '机构', TEACHER: '教师' } as const;

/** 管理角色查看并顺延数据范围内的待优化任务。 */
export function TaskDeferQueue() {
  const [page, setPage] = useState<ManagedDeferCandidatePage>({
    items: [], page: 1, pageSize: PAGE_SIZE, total: 0
  });
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selected, setSelected] = useState<ManagedDeferCandidate | null>(null);
  const [targetDate, setTargetDate] = useState<Dayjs | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    void load(1);
  }, []);

  async function load(nextPage: number): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      setPage(await taskDeferApi.list(nextPage, PAGE_SIZE));
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  function openDefer(candidate: ManagedDeferCandidate): void {
    setSelected(candidate);
    setTargetDate(dayjs().add(1, 'day').startOf('day'));
  }

  async function submit(): Promise<void> {
    if (!selected || !targetDate) return;
    setSubmitting(true);
    try {
      await taskDeferApi.defer(selected.assignmentId, targetDate.format('YYYY-MM-DD'));
      message.success('任务已顺延，学生可在新日期重新认领');
      setSelected(null);
      setTargetDate(null);
      await load(page.page);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  const today = dayjs().startOf('day');
  const latestDate = today.add(7, 'day');

  return (
    <div className="page-stack">
      {errorMessage && (
        <Alert
          type="error"
          showIcon
          message={errorMessage}
          action={<Button size="small" onClick={() => void load(page.page)}>重试</Button>}
        />
      )}
      <div className="table-toolbar">
        <span>待优化任务和尚未认领的自动顺延任务</span>
        <Button icon={<RefreshCw size={16} />} onClick={() => void load(page.page)}>刷新</Button>
      </div>
      <Table<ManagedDeferCandidate>
        rowKey="assignmentId"
        loading={loading}
        dataSource={page.items}
        locale={{ emptyText: '暂无可顺延任务' }}
        pagination={{
          current: page.page,
          pageSize: PAGE_SIZE,
          total: page.total,
          showSizeChanger: false,
          onChange: (nextPage) => void load(nextPage)
        }}
        columns={[
          { title: '任务', dataIndex: 'title', width: 220 },
          { title: '学生', dataIndex: 'studentName', width: 140 },
          {
            title: '来源', width: 120,
            render: (_, item) => sourceLabels[item.sourceType]
          },
          { title: '计划日期', dataIndex: 'scheduledDate', width: 130 },
          {
            title: '状态', width: 150,
            render: (_, item) => (
              <Space size={4} wrap>
                <Tag color={item.currentStatus === 'NEEDS_IMPROVEMENT' ? 'gold' : 'blue'}>
                  {item.currentStatus === 'NEEDS_IMPROVEMENT' ? '待优化' : '待认领'}
                </Tag>
                {item.overnightMigrated && <Tag>隔夜迁移</Tag>}
              </Space>
            )
          },
          {
            title: '操作', width: 100, fixed: 'right',
            render: (_, item) => (
              <Button
                type="link"
                icon={<CalendarClock size={16} />}
                onClick={() => openDefer(item)}
              >顺延</Button>
            )
          }
        ]}
      />
      <Modal
        title="顺延任务"
        open={selected !== null}
        okText="确认顺延"
        cancelText="取消"
        confirmLoading={submitting}
        okButtonProps={{ disabled: !targetDate }}
        onOk={() => void submit()}
        onCancel={() => {
          if (!submitting) {
            setSelected(null);
            setTargetDate(null);
          }
        }}
      >
        <Space direction="vertical" size={12} className="full-width-control">
          <span>{selected?.title}，当前计划日期 {selected?.scheduledDate}</span>
          <DatePicker
            value={targetDate}
            className="full-width-control"
            disabledDate={(date) => !date.isAfter(today) || date.isAfter(latestDate)}
            onChange={setTargetDate}
          />
        </Space>
      </Modal>
    </div>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '任务顺延操作失败';
}
