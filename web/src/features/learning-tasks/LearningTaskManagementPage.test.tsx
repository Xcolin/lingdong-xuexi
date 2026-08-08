import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { CurrentUser } from '../../api/auth';
import { LearningTaskManagementPage } from './LearningTaskManagementPage';

const learningTaskApi = vi.hoisted(() => ({
  list: vi.fn(),
  findById: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  publish: vi.fn(),
  stopRecurrence: vi.fn(),
  batchPublish: vi.fn(),
  listOrganizations: vi.fn(),
  listStudents: vi.fn(),
  listTeachers: vi.fn()
}));
const taskReviewApi = vi.hoisted(() => ({
  list: vi.fn(),
  findById: vi.fn(),
  listReviewerOptions: vi.fn(),
  readAttachment: vi.fn(),
  approve: vi.fn(),
  reject: vi.fn(),
  transfer: vi.fn()
}));
const taskDeferApi = vi.hoisted(() => ({
  list: vi.fn(),
  defer: vi.fn()
}));
const previousDayTaskCopyApi = vi.hoisted(() => ({
  preview: vi.fn(),
  copy: vi.fn(),
  retry: vi.fn()
}));
const taskTemplateApi = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  reorder: vi.fn()
}));

vi.mock('./api', () => ({ learningTaskApi }));
vi.mock('./reviewApi', () => ({ taskReviewApi }));
vi.mock('./taskDeferApi', () => ({ taskDeferApi }));
vi.mock('./previousDayTaskCopyApi', () => ({ previousDayTaskCopyApi }));
vi.mock('./taskTemplateApi', () => ({ taskTemplateApi }));

const parent: CurrentUser = {
  userId: '1874244142494646001',
  sessionId: '1874244142494646002',
  username: 'parent',
  displayName: '测试家长',
  clientType: 'WEB',
  roleCodes: ['PARENT']
};

describe('学习任务管理页面', () => {
  beforeEach(() => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:task-checkin-image')
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn()
    });
    learningTaskApi.list.mockResolvedValue({
      items: [{
        id: '1874244142494647001', sourceType: 'FAMILY', sourceOrganizationId: null,
        title: '家庭阅读', difficultyLevel: 2, basePoints: 20, durationMinutes: 30,
        scheduledDate: '2026-08-03', status: 'DRAFT', publishedAt: null,
        createdAt: '2026-08-01T10:00:00'
      }],
      page: 1,
      pageSize: 20,
      total: 1
    });
    learningTaskApi.listStudents.mockResolvedValue([
      { id: '1874244142494647101', studentName: '小灵', studentAccountMasked: '26****01', currentClassId: null, currentClassName: null }
    ]);
    learningTaskApi.listOrganizations.mockResolvedValue([]);
    learningTaskApi.listTeachers.mockResolvedValue([]);
    learningTaskApi.publish.mockResolvedValue({
      taskId: '1874244142494647001', assignmentCount: 1, status: 'PUBLISHED'
    });
    learningTaskApi.stopRecurrence.mockResolvedValue({
      taskId: '1874244142494647001',
      recurrenceId: '1874244142494647009',
      status: 'STOPPED',
      stoppedByUserId: parent.userId,
      stoppedAt: '2026-08-08T12:00:00'
    });
    learningTaskApi.findById.mockResolvedValue({
      id: '1874244142494647001', sourceType: 'FAMILY', sourceOrganizationId: null,
      creatorUserId: parent.userId, title: '家庭阅读', difficultyLevel: 2,
      basePoints: 20, durationMinutes: 30, scheduledDate: '2026-08-03',
      categoryCode: 'GENERAL', tagCodes: ['DAILY'], remark: null,
      reviewerUserId: parent.userId, reviewTimeoutHours: 72,
      recurrenceEnabled: false, recurrenceEndDate: null, recurrenceStatus: null,
      status: 'DRAFT', publishedAt: null, createdAt: '2026-08-01T10:00:00',
      updatedAt: '2026-08-01T10:00:00', targets: [{
        id: '1874244142494647111', targetType: 'STUDENT', targetId: '1874244142494647101'
      }]
    });
    learningTaskApi.update.mockResolvedValue({});
    taskDeferApi.list.mockResolvedValue({
      items: [{
        assignmentId: '1874244142494647401',
        title: '昨日阅读',
        studentId: '1874244142494647101',
        studentName: '小灵',
        sourceType: 'FAMILY',
        sourceOrganizationName: null,
        scheduledDate: '2026-08-07',
        currentStatus: 'NEEDS_IMPROVEMENT',
        lastDeferType: null,
        overnightMigrated: false
      }],
      page: 1,
      pageSize: 20,
      total: 1
    });
    taskDeferApi.defer.mockResolvedValue({
      assignmentId: '1874244142494647401',
      targetTaskId: '1874244142494647402',
      status: 'PENDING_CLAIM',
      targetDate: '2026-08-09',
      deferType: 'MANUAL',
      overnightMigrated: true
    });
    previousDayTaskCopyApi.preview.mockResolvedValue({
      studentId: '1874244142494647101',
      studentName: '小灵',
      sourceDate: '2026-08-07',
      targetDate: '2026-08-08',
      candidateCount: 2,
      duplicateTitles: ['每日阅读'],
      alreadyCopied: false,
      existingBatch: null
    });
    previousDayTaskCopyApi.copy.mockResolvedValue({
      batchId: '1874244142494647501',
      studentId: '1874244142494647101',
      sourceDate: '2026-08-07',
      targetDate: '2026-08-08',
      status: 'PARTIAL_FAILED',
      totalCount: 2,
      successCount: 1,
      failureCount: 1,
      items: [
        {
          itemId: '1874244142494647502', sourceTaskId: '1874244142494647503',
          targetTaskId: '1874244142494647504', taskTitle: '每日阅读', status: 'SUCCESS',
          failureCode: null, failureMessage: null, retryCount: 0
        },
        {
          itemId: '1874244142494647505', sourceTaskId: '1874244142494647506',
          targetTaskId: null, taskTitle: '口算练习', status: 'FAILED',
          failureCode: 'TASK_COPY_FAILED', failureMessage: '任务复制失败', retryCount: 0
        }
      ]
    });
    previousDayTaskCopyApi.retry.mockResolvedValue({
      batchId: '1874244142494647501',
      studentId: '1874244142494647101',
      sourceDate: '2026-08-07',
      targetDate: '2026-08-08',
      status: 'COMPLETED',
      totalCount: 2,
      successCount: 2,
      failureCount: 0,
      items: []
    });
    taskTemplateApi.list.mockResolvedValue([
      {
        id: '1874244142494647601', templateScope: 'SYSTEM',
        templateName: '每日阅读30分钟', taskTitle: '每日阅读', difficultyLevel: 2,
        durationMinutes: 30, categoryCode: 'READING', tagCodes: ['DAILY', 'READING'],
        remark: '完成当天阅读任务', sortOrder: 10, versionNo: 1,
        createdAt: '2026-08-08T10:00:00', updatedAt: '2026-08-08T10:00:00'
      },
      {
        id: '1874244142494647602', templateScope: 'PERSONAL',
        templateName: '周末口算', taskTitle: '口算练习', difficultyLevel: 1,
        durationMinutes: 20, categoryCode: 'MATH', tagCodes: ['WEEKEND'],
        remark: null, sortOrder: 10, versionNo: 1,
        createdAt: '2026-08-08T10:00:00', updatedAt: '2026-08-08T10:00:00'
      }
    ]);
    taskTemplateApi.create.mockResolvedValue({});
    taskTemplateApi.update.mockResolvedValue({});
    taskTemplateApi.remove.mockResolvedValue(undefined);
    taskTemplateApi.reorder.mockResolvedValue([]);
    const review = {
      assignmentId: '1874244142494647201',
      taskId: '1874244142494647002',
      title: '每日阅读打卡',
      basePoints: 20,
      studentId: '1874244142494647101',
      studentName: '小灵',
      sourceType: 'FAMILY',
      sourceOrganizationId: null,
      sourceOrganizationName: null,
      currentStatus: 'PENDING_REVIEW',
      currentReviewerId: parent.userId,
      reviewerDisplayName: parent.displayName,
      latestCheckIn: {
        id: '1874244142494647301',
        submissionNo: 1,
        content: '完成阅读并写下了摘要。',
        status: 'SUBMITTED',
        submittedAt: '2026-08-03T09:00:00',
        reviewComment: null,
        attachments: [{
          id: '1874244142494647302',
          originalName: 'reading.jpg',
          contentType: 'image/jpeg',
          sizeBytes: 4,
          contentUrl: '/api/v1/attachments/1874244142494647302/content'
        }]
      }
    } as const;
    taskReviewApi.list.mockResolvedValue({ items: [review], page: 1, pageSize: 20, total: 1 });
    taskReviewApi.findById.mockResolvedValue(review);
    taskReviewApi.listReviewerOptions.mockResolvedValue([]);
    taskReviewApi.readAttachment.mockResolvedValue(new Blob(['image'], { type: 'image/jpeg' }));
    taskReviewApi.reject.mockResolvedValue({
      assignmentId: review.assignmentId,
      currentStatus: 'IN_PROGRESS',
      checkInId: review.latestCheckIn.id,
      checkInStatus: 'REJECTED'
    });
    taskReviewApi.approve.mockResolvedValue({
      assignmentId: review.assignmentId,
      currentStatus: 'COMPLETED',
      checkInId: review.latestCheckIn.id,
      checkInStatus: 'APPROVED',
      awardedPoints: 20,
      totalPoints: 20,
      availablePoints: 20,
      ledgerId: '1874244142494647401'
    });
  });

  it('家长只使用家庭来源创建任务', async () => {
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    expect(await screen.findByText('家庭阅读')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '新建任务' }));

    expect(await screen.findByRole('dialog', { name: '新建学习任务' })).toBeInTheDocument();
    expect(screen.getByText('家庭任务')).toBeInTheDocument();
    expect(screen.queryByText('机构任务')).not.toBeInTheDocument();
    expect(screen.queryByText('教师任务')).not.toBeInTheDocument();
  });

  it('草稿经确认发布并刷新列表', async () => {
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    expect(await screen.findByText('家庭阅读')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '发布 家庭阅读' }));
    await user.click(await screen.findByRole('button', { name: '确认发布' }));

    await waitFor(() => {
      expect(learningTaskApi.publish).toHaveBeenCalledWith('1874244142494647001');
      expect(learningTaskApi.list).toHaveBeenCalledTimes(2);
    });
  });

  it('编辑草稿时提交每日固定任务配置', async () => {
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    await user.click(await screen.findByRole('button', { name: '编辑 家庭阅读' }));
    await screen.findByRole('dialog', { name: '编辑学习任务' });
    const recurrenceSwitch = screen.getByRole('switch', { name: '每日固定任务' });
    await user.click(recurrenceSwitch);
    await user.type(screen.getByLabelText('固定任务结束日'), '2026-08-10');
    await user.click(screen.getByRole('button', { name: '保存草稿' }));

    await waitFor(() => expect(learningTaskApi.update).toHaveBeenCalledWith(
      '1874244142494647001', expect.objectContaining({
        recurrenceEnabled: true,
        recurrenceEndDate: '2026-08-10'
      })
    ));

  });

  it('关闭每日固定任务时清空结束日', async () => {
    learningTaskApi.findById.mockResolvedValueOnce({
      id: '1874244142494647001', sourceType: 'FAMILY', sourceOrganizationId: null,
      creatorUserId: parent.userId, title: '家庭阅读', difficultyLevel: 2,
      basePoints: 20, durationMinutes: 30, scheduledDate: '2026-08-03',
      categoryCode: 'GENERAL', tagCodes: ['DAILY'], remark: null,
      reviewerUserId: parent.userId, reviewTimeoutHours: 72,
      recurrenceEnabled: true, recurrenceEndDate: '2026-08-10', recurrenceStatus: null,
      status: 'DRAFT', publishedAt: null, createdAt: '2026-08-01T10:00:00',
      updatedAt: '2026-08-01T10:00:00', targets: [{
        id: '1874244142494647111', targetType: 'STUDENT', targetId: '1874244142494647101'
      }]
    });
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    await user.click(await screen.findByRole('button', { name: '编辑 家庭阅读' }));
    await user.click(await screen.findByRole('switch', { name: '每日固定任务' }));
    await user.click(screen.getByRole('button', { name: '保存草稿' }));

    await waitFor(() => expect(learningTaskApi.update).toHaveBeenCalledWith(
      '1874244142494647001', expect.objectContaining({
        recurrenceEnabled: false,
        recurrenceEndDate: undefined
      })
    ));
  });

  it('活动固定任务经二次确认后停止并刷新列表', async () => {
    learningTaskApi.list.mockResolvedValue({
      items: [{
        id: '1874244142494647001', sourceType: 'FAMILY', sourceOrganizationId: null,
        title: '每日固定阅读', difficultyLevel: 2, basePoints: 20, durationMinutes: 30,
        scheduledDate: '2026-08-03', recurrenceEnabled: true, recurrenceStatus: 'ACTIVE',
        status: 'PUBLISHED', publishedAt: '2026-08-03T00:00:00',
        createdAt: '2026-08-01T10:00:00'
      }],
      page: 1,
      pageSize: 20,
      total: 1
    });
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    await user.click(await screen.findByRole('button', { name: '停止 每日固定阅读' }));
    await user.click(await screen.findByRole('button', { name: '确认停止' }));

    await waitFor(() => {
      expect(learningTaskApi.stopRecurrence).toHaveBeenCalledWith('1874244142494647001');
      expect(learningTaskApi.list).toHaveBeenCalledTimes(2);
    });
  });

  it('审核待办支持审核通过并按基础积分发放', async () => {
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    await user.click(await screen.findByText('审核待办'));
    await user.click(await screen.findByRole('button', { name: '查看审核 每日阅读打卡' }));

    expect(await screen.findByText('基础积分')).toBeInTheDocument();
    expect(await screen.findByAltText('reading.jpg')).toBeInTheDocument();
    expect(taskReviewApi.readAttachment).toHaveBeenCalledWith('1874244142494647302');
    await user.click(screen.getByRole('button', { name: '审核通过并发放 20 积分' }));
    await user.click(await screen.findByRole('button', { name: '确认通过' }));

    await waitFor(() => {
      expect(taskReviewApi.approve).toHaveBeenCalledWith('1874244142494647201');
      expect(taskReviewApi.list).toHaveBeenCalledTimes(2);
    });
  });

  it('审核待办保留驳回和转交操作', async () => {
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    await user.click(await screen.findByText('审核待办'));
    expect(await screen.findByText('每日阅读打卡')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '查看审核 每日阅读打卡' }));

    expect(await screen.findByRole('dialog', { name: '任务审核' })).toBeInTheDocument();
    expect(await screen.findByText('转交审核')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认转交' })).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('填写驳回意见'), '需要补充主要人物。');
    await user.click(screen.getByRole('button', { name: '驳回打卡' }));
    await user.click(await screen.findByRole('button', { name: '确认驳回' }));

    await waitFor(() => {
      expect(taskReviewApi.reject).toHaveBeenCalledWith(
        '1874244142494647201', '需要补充主要人物。'
      );
      expect(taskReviewApi.list).toHaveBeenCalledTimes(2);
    });
  });

  it('待优化任务可选择七天内日期顺延', async () => {
    const user = userEvent.setup();
    render(<LearningTaskManagementPage currentUser={parent} />);

    await user.click(await screen.findByText('待优化任务'));
    expect(await screen.findByText('昨日阅读')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '顺延' }));
    expect(await screen.findByRole('dialog', { name: '顺延任务' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '确认顺延' }));

    await waitFor(() => {
      expect(taskDeferApi.defer).toHaveBeenCalledWith(
        '1874244142494647401', expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/)
      );
      expect(taskDeferApi.list).toHaveBeenCalledTimes(2);
    });
  });

  it('家长确认同名后复制昨日任务并可重试失败条目', async () => {
    const user = userEvent.setup();
    render(
      <LearningTaskManagementPage
        currentUser={parent}
        previousDayTaskCopyEnabled
      />
    );

    await user.click(await screen.findByRole('button', { name: '复制昨日任务' }));
    expect(await screen.findByRole('dialog', { name: '复制昨日任务' })).toBeInTheDocument();
    expect(await screen.findByText('今天已存在同名任务')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认复制' })).toBeDisabled();
    await user.click(screen.getByRole('checkbox', { name: '仍然复制这些同名任务' }));
    await user.click(screen.getByRole('button', { name: '确认复制' }));

    await waitFor(() => expect(previousDayTaskCopyApi.copy).toHaveBeenCalledWith(
      '1874244142494647101', true
    ));
    expect(await screen.findByText('口算练习')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => expect(previousDayTaskCopyApi.retry).toHaveBeenCalledWith(
      '1874244142494647501', '1874244142494647505'
    ));
  });

  it('家长从模板库选用系统模板且不带入一次性任务字段', async () => {
    const user = userEvent.setup();
    render(
      <LearningTaskManagementPage
        currentUser={parent}
        learningTaskTemplateEnabled
      />
    );

    await user.click(await screen.findByRole('button', { name: '任务模板' }));
    expect(await screen.findByRole('dialog', { name: '任务模板' })).toBeInTheDocument();
    expect(screen.getByText('每日阅读30分钟')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: '个人模板（1）' }));
    expect(screen.getByText('周末口算')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: '系统模板（1）' }));
    await user.click(screen.getByRole('button', { name: '选用 每日阅读30分钟' }));

    expect(await screen.findByRole('dialog', { name: '新建学习任务' })).toBeInTheDocument();
    expect(screen.getByLabelText('任务标题')).toHaveValue('每日阅读');
    expect(screen.getByLabelText('执行时长（分钟）')).toHaveValue('30');
    expect(screen.getByLabelText('计划日期')).toHaveValue('');
    expect(screen.getByLabelText('任务分类')).toHaveValue('READING');
  });

  it('家长将当前任务可复用字段带入个人模板表单', async () => {
    const user = userEvent.setup();
    render(
      <LearningTaskManagementPage
        currentUser={parent}
        learningTaskTemplateEnabled
      />
    );

    await user.click(await screen.findByRole('button', { name: '新建任务' }));
    await user.type(screen.getByLabelText('任务标题'), '每日英语朗读');
    await user.click(screen.getByRole('button', { name: '保存为个人模板' }));

    expect(await screen.findByText('新建个人模板')).toBeInTheDocument();
    expect(screen.getByLabelText('模板名称')).toHaveValue('每日英语朗读');
    expect(screen.getByLabelText('任务标题', { selector: '#personalTaskTemplate_taskTitle' })).toHaveValue('每日英语朗读');
    expect(screen.getByLabelText('执行时长（分钟）', { selector: '#personalTaskTemplate_durationMinutes' })).toHaveValue('30');
  });
});
