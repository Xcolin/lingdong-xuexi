import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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

  it('展示本人作业进度并仅在成功后允许下载', async () => {
    renderPage(<ExportJobManagementPage canRead canCreateOrdinary canSubmitSensitive={false} canReview={false} />);

    expect(await screen.findByText(job.jobCode)).toBeInTheDocument();
    expect(screen.getByText('12 / 12')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: `下载-${job.jobCode}` }));
    expect(exportJobApi.download).toHaveBeenCalledWith(job.id);
    expect(screen.queryByRole('tab', { name: '敏感导出审核' })).not.toBeInTheDocument();
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
