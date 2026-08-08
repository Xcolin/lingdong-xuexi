import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message
} from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { BookOpen, CircleStop, CopyPlus, Edit3, Plus, Rocket, Search } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import { learningTaskApi } from './api';
import { BatchPublishResultModal } from './BatchPublishResultModal';
import { LearningTaskEditorDrawer } from './LearningTaskEditorDrawer';
import { TaskReviewQueue } from './TaskReviewQueue';
import { TaskDeferQueue } from './TaskDeferQueue';
import { PreviousDayTaskCopyModal } from './PreviousDayTaskCopyModal';
import { TaskTemplateLibraryModal } from './TaskTemplateLibraryModal';
import type {
  BatchPublishResult,
  LearningTaskDetails,
  LearningTaskFilters,
  LearningTaskPage,
  LearningTaskSourceType,
  LearningTaskStatus,
  LearningTaskSummary,
  LearningTaskTemplate
} from './types';

const PAGE_SIZE = 20;
const sourceLabels: Record<LearningTaskSourceType, string> = {
  FAMILY: '家庭',
  ORGANIZATION: '机构',
  TEACHER: '教师'
};

interface LearningTaskManagementPageProps {
  currentUser: CurrentUser;
  previousDayTaskCopyEnabled?: boolean;
  learningTaskTemplateEnabled?: boolean;
}

interface FilterValues {
  sourceType?: LearningTaskSourceType;
  status?: LearningTaskStatus;
  scheduledDate?: string;
  keyword?: string;
}

export function LearningTaskManagementPage({
  currentUser,
  previousDayTaskCopyEnabled = false,
  learningTaskTemplateEnabled = false
}: LearningTaskManagementPageProps) {
  const [directory, setDirectory] = useState<LearningTaskPage>({
    items: [], page: 1, pageSize: PAGE_SIZE, total: 0
  });
  const [filters, setFilters] = useState<FilterValues>({});
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<LearningTaskDetails | null>(null);
  const [selectedTaskIds, setSelectedTaskIds] = useState<string[]>([]);
  const [batchPublishing, setBatchPublishing] = useState(false);
  const [batchResult, setBatchResult] = useState<BatchPublishResult | null>(null);
  const [copyModalOpen, setCopyModalOpen] = useState(false);
  const [templateLibraryOpen, setTemplateLibraryOpen] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<LearningTaskTemplate | null>(null);
  const [viewMode, setViewMode] = useState<'TASKS' | 'REVIEWS' | 'DEFER'>('TASKS');
  const [filterForm] = Form.useForm<FilterValues>();
  const sourceOptions = useMemo(() => availableSources(currentUser), [currentUser]);
  const canDefer = currentUser.roleCodes.some((role) =>
    ['PARENT', 'TEACHER', 'ORG_ADMIN'].includes(role)
  );
  const canCopyPreviousDay = previousDayTaskCopyEnabled
    && currentUser.roleCodes.includes('PARENT');
  const canUseTaskTemplates = learningTaskTemplateEnabled
    && currentUser.roleCodes.includes('PARENT');

  useEffect(() => {
    void loadTasks({}, 1);
  }, []);

  async function loadTasks(nextFilters: FilterValues, page: number): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    const query: LearningTaskFilters = {
      sourceType: nextFilters.sourceType,
      status: nextFilters.status,
      scheduledDate: nextFilters.scheduledDate || undefined,
      keyword: nextFilters.keyword?.trim() || undefined,
      page,
      pageSize: PAGE_SIZE
    };
    try {
      setDirectory(await learningTaskApi.list(query));
      setSelectedTaskIds([]);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function search(values: FilterValues): Promise<void> {
    const nextFilters = {
      ...values,
      scheduledDate: values.scheduledDate || undefined,
      keyword: values.keyword?.trim() || undefined
    };
    setFilters(nextFilters);
    await loadTasks(nextFilters, 1);
  }

  async function openEditor(task: LearningTaskSummary): Promise<void> {
    try {
      setEditingTask(await learningTaskApi.findById(task.id));
      setEditorOpen(true);
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  function confirmPublish(task: LearningTaskSummary): void {
    Modal.confirm({
      title: '确认发布学习任务',
      content: `${task.title}，计划日期 ${task.scheduledDate}。发布后内容不可再编辑。`,
      okText: '确认发布',
      cancelText: '取消',
      onOk: async () => {
        try {
          const result = await learningTaskApi.publish(task.id);
          message.success(`已发布并生成 ${result.assignmentCount} 个学生任务`);
          await loadTasks(filters, directory.page);
        } catch (error) {
          message.error(toMessage(error));
          throw error;
        }
      }
    });
  }

  function confirmBatchPublish(): void {
    Modal.confirm({
      title: '确认批量发布',
      content: `将逐项发布已选择的 ${selectedTaskIds.length} 个草稿，单项失败不会影响其他任务。`,
      okText: '确认发布',
      cancelText: '取消',
      onOk: async () => {
        setBatchPublishing(true);
        try {
          const result = await learningTaskApi.batchPublish(selectedTaskIds);
          setBatchResult(result);
          await loadTasks(filters, directory.page);
        } catch (error) {
          message.error(toMessage(error));
          throw error;
        } finally {
          setBatchPublishing(false);
        }
      }
    });
  }

  function confirmStopRecurrence(task: LearningTaskSummary): void {
    Modal.confirm({
      title: '确认停止每日固定任务',
      content: `${task.title}停止后不再生成后续日期任务，已经生成的学生任务不会删除。`,
      okText: '确认停止',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await learningTaskApi.stopRecurrence(task.id);
          message.success('每日固定任务已停止');
          await loadTasks(filters, directory.page);
        } catch (error) {
          message.error(toMessage(error));
          throw error;
        }
      }
    });
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>学习任务</h1>
        <Space wrap>
          <Segmented
            value={viewMode}
            options={[
              { value: 'TASKS', label: '任务管理' },
              { value: 'REVIEWS', label: '审核待办' },
              ...(canDefer ? [{ value: 'DEFER', label: '待优化任务' }] : [])
            ]}
            onChange={(value) => setViewMode(value as 'TASKS' | 'REVIEWS' | 'DEFER')}
          />
          {viewMode === 'TASKS' && (
            <>
              {canCopyPreviousDay && (
                <Button
                  icon={<CopyPlus size={16} />}
                  onClick={() => setCopyModalOpen(true)}
                >复制昨日任务</Button>
              )}
              {canUseTaskTemplates && (
                <Button
                  icon={<BookOpen size={16} />}
                  onClick={() => setTemplateLibraryOpen(true)}
                >任务模板</Button>
              )}
              <Button
                icon={<Rocket size={16} />}
                disabled={selectedTaskIds.length === 0}
                loading={batchPublishing}
                onClick={confirmBatchPublish}
              >批量发布</Button>
              <Button
                type="primary"
                icon={<Plus size={16} />}
                onClick={() => {
                  setEditingTask(null);
                  setSelectedTemplate(null);
                  setEditorOpen(true);
                }}
              >新建任务</Button>
            </>
          )}
        </Space>
      </div>

      {viewMode === 'REVIEWS' ? <TaskReviewQueue /> : viewMode === 'DEFER' ? <TaskDeferQueue /> : (
        <>
      {errorMessage && (
        <Alert
          type="error"
          showIcon
          message={errorMessage}
          action={<Button size="small" onClick={() => void loadTasks(filters, directory.page)}>重试</Button>}
        />
      )}

      <ProCard className="content-panel" bordered={false}>
        <Form form={filterForm} layout="inline" className="directory-filters" onFinish={search}>
          <Form.Item label="来源" name="sourceType">
            <Select allowClear options={sourceOptions} className="filter-select" />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Select allowClear className="filter-select" options={[
              { value: 'DRAFT', label: '草稿' },
              { value: 'PUBLISHED', label: '已发布' }
            ]} />
          </Form.Item>
          <Form.Item label="计划日期" name="scheduledDate"><Input type="date" /></Form.Item>
          <Form.Item label="标题" name="keyword"><Input allowClear maxLength={50} /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" icon={<Search size={16} />}>查询</Button></Form.Item>
        </Form>

        <Table<LearningTaskSummary>
          rowKey="id"
          loading={loading}
          dataSource={directory.items}
          locale={{ emptyText: '暂无学习任务' }}
          scroll={{ x: 1160 }}
          rowSelection={{
            selectedRowKeys: selectedTaskIds,
            onChange: (keys) => setSelectedTaskIds(keys.map(String)),
            getCheckboxProps: (task) => ({ disabled: task.status !== 'DRAFT' })
          }}
          pagination={{
            current: directory.page,
            pageSize: directory.pageSize,
            total: directory.total,
            showSizeChanger: false,
            onChange: (page) => void loadTasks(filters, page)
          }}
          columns={[
            { title: '任务标题', dataIndex: 'title', key: 'title', width: 220 },
            {
              title: '来源', dataIndex: 'sourceType', key: 'sourceType', width: 90,
              render: (source: LearningTaskSourceType) => <Tag color={sourceColor(source)}>{sourceLabels[source]}</Tag>
            },
            {
              title: '状态', dataIndex: 'status', key: 'status', width: 90,
              render: (status: LearningTaskStatus) => (
                <Tag color={status === 'DRAFT' ? 'gold' : 'green'}>{status === 'DRAFT' ? '草稿' : '已发布'}</Tag>
              )
            },
            { title: '计划日期', dataIndex: 'scheduledDate', key: 'scheduledDate', width: 120 },
            {
              title: '固定任务', key: 'recurrence', width: 100,
              render: (_, task) => recurrenceLabel(task)
            },
            { title: '难度', dataIndex: 'difficultyLevel', key: 'difficultyLevel', width: 80, render: (value: number) => `${value} 级` },
            { title: '积分', dataIndex: 'basePoints', key: 'basePoints', width: 80, render: (value: number) => `${value} 分` },
            { title: '时长', dataIndex: 'durationMinutes', key: 'durationMinutes', width: 90, render: (value: number) => `${value} 分钟` },
            { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatTime },
            {
              title: '操作', key: 'actions', fixed: 'right', width: 104,
              render: (_, task) => task.status === 'DRAFT' ? (
                <Space size={2}>
                  <ActionButton
                    label={`编辑 ${task.title}`}
                    title="编辑草稿"
                    icon={<Edit3 size={16} />}
                    onClick={() => void openEditor(task)}
                  />
                  <ActionButton
                    label={`发布 ${task.title}`}
                    title="发布任务"
                    icon={<Rocket size={16} />}
                    onClick={() => confirmPublish(task)}
                  />
                </Space>
              ) : task.recurrenceStatus === 'ACTIVE' ? (
                <ActionButton
                  label={`停止 ${task.title}`}
                  title="停止每日固定任务"
                  icon={<CircleStop size={16} />}
                  onClick={() => confirmStopRecurrence(task)}
                />
              ) : '-'
            }
          ]}
        />
      </ProCard>

      <LearningTaskEditorDrawer
        open={editorOpen}
        currentUser={currentUser}
        initialTask={editingTask}
        initialTemplate={selectedTemplate}
        taskTemplateEnabled={canUseTaskTemplates}
        onClose={() => {
          setEditorOpen(false);
          setSelectedTemplate(null);
        }}
        onSaved={() => loadTasks(filters, directory.page)}
      />
      <BatchPublishResultModal result={batchResult} onClose={() => setBatchResult(null)} />
      <PreviousDayTaskCopyModal
        open={copyModalOpen}
        onClose={() => setCopyModalOpen(false)}
        onCompleted={() => void loadTasks(filters, directory.page)}
      />
      <TaskTemplateLibraryModal
        open={templateLibraryOpen}
        onClose={() => setTemplateLibraryOpen(false)}
        onSelect={(template) => {
          setSelectedTemplate(template);
          setEditingTask(null);
          setTemplateLibraryOpen(false);
          setEditorOpen(true);
        }}
      />
        </>
      )}
    </div>
  );
}

function ActionButton({
  label,
  title,
  icon,
  onClick
}: { label: string; title: string; icon: ReactNode; onClick: () => void }) {
  return (
    <Tooltip title={title}>
      <Button type="text" aria-label={label} icon={icon} onClick={onClick} />
    </Tooltip>
  );
}

function availableSources(currentUser: CurrentUser) {
  const options: Array<{ role: string; value: LearningTaskSourceType; label: string }> = [
    { role: 'PARENT', value: 'FAMILY', label: '家庭' },
    { role: 'ORG_ADMIN', value: 'ORGANIZATION', label: '机构' },
    { role: 'TEACHER', value: 'TEACHER', label: '教师' }
  ];
  return options.filter((option) => currentUser.roleCodes.includes(option.role));
}

function sourceColor(source: LearningTaskSourceType): string {
  return source === 'FAMILY' ? 'magenta' : source === 'ORGANIZATION' ? 'blue' : 'cyan';
}

function recurrenceLabel(task: LearningTaskSummary) {
  if (!task.recurrenceEnabled) {
    return '-';
  }
  const labels = { ACTIVE: '运行中', COMPLETED: '已完成', STOPPED: '已停止' } as const;
  const colors = { ACTIVE: 'processing', COMPLETED: 'success', STOPPED: 'default' } as const;
  return task.recurrenceStatus
    ? <Tag color={colors[task.recurrenceStatus]}>{labels[task.recurrenceStatus]}</Tag>
    : <Tag>待发布</Tag>;
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short'
  }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
