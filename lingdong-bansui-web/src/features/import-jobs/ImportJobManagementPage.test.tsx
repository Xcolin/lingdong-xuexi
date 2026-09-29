import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { importJobApi } from '../../api/import-jobs';
import { studentImportApi } from '../../api/student-imports';
import { classApi } from '../../api/classes';
import { ImportJobManagementPage } from './ImportJobManagementPage';

vi.mock('../../api/import-jobs', () => ({
  importJobApi: { options: vi.fn(), list: vi.fn(), create: vi.fn(), detail: vi.fn(), errors: vi.fn(), downloadError: vi.fn() }
}));
vi.mock('../../api/student-imports', () => ({
  studentImportApi: { list: vi.fn(), create: vi.fn(), rows: vi.fn(), retryFailures: vi.fn(), downloadCredentials: vi.fn() }
}));
vi.mock('../../api/classes', () => ({ classApi: { listClasses: vi.fn() } }));

const job = {
  id: '1874244142494647901', jobCode: 'IMP-20260901-000001',
  templateId: '1874244142494647801', templateVersion: 'V1', templateName: '学生导入模板',
  sourceFileId: '1874244142494647902', errorFileId: '1874244142494647903',
  requesterId: '1874244142494647001', organizationId: null,
  status: 'VALIDATION_FAILED' as const, versionNo: 2, failureCode: null, failureMessage: null,
  totalRows: 2, processedRows: 2, validRows: 1, invalidRows: 1,
  queuedAt: '2026-09-01T10:00:00', startedAt: '2026-09-01T10:00:01',
  completedAt: '2026-09-01T10:00:02', createdAt: '2026-09-01T10:00:00', updatedAt: '2026-09-01T10:00:02'
};

const options = {
  templates: [{ id: job.templateId, label: '学生导入模板（V1）' }],
  organizations: [{ id: '1874244142494647101', label: '第一学校' }]
};

describe('导入校验作业页面', () => {
  beforeEach(() => {
    vi.mocked(importJobApi.options).mockResolvedValue(options);
    vi.mocked(importJobApi.list).mockResolvedValue({ items: [job], page: 1, pageSize: 20, total: 1 });
    vi.mocked(importJobApi.create).mockResolvedValue(job);
    vi.mocked(importJobApi.detail).mockResolvedValue({
      job,
      fields: [{ fieldCode: 'STUDENT_NAME', columnName: '学生姓名', dataType: 'TEXT',
        required: true, maxLength: 50, dictionaryValues: [], sortOrder: 10 }]
    });
    vi.mocked(importJobApi.errors).mockResolvedValue({
      items: [{ id: '1874244142494647911', rowNumber: 2, status: 'INVALID',
        errorSummary: '学生姓名不能为空', createdAt: '2026-09-01T10:00:02' }],
      page: 1, pageSize: 20, total: 1
    });
    vi.mocked(importJobApi.downloadError).mockResolvedValue(new Blob(['error']));
    vi.mocked(studentImportApi.list).mockResolvedValue({ items: [], page: 1, pageSize: 100, total: 0 });
    vi.mocked(studentImportApi.rows).mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0 });
    vi.mocked(classApi.listClasses).mockResolvedValue([]);
  });

  it('加载作业台账并按创建权限显示上传命令', async () => {
    const { rerender } = render(<ConfigProvider locale={zhCN}>
      <ImportJobManagementPage canCreate={false} />
    </ConfigProvider>);
    expect(await screen.findByText('导入校验作业')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新建校验作业' })).not.toBeInTheDocument();

    rerender(<ConfigProvider locale={zhCN}>
      <ImportJobManagementPage canCreate />
    </ConfigProvider>);
    expect(screen.getByRole('button', { name: '新建校验作业' })).toBeInTheDocument();
  });

  it('详情抽屉同时加载字段快照和错误行，但不展示原始单元格值', async () => {
    const user = userEvent.setup();
    render(<ConfigProvider locale={zhCN}><ImportJobManagementPage canCreate /></ConfigProvider>);
    await screen.findByText(job.jobCode);

    await user.click(screen.getByRole('button', { name: `详情-${job.jobCode}` }));
    const drawer = await screen.findByRole('dialog', { name: '作业详情' });
    expect(importJobApi.detail).toHaveBeenCalledWith(job.id);
    expect(importJobApi.errors).toHaveBeenCalledWith(job.id, 1, 20);
    expect(within(drawer).getByText('学生姓名')).toBeInTheDocument();
    expect(within(drawer).getByText('学生姓名不能为空')).toBeInTheDocument();
    expect(within(drawer).getByText('第 2 行')).toBeInTheDocument();
    expect(within(drawer).queryByText('张三')).not.toBeInTheDocument();
  });

  it('创建作业时提交模板、组织和 XLSX 文件', async () => {
    const user = userEvent.setup();
    render(<ConfigProvider locale={zhCN}><ImportJobManagementPage canCreate /></ConfigProvider>);
    await screen.findByText(job.jobCode);
    await user.click(screen.getByRole('button', { name: '新建校验作业' }));
    const dialog = screen.getByRole('dialog', { name: '新建校验作业' });

    const templateSelect = dialog.querySelector('#templateId');
    expect(templateSelect).not.toBeNull();
    fireEvent.mouseDown(templateSelect!);
    await user.click(await screen.findByText('学生导入模板（V1）', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '组织范围' }));
    await user.click(await screen.findByText('第一学校', { selector: '.ant-select-item-option-content' }));
    const file = new File(['xlsx'], 'students.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const fileInput = dialog.querySelector<HTMLInputElement>('input[type="file"]');
    expect(fileInput).not.toBeNull();
    await user.upload(fileInput!, file);
    await user.click(within(dialog).getByRole('button', { name: '提交校验' }));

    await waitFor(() => expect(importJobApi.create).toHaveBeenCalledWith({
      templateId: job.templateId, organizationId: options.organizations[0].id, file
    }));
  });

  it('校验通过后由有权机构管理员执行学员导入并查看一次性凭证状态', async () => {
    const user = userEvent.setup();
    const validated = { ...job, organizationId: options.organizations[0].id,
      status: 'VALIDATED' as const, errorFileId: null, validRows: 2, invalidRows: 0 };
    const execution = {
      id: '1874244142494647991', executionCode: 'SIM-1874244142494647991',
      validationJobId: validated.id, organizationId: validated.organizationId,
      classOrganizationId: null, status: 'QUEUED' as const, versionNo: 0,
      totalRows: 2, processedRows: 0, succeededRows: 0, failedRows: 0,
      failureCode: null, failureMessage: null, credentialStatus: 'NONE' as const,
      credentialExpiresAt: null, credentialDownloadedAt: null,
      queuedAt: validated.queuedAt, startedAt: null, completedAt: null, createdAt: validated.createdAt
    };
    vi.mocked(importJobApi.list).mockResolvedValue({ items: [validated], page: 1, pageSize: 20, total: 1 });
    vi.mocked(importJobApi.detail).mockResolvedValue({ job: validated, fields: [{
      fieldCode: 'STUDENT_NAME', columnName: '学生姓名', dataType: 'TEXT', required: true,
      maxLength: 64, dictionaryValues: [], sortOrder: 10 }] });
    vi.mocked(studentImportApi.create).mockResolvedValue(execution);

    render(<ConfigProvider locale={zhCN}><ImportJobManagementPage
      canCreate canExecuteStudentImport canReadStudentImport canDownloadStudentCredentials
    /></ConfigProvider>);
    await screen.findByText(validated.jobCode);
    await user.click(screen.getByRole('button', { name: `详情-${validated.jobCode}` }));
    const drawer = await screen.findByRole('dialog', { name: '作业详情' });
    await user.click(within(drawer).getByRole('button', { name: '执行学员导入' }));
    await waitFor(() => expect(classApi.listClasses).toHaveBeenCalled());
    const modalTitle = await screen.findByText('执行学员导入', { selector: '.ant-modal-title' });
    const dialog = modalTitle.closest('[role="dialog"]');
    if (!(dialog instanceof HTMLElement)) throw new Error('未找到学员导入执行弹窗');
    await user.click(within(dialog).getByRole('button', { name: '确认执行' }));

    await waitFor(() => expect(studentImportApi.create).toHaveBeenCalledWith({
      validationJobId: validated.id, classOrganizationId: undefined
    }));
    expect(await within(drawer).findByText(execution.executionCode)).toBeInTheDocument();
    expect(within(drawer).getByText('尚未生成')).toBeInTheDocument();
  });
});
