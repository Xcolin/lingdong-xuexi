import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaskTemplateLibraryModal } from './TaskTemplateLibraryModal';

const taskTemplateApi = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  reorder: vi.fn()
}));

vi.mock('./taskTemplateApi', () => ({ taskTemplateApi }));

const personalTemplates = [
  {
    id: '1874244142494647602', templateScope: 'PERSONAL' as const,
    templateName: '周末口算', taskTitle: '口算练习', difficultyLevel: 1,
    durationMinutes: 20, categoryCode: 'MATH', tagCodes: ['WEEKEND'], remark: null,
    sortOrder: 10, versionNo: 1, createdAt: null, updatedAt: null
  },
  {
    id: '1874244142494647603', templateScope: 'PERSONAL' as const,
    templateName: '晚间阅读', taskTitle: '阅读练习', difficultyLevel: 2,
    durationMinutes: 30, categoryCode: 'READING', tagCodes: ['DAILY'], remark: null,
    sortOrder: 20, versionNo: 3, createdAt: null, updatedAt: null
  }
];

describe('个人任务模板库', () => {
  beforeEach(() => {
    taskTemplateApi.list.mockResolvedValue(personalTemplates);
    taskTemplateApi.create.mockResolvedValue(personalTemplates[0]);
    taskTemplateApi.update.mockResolvedValue(personalTemplates[0]);
    taskTemplateApi.remove.mockResolvedValue(undefined);
    taskTemplateApi.reorder.mockResolvedValue(personalTemplates);
  });

  it('新增个人模板时提交可复用任务字段', async () => {
    const user = userEvent.setup();
    render(<TaskTemplateLibraryModal open onClose={vi.fn()} onSelect={vi.fn()} />);

    await user.click(await screen.findByRole('tab', { name: '个人模板（2）' }));
    await user.click(screen.getByRole('button', { name: '新建个人模板' }));
    await user.type(screen.getByLabelText('模板名称'), '每日英语');
    await user.type(screen.getByLabelText('任务标题'), '英语朗读');
    const createDialogs = screen.getAllByRole('dialog');
    await user.click(within(createDialogs[createDialogs.length - 1]).getByRole(
      'button', { name: /保\s*存/ }
    ));

    await waitFor(() => expect(taskTemplateApi.create).toHaveBeenCalledWith({
      templateName: '每日英语',
      taskTitle: '英语朗读',
      difficultyLevel: 1,
      durationMinutes: 30,
      categoryCode: 'GENERAL',
      tagCodes: ['DAILY'],
      remark: undefined
    }));
  });

  it('编辑和删除时携带当前版本号', async () => {
    const user = userEvent.setup();
    render(<TaskTemplateLibraryModal open onClose={vi.fn()} onSelect={vi.fn()} />);

    await user.click(await screen.findByRole('tab', { name: '个人模板（2）' }));
    await user.click(screen.getByRole('button', { name: '编辑 周末口算' }));
    const nameInput = screen.getByLabelText('模板名称');
    await user.clear(nameInput);
    await user.type(nameInput, '每日口算');
    const editDialogs = screen.getAllByRole('dialog');
    await user.click(within(editDialogs[editDialogs.length - 1]).getByRole(
      'button', { name: /保\s*存/ }
    ));
    await waitFor(() => expect(taskTemplateApi.update).toHaveBeenCalledWith(
      '1874244142494647602', 1, expect.objectContaining({ templateName: '每日口算' })
    ));

    await user.click(screen.getByRole('button', { name: '删除 周末口算' }));
    await user.click(await screen.findByRole('button', { name: '确认删除' }));
    await waitFor(() => expect(taskTemplateApi.remove).toHaveBeenCalledWith(
      '1874244142494647602', 1
    ));
  });

  it('排序提交当前家长全部个人模板及版本', async () => {
    const user = userEvent.setup();
    render(<TaskTemplateLibraryModal open onClose={vi.fn()} onSelect={vi.fn()} />);

    await user.click(await screen.findByRole('tab', { name: '个人模板（2）' }));
    await user.click(screen.getByRole('button', { name: '下移 周末口算' }));

    await waitFor(() => expect(taskTemplateApi.reorder).toHaveBeenCalledWith([
      { templateId: '1874244142494647603', versionNo: 3 },
      { templateId: '1874244142494647602', versionNo: 1 }
    ]));
  });
});
