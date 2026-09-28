import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { exportJobApi } from '../../api/export-jobs';
import { ExportJobManagementPage } from './ExportJobManagementPage';

vi.mock('../../api/export-jobs', () => ({
  exportJobApi: {
    options: vi.fn(), list: vi.fn(), create: vi.fn(), detail: vi.fn(),
    download: vi.fn(), listReviews: vi.fn(), approve: vi.fn(), reject: vi.fn()
  }
}));

const job = {
  id: '1874244142494648001', jobCode: 'EXP-20260903-000001',
  exportType: 'GROWTH_POINT_LEDGER' as const, templateName: '报表导出模板', templateVersion: 'V1',
  status: 'SUCCEEDED' as const, totalRows: 12, processedRows: 12,
  failureCode: null, failureMessage: null, requestReason: '家庭存档',
  requestedAt: '2026-09-03T10:00:00', completedAt: '2026-09-03T10:00:03'
};

const options = {
  exportType: 'GROWTH_POINT_LEDGER' as const,
  templateName: '报表导出模板', templateVersion: 'V1', sensitive: false,
  columns: [
    { code: 'OCCURRED_AT', header: '发生时间', defaultSelected: true },
    { code: 'AMOUNT', header: '积分变动', defaultSelected: true }
  ],
  students: [{ id: '1874244142494647101', name: '小灵' }]
};

const review = {
  jobId: '1874244142494648002', systemTaskId: '1874244142494648003',
  requesterName: '系统管理员', exportType: 'IAM_CHANGE_AUDIT' as const,
  scopeSummary: '2026-09-01 至 2026-09-03，全部事件', requestReason: '安全核查',
  requestedAt: '2026-09-03T11:00:00'
};

describe('数据导出中心', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [job], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.options).mockResolvedValue(options);
    vi.mocked(exportJobApi.create).mockResolvedValue({ ...job, status: 'QUEUED' });
    vi.mocked(exportJobApi.detail).mockResolvedValue({
      job, columns: [{ code: 'OCCURRED_AT', header: '发生时间' }],
      scopeSummary: '小灵，全部时间', events: []
    });
    vi.mocked(exportJobApi.download).mockResolvedValue(new Blob(['xlsx']));
    vi.mocked(exportJobApi.listReviews).mockResolvedValue({ items: [review], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.approve).mockResolvedValue({ ...job, id: review.jobId, status: 'QUEUED' });
    vi.mocked(exportJobApi.reject).mockResolvedValue({ ...job, id: review.jobId, status: 'REJECTED' });
  });


  it('附件台账提交文本筛选、19位上传人、创建时间及服务端默认列', async () => {
    const columns = ['NAME', 'MODULE_CODE', 'UPLOADER_NAME', 'CREATED_AT', 'FILE_CATEGORY', 'SIZE_BYTES', 'STATUS'];
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, students: [], exportType: 'ATTACHMENT_LEDGER', columns: columns.map(code => ({ code, header: code, defaultSelected: true })) });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportAttachments />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalledWith('ATTACHMENT_LEDGER'));
    await userEvent.type(within(dialog).getByLabelText('模块编码'), 'CUSTOM_MODULE');
    await userEvent.type(within(dialog).getByLabelText('上传人 ID'), '1874244142494647109');
    await userEvent.type(within(dialog).getByLabelText('文件分类'), 'CUSTOM_CATEGORY');
    fireEvent.change(within(dialog).getByLabelText('创建开始时间'), { target: { value: '2026-09-01T10:00' } });
    await userEvent.type(within(dialog).getByLabelText('申请原因'), '核对附件台账');
    await userEvent.click(within(dialog).getByRole('button', { name: '提交导出' }));
    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({ exportType: 'ATTACHMENT_LEDGER', studentId: undefined, attachmentModuleCode: 'CUSTOM_MODULE', attachmentUploaderId: '1874244142494647109', attachmentFileCategory: 'CUSTOM_CATEGORY', startedAt: '2026-09-01T10:00:00', columns })));
  });

  it('异常报备导出只使用服务端有效班级并提交类型状态和时间', async () => {
    const columns = ['STUDENT_NAME', 'EXCEPTION_TYPE', 'REPORTER_NAME', 'STATUS', 'REPORTED_AT'];
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, students: [], exportType: 'EXCEPTION_REPORT_LEDGER',
      exceptionClasses: [{ id: '1874244142494647109', name: '授权班级' }],
      columns: columns.map(code => ({ code, header: code, defaultSelected: true })) });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportExceptions />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalledWith('EXCEPTION_REPORT_LEDGER'));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '班级' }));
    await userEvent.click(await screen.findByText('授权班级', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '异常类型' }));
    await userEvent.click(await screen.findByText('心态异常', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '报备状态' }));
    await userEvent.click(await screen.findByText('已处理', { selector: '.ant-select-item-option-content' }));
    fireEvent.change(within(dialog).getByLabelText('开始时间'), { target: { value: '2026-09-01T10:00' } });
    await userEvent.type(within(dialog).getByLabelText('申请原因'), '核对报备记录');
    await userEvent.click(within(dialog).getByRole('button', { name: '提交导出' }));
    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType: 'EXCEPTION_REPORT_LEDGER', studentId: undefined, exceptionClassId: '1874244142494647109',
      exceptionType: 'MENTAL_STATE', exceptionStatus: 'HANDLED', startedAt: '2026-09-01T10:00:00', columns
    })));
  });

  it.each(['revision', 'permission', 'read'])('附件台账撤回 %s 后清除详情并丢弃迟到文件', async (change) => {
    const exceptionJob = { ...job, exportType: 'ATTACHMENT_LEDGER' as const };
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [exceptionJob], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.detail).mockResolvedValue({ job: exceptionJob, columns: [], scopeSummary: '附件安全元数据', events: [] });
    let resolveDownload!: (value: Blob) => void;
    vi.mocked(exportJobApi.download).mockReturnValue(new Promise(resolve => { resolveDownload = resolve; }));
    const createObjectURL = vi.fn(() => 'blob:test');
    vi.stubGlobal('URL', class extends URL { static createObjectURL = createObjectURL; static revokeObjectURL = vi.fn(); });
    try {
      const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false, canExportAttachments: true };
      const view = renderPage(<ExportJobManagementPage {...props} accessRevision="teacher" />);
      await screen.findByText(job.jobCode);
      await userEvent.click(screen.getByRole('button', { name: `详情-${job.jobCode}` }));
      expect(await screen.findByText('附件安全元数据')).toBeInTheDocument();
      await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} accessRevision={change === 'revision' ? 'changed' : 'teacher'} canExportAttachments={change !== 'permission'} canRead={change !== 'read'} /></ConfigProvider>);
      await act(async () => { resolveDownload(new Blob(['exception-export'])); });
      await waitFor(() => expect(screen.queryByText('附件安全元数据')).not.toBeInTheDocument());
      expect(createObjectURL).not.toHaveBeenCalled();
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} accessRevision="org-admin" canExportAttachments={false} /></ConfigProvider>);
      expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument();
    } finally { vi.unstubAllGlobals(); }
  });

  it('异常台账权限上下文变化后清除详情并丢弃迟到文件', async () => {
    const exceptionJob = { ...job, exportType: 'EXCEPTION_REPORT_LEDGER' as const };
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [exceptionJob], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.detail).mockResolvedValue({ job: exceptionJob, columns: [], scopeSummary: '授权班级报备', events: [] });
    let resolveDownload!: (value: Blob) => void;
    vi.mocked(exportJobApi.download).mockReturnValue(new Promise(resolve => { resolveDownload = resolve; }));
    const createObjectURL = vi.fn(() => 'blob:test');
    vi.stubGlobal('URL', class extends URL { static createObjectURL = createObjectURL; static revokeObjectURL = vi.fn(); });
    try {
      const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false, canExportExceptions: true };
      const view = renderPage(<ExportJobManagementPage {...props} accessRevision="teacher" />);
      await screen.findByText(job.jobCode);
      await userEvent.click(screen.getByRole('button', { name: `详情-${job.jobCode}` }));
      expect(await screen.findByText('授权班级报备')).toBeInTheDocument();
      await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} accessRevision="org-admin" /></ConfigProvider>);
      await act(async () => { resolveDownload(new Blob(['exception-export'])); });
      await waitFor(() => expect(screen.queryByText('授权班级报备')).not.toBeInTheDocument());
      expect(createObjectURL).not.toHaveBeenCalled();
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} accessRevision="org-admin" canExportExceptions={false} /></ConfigProvider>);
      expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument();
    } finally { vi.unstubAllGlobals(); }
  });

  it('奖励兑换报表使用主家长学生选项及兑换状态和申请时间', async () => {
    const columns = ['REWARD_NAME', 'REQUIRED_POINTS', 'REQUESTED_AT', 'APPROVAL_STATUS', 'VERIFICATION_STATUS'];
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, exportType: 'REWARD_EXCHANGE_LEDGER', columns: columns.map(code => ({ code, header: code, defaultSelected: true })) });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportRewards />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalledWith('REWARD_EXCHANGE_LEDGER'));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '学生' }));
    await userEvent.click(await screen.findByText('小灵', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '兑换状态' }));
    await userEvent.click(await screen.findByText('已核销', { selector: '.ant-select-item-option-content' }));
    fireEvent.change(within(dialog).getByLabelText('开始时间'), { target: { value: '2026-09-01T10:00' } });
    await userEvent.type(within(dialog).getByLabelText('申请原因'), '家庭兑换存档');
    await userEvent.click(within(dialog).getByRole('button', { name: '提交导出' }));
    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType: 'REWARD_EXCHANGE_LEDGER', studentId: options.students[0].id,
      rewardExchangeStatus: 'VERIFIED', startedAt: '2026-09-01T10:00:00', columns
    })));
  });

  it.each(['canRead', 'canExportRewards'] as const)('奖励报表撤回 %s 后关闭详情并隐藏下载', async (revoked) => {
    const rewardJob = { ...job, exportType: 'REWARD_EXCHANGE_LEDGER' as const };
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [rewardJob], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.detail).mockResolvedValue({ job: rewardJob, columns: [], scopeSummary: '家庭兑换记录', events: [] });
    const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false, canExportRewards: true };
    const view = renderPage(<ExportJobManagementPage {...props} />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: `详情-${job.jobCode}` }));
    expect(await screen.findByText('家庭兑换记录')).toBeInTheDocument();
    view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} {...{ [revoked]: false }} /></ConfigProvider>);
    await waitFor(() => expect(screen.queryByText('家庭兑换记录')).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument();
  });

  it('奖励报表请求期间撤权不保存迟到文件', async () => {
    let resolveDownload!: (value: Blob) => void;
    vi.mocked(exportJobApi.download).mockReturnValue(new Promise(resolve => { resolveDownload = resolve; }));
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [{ ...job, exportType: 'REWARD_EXCHANGE_LEDGER' }], page: 1, pageSize: 20, total: 1 });
    const createObjectURL = vi.fn(() => 'blob:test');
    vi.stubGlobal('URL', class extends URL { static createObjectURL = createObjectURL; static revokeObjectURL = vi.fn(); });
    try {
      const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false };
      const view = renderPage(<ExportJobManagementPage {...props} canExportRewards />);
      await screen.findByText(job.jobCode);
      await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} canExportRewards={false} /></ConfigProvider>);
      await act(async () => { resolveDownload(new Blob(['family-export'])); });
      expect(createObjectURL).not.toHaveBeenCalled();
    } finally { vi.unstubAllGlobals(); }
  });

  it('展示本人作业进度并仅在成功后允许下载', async () => {
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary canSubmitSensitive={false} canReview={false} />);

    expect(await screen.findByText(job.jobCode)).toBeInTheDocument();
    expect(screen.getByText('12 / 12')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
    expect(exportJobApi.download).toHaveBeenCalledWith(job.id);
    expect(screen.queryByRole('tab', { name: '敏感导出审核' })).not.toBeInTheDocument();
  });

  it('系统管理员可按字典类型和项状态提交字典台账导出', async () => {
    const user=userEvent.setup();
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, exportType: 'DICTIONARY_LEDGER', students: [],
      columns: [{ code: 'ITEM_CODE', header: '项编码', defaultSelected: true }] });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportDictionary />);
    await screen.findByText(job.jobCode);
    await user.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog=await screen.findByRole('dialog', {name:'新建数据导出'});
    await waitFor(()=>expect(exportJobApi.options).toHaveBeenCalledWith('DICTIONARY_LEDGER'));
    await user.type(within(dialog).getByLabelText('字典类型编码'),'EXPORT_SAMPLE');
    fireEvent.mouseDown(within(dialog).getByRole('combobox',{name:'字典项状态'}));
    await user.click(await screen.findByText('启用',{selector:'.ant-select-item-option-content'}));
    await user.type(within(dialog).getByLabelText('申请原因'),'核对台账');
    await user.click(within(dialog).getByRole('button',{name:'提交导出'}));
    await waitFor(()=>expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType:'DICTIONARY_LEDGER',dictionaryTypeCode:'EXPORT_SAMPLE',dictionaryStatus:'ENABLED',columns:['ITEM_CODE']
    })));
  });

  it('字典导出权限或功能不可用时隐藏创建和历史作业内容入口', async () => {
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [{ ...job, exportType: 'DICTIONARY_LEDGER' }], page: 1, pageSize: 20, total: 1 });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportDictionary={false} />);
    await screen.findByText(job.jobCode);
    expect(screen.queryByRole('button', { name: '新建导出' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `详情-${job.jobCode}` })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument();
    expect(exportJobApi.options).not.toHaveBeenCalled();
  });

  it('系统管理员按模板类型模块及状态提交模板台账导出', async () => {
    const user = userEvent.setup();
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, exportType: 'TEMPLATE_LEDGER', students: [],
      columns: [{ code: 'TEMPLATE_NAME', header: '名称', defaultSelected: true }] });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportTemplate />);
    await screen.findByText(job.jobCode);
    await user.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog', { name: '新建数据导出' });
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalledWith('TEMPLATE_LEDGER'));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '模板类型' }));
    await user.click(await screen.findByText('导出', { selector: '.ant-select-item-option-content' }));
    await user.type(within(dialog).getByLabelText('适用模块编码'), 'REPORT');
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '模板状态' }));
    await user.click(await screen.findByText('停用', { selector: '.ant-select-item-option-content' }));
    await user.type(within(dialog).getByLabelText('申请原因'), '核对模板台账');
    await user.click(within(dialog).getByRole('button', { name: '提交导出' }));
    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType: 'TEMPLATE_LEDGER', templateType: 'EXPORT', templateModuleCode: 'REPORT', templateStatus: 'DISABLED', columns: ['TEMPLATE_NAME']
    })));
  });

  it('模板导出功能关闭时隐藏创建及历史详情下载', async () => {
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [{ ...job, exportType: 'TEMPLATE_LEDGER' }], page: 1, pageSize: 20, total: 1 });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportTemplate={false} />);
    await screen.findByText(job.jobCode);
    expect(screen.getByText('导入导出模板台账')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新建导出' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `详情-${job.jobCode}` })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument();
  });

  it('系统任务台账只使用服务端可见类型并提交状态时间', async () => {
    const columns = ['TASK_TYPE', 'SUBMITTER_ID', 'REVIEWER_ID', 'STATUS', 'CREATED_AT', 'REVIEW_COMMENT'];
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, exportType: 'SYSTEM_TASK_LEDGER', students: [], systemTaskTypes: ['CACHE_CLEAR'],
      columns: columns.map(code => ({ code, header: code, defaultSelected: true })) });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportSystemTasks />);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByLabelText('任务类型');
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '任务类型' }));
    await userEvent.click(await screen.findByText('缓存清除', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '任务状态' }));
    await userEvent.click(await screen.findByText('待审核', { selector: '.ant-select-item-option-content' }));
    fireEvent.change(within(dialog).getByLabelText('开始时间'), { target: { value: '2026-09-01T10:00' } });
    fireEvent.change(within(dialog).getByLabelText('结束时间'), { target: { value: '2026-09-02T10:00' } });
    await userEvent.type(within(dialog).getByLabelText('申请原因'), '核对系统任务');
    await userEvent.click(within(dialog).getByRole('button', { name: '提交导出' }));
    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType: 'SYSTEM_TASK_LEDGER', systemTaskType: 'CACHE_CLEAR', systemTaskStatus: 'PENDING_REVIEW',
      startedAt: '2026-09-01T10:00:00', endedAt: '2026-09-02T10:00:00', columns
    })));
  });

  it('系统任务领域权限变化时清除已打开详情，即使导出权限仍存在', async () => {
    const taskJob = { ...job, exportType: 'SYSTEM_TASK_LEDGER' as const };
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [taskJob], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.detail).mockResolvedValue({ job: taskJob, columns: [], scopeSummary: '本人系统任务', events: [] });
    const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false, canExportSystemTasks: true };
    const view = renderPage(<ExportJobManagementPage {...props} accessRevision="cache-enabled" />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: `详情-${job.jobCode}` }));
    expect(await screen.findByText('本人系统任务')).toBeInTheDocument();
    view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} accessRevision="cache-disabled" /></ConfigProvider>);
    await waitFor(() => expect(screen.queryByText('本人系统任务')).not.toBeInTheDocument());
  });

  it('缓存日志按缓存域状态和申请时间提交，列来自服务端且说明不执行操作', async () => {
    const columns = ['CACHE_DOMAIN', 'MODULE', 'OPERATION', 'OPERATOR_ID', 'OCCURRED_AT', 'RESULT'];
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, exportType: 'CACHE_OPERATION_LOG', students: [],
      columns: columns.map(code => ({ code, header: code, defaultSelected: true })) });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportCache />);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByLabelText('缓存域');
    expect(within(dialog).getByText(/申请时间.*闭区间/)).toBeInTheDocument();
    expect(within(dialog).getByText(/未执行记录.*执行人.*空/)).toBeInTheDocument();
    expect(within(dialog).getByText(/用户会话清除.*强制退出/)).toBeInTheDocument();
    expect(within(dialog).getByText(/导出不执行缓存操作/)).toBeInTheDocument();
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '缓存域' }));
    await userEvent.click(await screen.findByText('用户会话', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '操作状态' }));
    await userEvent.click(await screen.findByText('待执行', { selector: '.ant-select-item-option-content' }));
    fireEvent.change(within(dialog).getByLabelText('开始时间'), { target: { value: '2026-09-01T10:00' } });
    fireEvent.change(within(dialog).getByLabelText('结束时间'), { target: { value: '2026-09-02T10:00' } });
    await userEvent.type(within(dialog).getByLabelText('申请原因'), '核对缓存操作日志');
    await userEvent.click(within(dialog).getByRole('button', { name: '提交导出' }));
    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType: 'CACHE_OPERATION_LOG', cacheDomain: 'USER_SESSION', cacheStatus: 'PENDING',
      startedAt: '2026-09-01T10:00:00', endedAt: '2026-09-02T10:00:00', columns
    })));
  });

  it('接口服务台账按调用方状态及字符串责任人标识提交', async () => {
    const user = userEvent.setup();
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, exportType: 'INTERFACE_SERVICE_LEDGER', students: [],
      columns: [{ code: 'SERVICE_NAME', header: '名称', defaultSelected: true }] });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportInterface />);
    await screen.findByText(job.jobCode);
    await user.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog', { name: '新建数据导出' });
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalledWith('INTERFACE_SERVICE_LEDGER'));
    await user.type(within(dialog).getByLabelText('调用方'), '业务系统');
    await user.type(within(dialog).getByLabelText('责任人标识'), '1874244142494647101');
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '接口状态' }));
    await user.click(await screen.findByText('停用', { selector: '.ant-select-item-option-content' }));
    await user.type(within(dialog).getByLabelText('申请原因'), '核对接口台账');
    await user.click(within(dialog).getByRole('button', { name: '提交导出' }));
    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType: 'INTERFACE_SERVICE_LEDGER', interfaceCallerName: '业务系统', interfaceStatus: 'DISABLED',
      interfaceOwnerId: '1874244142494647101', columns: ['SERVICE_NAME']
    })));
  });

  it.each(['canExportCache', 'canRead'] as const)('缓存权限 %s 撤回后关闭已打开的详情并隐藏下载入口', async (revoked) => {
    const interfaceJob = { ...job, exportType: 'CACHE_OPERATION_LOG' as const };
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [interfaceJob], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.detail).mockResolvedValue({ job: interfaceJob, columns: [], scopeSummary: '缓存服务台账', events: [] });
    const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false };
    const view = renderPage(<ExportJobManagementPage {...props} canExportCache />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: `详情-${job.jobCode}` }));
    expect(await screen.findByText('数据范围')).toBeInTheDocument();
    view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} {...{ canExportCache: true, [revoked]: false }} /></ConfigProvider>);
    await waitFor(() => expect(screen.queryByText('数据范围')).not.toBeInTheDocument());
    if (revoked === 'canExportCache') expect(screen.queryByRole('button', { name: '新建导出' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument();
  });

  it('系统任务领域变化后不保存迟到文件，即使总体导出权限仍存在', async () => {
    let resolveDownload!: (value: Blob) => void;
    vi.mocked(exportJobApi.download).mockReturnValue(new Promise(resolve => { resolveDownload = resolve; }));
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [{ ...job, exportType: 'SYSTEM_TASK_LEDGER' }], page: 1, pageSize: 20, total: 1 });
    const createObjectURL = vi.fn(() => 'blob:test');
    vi.stubGlobal('URL', class extends URL { static createObjectURL = createObjectURL; static revokeObjectURL = vi.fn(); });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    try {
      const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false, canExportSystemTasks: true };
      const view = renderPage(<ExportJobManagementPage {...props} accessRevision="cache-enabled" />);
      await screen.findByText(job.jobCode);
      await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} accessRevision="cache-disabled" /></ConfigProvider>);
      await act(async () => { resolveDownload(new Blob(['private-export'])); });
      expect(createObjectURL).not.toHaveBeenCalled();
      expect(click).not.toHaveBeenCalled();
    } finally { click.mockRestore(); vi.unstubAllGlobals(); }
  });

  it('缓存文件请求期间撤权后不保存迟到的文件', async () => {
    let resolveDownload!: (value: Blob) => void;
    vi.mocked(exportJobApi.download).mockReturnValue(new Promise(resolve => { resolveDownload = resolve; }));
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [{ ...job, exportType: 'CACHE_OPERATION_LOG' }], page: 1, pageSize: 20, total: 1 });
    const createObjectURL = vi.fn(() => 'blob:test');
    vi.stubGlobal('URL', class extends URL { static createObjectURL = createObjectURL; static revokeObjectURL = vi.fn(); });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    try {
      const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false };
      const view = renderPage(<ExportJobManagementPage {...props} canExportCache />);
      await screen.findByText(job.jobCode);
      await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} canExportCache={false} /></ConfigProvider>);
      resolveDownload(new Blob(['private-export']));
      await waitFor(() => expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument());
      expect(createObjectURL).not.toHaveBeenCalled();
      expect(click).not.toHaveBeenCalled();
    } finally { click.mockRestore(); vi.unstubAllGlobals(); }
  });

  it('缓存创建中撤权后迟到的选项不能恢复弹窗', async () => {
    let resolveOptions!: (value: typeof options) => void;
    vi.mocked(exportJobApi.options).mockReturnValue(new Promise(resolve => { resolveOptions = resolve; }));
    const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false };
    const view = renderPage(<ExportJobManagementPage {...props} canExportCache />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalled());
    view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} canExportCache={false} /></ConfigProvider>);
    resolveOptions(options);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(exportJobApi.create).not.toHaveBeenCalled();
  });

  it.each(['canExportInterface', 'canRead'] as const)('接口权限 %s 撤回后关闭已打开的详情并隐藏下载入口', async (revoked) => {
    const interfaceJob = { ...job, exportType: 'INTERFACE_SERVICE_LEDGER' as const };
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [interfaceJob], page: 1, pageSize: 20, total: 1 });
    vi.mocked(exportJobApi.detail).mockResolvedValue({ job: interfaceJob, columns: [], scopeSummary: '接口服务台账', events: [] });
    const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false };
    const view = renderPage(<ExportJobManagementPage {...props} canExportInterface />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: `详情-${job.jobCode}` }));
    expect(await screen.findByText('数据范围')).toBeInTheDocument();
    view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} {...{ canExportInterface: true, [revoked]: false }} /></ConfigProvider>);
    await waitFor(() => expect(screen.queryByText('数据范围')).not.toBeInTheDocument());
    if (revoked === 'canExportInterface') expect(screen.queryByRole('button', { name: '新建导出' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument();
  });

  it('接口文件请求期间撤权后不保存迟到的文件', async () => {
    let resolveDownload!: (value: Blob) => void;
    vi.mocked(exportJobApi.download).mockReturnValue(new Promise(resolve => { resolveDownload = resolve; }));
    vi.mocked(exportJobApi.list).mockResolvedValue({ items: [{ ...job, exportType: 'INTERFACE_SERVICE_LEDGER' }], page: 1, pageSize: 20, total: 1 });
    const createObjectURL = vi.fn(() => 'blob:test');
    vi.stubGlobal('URL', class extends URL { static createObjectURL = createObjectURL; static revokeObjectURL = vi.fn(); });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    try {
      const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false };
      const view = renderPage(<ExportJobManagementPage {...props} canExportInterface />);
      await screen.findByText(job.jobCode);
      await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
      view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} canExportInterface={false} /></ConfigProvider>);
      resolveDownload(new Blob(['private-export']));
      await waitFor(() => expect(screen.queryByRole('button', { name: `下载-${job.jobCode}` })).not.toBeInTheDocument());
      expect(createObjectURL).not.toHaveBeenCalled();
      expect(click).not.toHaveBeenCalled();
    } finally { click.mockRestore(); vi.unstubAllGlobals(); }
  });

  it('接口创建中撤权后迟到的选项不能恢复弹窗', async () => {
    let resolveOptions!: (value: typeof options) => void;
    vi.mocked(exportJobApi.options).mockReturnValue(new Promise(resolve => { resolveOptions = resolve; }));
    const props = { canRead: true, canCreateOrdinary: false, canSubmitSensitive: false, canReview: false };
    const view = renderPage(<ExportJobManagementPage {...props} canExportInterface />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalled());
    view.rerender(<ConfigProvider locale={zhCN}><ExportJobManagementPage {...props} canExportInterface={false} /></ConfigProvider>);
    resolveOptions(options);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(exportJobApi.create).not.toHaveBeenCalled();
  });

  it('接口台账拒绝非19位责任人标识而不调用创建接口', async () => {
    vi.mocked(exportJobApi.options).mockResolvedValue({ ...options, exportType: 'INTERFACE_SERVICE_LEDGER', students: [] });
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} canExportInterface />);
    await screen.findByText(job.jobCode);
    await userEvent.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(await within(dialog).findByLabelText('责任人标识'), '123');
    await userEvent.type(within(dialog).getByLabelText('申请原因'), '核对台账');
    await userEvent.click(within(dialog).getByRole('button', { name: '提交导出' }));
    expect(await screen.findByText('请输入有效的 19 位责任人标识')).toBeInTheDocument();
    expect(exportJobApi.create).not.toHaveBeenCalled();
  });

  it('家长创建积分台账导出时只能使用服务端返回的学生与列', async () => {
    const user = userEvent.setup();
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary canSubmitSensitive={false} canReview={false} />);
    await screen.findByText(job.jobCode);

    await user.click(screen.getByRole('button', { name: '新建导出' }));
    const dialog = await screen.findByRole('dialog', { name: '新建数据导出' });
    await waitFor(() => expect(exportJobApi.options).toHaveBeenCalledWith('GROWTH_POINT_LEDGER'));
    expect(within(dialog).getByText('发生时间')).toBeInTheDocument();
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '学生' }));
    await user.click(await screen.findByText('小灵', { selector: '.ant-select-item-option-content' }));
    await user.type(within(dialog).getByLabelText('申请原因'), '家庭阶段存档');
    await user.click(within(dialog).getByRole('button', { name: '提交导出' }));

    await waitFor(() => expect(exportJobApi.create).toHaveBeenCalledWith(expect.objectContaining({
      exportType: 'GROWTH_POINT_LEDGER', studentId: options.students[0].id,
      columns: ['OCCURRED_AT', 'AMOUNT'], reason: '家庭阶段存档'
    })));
  });

  it('审核员只加载敏感待审任务并可批准', async () => {
    const user = userEvent.setup();
    renderPage(<ExportJobManagementPage canRead={false} canCreateOrdinary={false} canSubmitSensitive={false} canReview />);

    expect(await screen.findByText('安全核查')).toBeInTheDocument();
    expect(exportJobApi.list).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: '批准-系统管理员' }));
    const dialog = screen.getByRole('dialog', { name: '批准敏感导出' });
    await user.type(within(dialog).getByLabelText('审核意见'), '核查范围合理');
    await user.click(within(dialog).getByRole('button', { name: '确认批准' }));
    await waitFor(() => expect(exportJobApi.approve).toHaveBeenCalledWith(review.systemTaskId, '核查范围合理'));
  });

  it('无创建权限时不显示新建命令', async () => {
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary={false} canSubmitSensitive={false} canReview={false} />);
    await screen.findByText(job.jobCode);
    expect(screen.queryByRole('button', { name: '新建导出' })).not.toBeInTheDocument();
  });
});

function renderPage(page: React.ReactNode) {
  return render(<ConfigProvider locale={zhCN}>{page}</ConfigProvider>);
}
