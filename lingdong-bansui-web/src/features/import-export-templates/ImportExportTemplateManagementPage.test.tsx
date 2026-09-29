import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { importExportTemplateApi } from '../../api/import-export-templates';
import { ApiRequestError } from '../../api/http';
import { ImportExportTemplateManagementPage } from './ImportExportTemplateManagementPage';

vi.mock('../../api/import-export-templates', () => ({
  importExportTemplateApi: {
    options: vi.fn(), list: vi.fn(), create: vi.fn(), enable: vi.fn(),
    disable: vi.fn(), setDefault: vi.fn(), download: vi.fn(), fields: vi.fn(), replaceFields: vi.fn()
  }
}));

const options = {
  templateTypes: [
    { code: 'IMPORT', name: '导入', defaultItem: true },
    { code: 'EXPORT', name: '导出', defaultItem: false }
  ],
  modules: [
    { code: 'STUDENT', name: '学生', defaultItem: true },
    { code: 'LEARNING_TASK', name: '学习任务', defaultItem: false }
  ],
  statuses: [
    { code: 'ENABLED', name: '启用', defaultItem: true },
    { code: 'DISABLED', name: '停用', defaultItem: false }
  ]
};

const template = {
  id: '1874244142494647801', templateName: '学生导入模板', templateType: 'IMPORT' as const,
  moduleCode: 'STUDENT', version: 'V1', fileId: '1874244142494647802', fileName: 'students.xlsx',
  contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', sizeBytes: 2048,
  defaultTemplate: false, status: 'ENABLED' as const, versionNo: 0,
  createdAt: '2026-09-01T10:00:00', updatedAt: '2026-09-01T10:00:00'
};

const disabledTemplate = {
  ...template,
  id: '1874244142494647811',
  templateName: '历史学生导入模板',
  status: 'DISABLED' as const,
  versionNo: 3
};

const templateFields = [{
  id: '1874244142494647821', templateId: disabledTemplate.id,
  fieldCode: 'STUDENT_NAME', columnName: '学生姓名', dataType: 'TEXT' as const,
  required: true, maxLength: 50, sortOrder: 10
}];

function renderPage(canManage = true) {
  return render(<ConfigProvider locale={zhCN}>
    <ImportExportTemplateManagementPage canManage={canManage} />
  </ConfigProvider>);
}

describe('导入导出模板管理页面', () => {
  beforeEach(() => {
    vi.mocked(importExportTemplateApi.options).mockResolvedValue(options);
    vi.mocked(importExportTemplateApi.list).mockResolvedValue([template]);
    vi.mocked(importExportTemplateApi.create).mockResolvedValue(template);
    vi.mocked(importExportTemplateApi.enable).mockResolvedValue({
      ...template, status: 'ENABLED', versionNo: 2
    });
    vi.mocked(importExportTemplateApi.disable).mockResolvedValue({
      ...template, status: 'DISABLED', versionNo: 1
    });
    vi.mocked(importExportTemplateApi.setDefault).mockResolvedValue({
      ...template, defaultTemplate: true, versionNo: 1
    });
    vi.mocked(importExportTemplateApi.download).mockResolvedValue(new Blob(['template']));
    vi.mocked(importExportTemplateApi.fields).mockResolvedValue(templateFields);
    vi.mocked(importExportTemplateApi.replaceFields).mockResolvedValue({
      versionNo: 4, fields: templateFields
    });
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:template') });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
  });

  it('加载选项和台账，支持组合筛选与重置', async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('学生导入模板')).toBeInTheDocument();
    expect(importExportTemplateApi.options).toHaveBeenCalledTimes(1);
    expect(importExportTemplateApi.list).toHaveBeenCalledWith({});

    await user.type(screen.getByLabelText('模板名称'), '学生');
    fireEvent.mouseDown(screen.getByLabelText('模板类型'));
    await user.click(await screen.findByText('导入', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(screen.getByLabelText('适用模块'));
    await user.click(await screen.findByText('学生', { selector: '.ant-select-item-option-content' }));
    await user.click(screen.getByRole('button', { name: '查询模板' }));
    await waitFor(() => expect(importExportTemplateApi.list).toHaveBeenLastCalledWith({
      templateName: '学生', templateType: 'IMPORT', moduleCode: 'STUDENT'
    }));

    await user.click(screen.getByRole('button', { name: '重置筛选' }));
    await waitFor(() => expect(importExportTemplateApi.list).toHaveBeenLastCalledWith({}));
  });

  it('选择单个文件并新增模板版本', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('学生导入模板');
    await user.click(screen.getByRole('button', { name: '新增模板版本' }));
    const dialog = screen.getByRole('dialog', { name: '新增模板版本' });
    await user.type(within(dialog).getByLabelText('模板名称'), '任务导入模板');
    fireEvent.mouseDown(within(dialog).getByLabelText('模板类型'));
    await user.click(await screen.findByText('导入', { selector: '.ant-select-item-option-content' }));
    expect(within(dialog).getByText('字段映射')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('字段编码-1'), 'TASK_NAME');
    await user.type(within(dialog).getByLabelText('表头名称-1'), '任务名称');
    fireEvent.mouseDown(within(dialog).getByLabelText('数据类型-1'));
    await user.click(await screen.findByText('文本', { selector: '.ant-select-item-option-content' }));
    await user.clear(within(dialog).getByLabelText('最大长度-1'));
    await user.type(within(dialog).getByLabelText('最大长度-1'), '100');
    fireEvent.mouseDown(within(dialog).getByLabelText('适用模块'));
    await user.click(await screen.findByText('学习任务', { selector: '.ant-select-item-option-content' }));
    await user.type(within(dialog).getByLabelText('模板版本'), 'V2');
    const file = new File(['task,name'], 'tasks.csv', { type: 'text/csv' });
    const fileInput = dialog.querySelector('input[type="file"]');
    expect(fileInput).not.toBeNull();
    fireEvent.change(fileInput!, { target: { files: [file] } });
    await user.click(within(dialog).getByRole('button', { name: '保存模板' }));

    await waitFor(() => expect(importExportTemplateApi.create).toHaveBeenCalledWith({
      templateName: '任务导入模板', templateType: 'IMPORT', moduleCode: 'LEARNING_TASK',
      version: 'V2', defaultTemplate: false, file,
      fields: [{ fieldCode: 'TASK_NAME', columnName: '任务名称', dataType: 'TEXT',
        required: false, maxLength: 100, sortOrder: 10 }]
    }));
    expect(importExportTemplateApi.list).toHaveBeenCalledTimes(2);
  });

  it('停用导入模板可以在字段抽屉中整体保存映射', async () => {
    const user = userEvent.setup();
    vi.mocked(importExportTemplateApi.list).mockResolvedValue([disabledTemplate]);
    renderPage();
    await screen.findByText('历史学生导入模板');

    await user.click(screen.getByRole('button', { name: '字段映射-历史学生导入模板' }));
    expect(await screen.findByRole('dialog', { name: '历史学生导入模板字段映射' })).toBeInTheDocument();
    expect(importExportTemplateApi.fields).toHaveBeenCalledWith(disabledTemplate.id);

    const drawer = screen.getByRole('dialog', { name: '历史学生导入模板字段映射' });
    await user.clear(within(drawer).getByLabelText('表头名称-1'));
    await user.type(within(drawer).getByLabelText('表头名称-1'), '姓名');
    await user.click(within(drawer).getByRole('button', { name: '保存字段映射' }));

    await waitFor(() => expect(importExportTemplateApi.replaceFields).toHaveBeenCalledWith(
      disabledTemplate.id,
      3,
      [{ fieldCode: 'STUDENT_NAME', columnName: '姓名', dataType: 'TEXT',
        required: true, maxLength: 50, dictionaryTypeCode: undefined, sortOrder: 10 }]
    ));
    await waitFor(() => expect(importExportTemplateApi.list).toHaveBeenCalledTimes(2));
  });

  it('启用模板字段映射只读，不显示保存命令', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('学生导入模板');

    await user.click(screen.getByRole('button', { name: '字段映射-学生导入模板' }));
    const drawer = await screen.findByRole('dialog', { name: '学生导入模板字段映射' });
    expect(within(drawer).getByLabelText('字段编码-1')).toBeDisabled();
    expect(within(drawer).queryByRole('button', { name: '保存字段映射' })).not.toBeInTheDocument();
  });

  it('首次打开新增弹窗时不会操作尚未挂载的表单实例', async () => {
    const user = userEvent.setup();
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    renderPage();
    await screen.findByText('学生导入模板');

    await user.click(screen.getByRole('button', { name: '新增模板版本' }));
    await new Promise((resolve) => setTimeout(resolve, 150));

    expect(screen.getByRole('dialog', { name: '新增模板版本' })).toBeInTheDocument();
    expect(consoleError.mock.calls.flat().join(' '))
      .not.toContain('Instance created by `useForm` is not connected');
    consoleError.mockRestore();
  });

  it('只读用户不显示变更命令但可以下载模板', async () => {
    const user = userEvent.setup();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    renderPage(false);
    await screen.findByText('学生导入模板');
    expect(screen.queryByRole('button', { name: '新增模板版本' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '设为默认-学生导入模板' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '停用-学生导入模板' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '下载-学生导入模板' }));
    await waitFor(() => expect(importExportTemplateApi.download).toHaveBeenCalledWith(template.id));
    expect(click).toHaveBeenCalled();
  });

  it('执行设默认和停用，遇到版本冲突时刷新列表', async () => {
    const user = userEvent.setup();
    vi.mocked(importExportTemplateApi.setDefault)
      .mockRejectedValueOnce(new ApiRequestError(409, 'CONFLICT', '模板版本已变化'));
    renderPage();
    await screen.findByText('学生导入模板');

    await user.click(screen.getByRole('button', { name: '设为默认-学生导入模板' }));
    await waitFor(() => expect(importExportTemplateApi.setDefault)
      .toHaveBeenCalledWith(template.id, template.versionNo));
    await waitFor(() => expect(importExportTemplateApi.list).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole('button', { name: '停用-学生导入模板' }));
    await waitFor(() => expect(importExportTemplateApi.disable)
      .toHaveBeenCalledWith(template.id, template.versionNo));
    await waitFor(() => expect(importExportTemplateApi.list).toHaveBeenCalledTimes(3));
  });

  it('加载失败后可以重试', async () => {
    vi.mocked(importExportTemplateApi.options)
      .mockRejectedValueOnce(new Error('网络异常'))
      .mockResolvedValueOnce(options);
    renderPage();
    expect(await screen.findByText('网络异常')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText('学生导入模板')).toBeInTheDocument();
    expect(importExportTemplateApi.options).toHaveBeenCalledTimes(2);
  });
});
