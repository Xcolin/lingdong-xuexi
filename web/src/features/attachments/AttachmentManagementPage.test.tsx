import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { attachmentManagementApi } from '../../api/attachment-management';
import { AttachmentManagementPage } from './AttachmentManagementPage';

vi.mock('../../api/attachment-management', () => ({
  attachmentManagementApi: {
    listRules: vi.fn(), createRule: vi.fn(), updateRule: vi.fn(), enableRule: vi.fn(), disableRule: vi.fn(),
    listFiles: vi.fn(), listRelations: vi.fn()
  }
}));

const rule = {
  id: '1874244142494647601', moduleCode: 'LEARNING_TASK_CHECKIN', fileCategory: 'IMAGE',
  ruleName: '任务打卡图片', allowedExtensions: ['jpg', 'png'], maxFileSizeBytes: 10485760,
  maxBatchCount: 9, previewEnabled: true, status: 'ENABLED' as const, versionNo: 0
};
const file = {
  id: '1874244142494647602', originalName: '阅读打卡.jpg', extension: 'jpg', contentType: 'image/jpeg',
  sizeBytes: 2048, uploaderId: '1874244142494646201', uploaderName: '测试学生',
  moduleCode: 'LEARNING_TASK_CHECKIN', fileCategory: 'IMAGE', contentSha256Present: true,
  status: 'AVAILABLE' as const, uploadedAt: '2026-08-31T12:00:00',
  createdAt: '2026-08-31T11:59:00', updatedAt: '2026-08-31T12:00:00'
};
const relation = {
  id: '1874244142494647603', fileId: file.id, moduleCode: 'LEARNING_TASK_CHECKIN',
  businessId: '1874244142494647604', relationType: 'IMAGE', visibleScope: 'BUSINESS_AUTHORIZED',
  status: 'ACTIVE' as const, createdAt: '2026-08-31T12:01:00', releasedAt: null
};

function renderPage(canReadRules = true, canManage = true, canReadFiles = true) {
  return render(<ConfigProvider locale={zhCN}>
    <AttachmentManagementPage canReadRules={canReadRules} canManage={canManage} canReadFiles={canReadFiles} />
  </ConfigProvider>);
}

describe('附件管理页面', () => {
  beforeEach(() => {
    vi.mocked(attachmentManagementApi.listRules).mockResolvedValue([rule]);
    vi.mocked(attachmentManagementApi.listFiles).mockResolvedValue([file]);
    vi.mocked(attachmentManagementApi.listRelations).mockResolvedValue([relation]);
    vi.mocked(attachmentManagementApi.createRule).mockResolvedValue(rule);
    vi.mocked(attachmentManagementApi.updateRule).mockResolvedValue({ ...rule, ruleName: '任务凭证图片', versionNo: 1 });
    vi.mocked(attachmentManagementApi.disableRule).mockResolvedValue({ ...rule, status: 'DISABLED', versionNo: 1 });
    vi.mocked(attachmentManagementApi.enableRule).mockResolvedValue({ ...rule, status: 'ENABLED', versionNo: 2 });
  });

  it('展示规则并在只读状态隐藏所有写操作', async () => {
    renderPage(true, false, false);
    expect(await screen.findByText('任务打卡图片')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增附件规则' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /编辑-/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: '文件台账' })).not.toBeInTheDocument();
  });

  it('新增、编辑并停用规则后刷新台账', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('任务打卡图片');

    await user.click(screen.getByRole('button', { name: '新增附件规则' }));
    let dialog = screen.getByRole('dialog', { name: '新增附件规则' });
    await user.type(within(dialog).getByLabelText('模块编码'), 'COURSEWORK');
    await user.type(within(dialog).getByLabelText('文件分类'), 'DOCUMENT');
    await user.type(within(dialog).getByLabelText('规则名称'), '课程材料');
    await user.type(within(dialog).getByLabelText('允许扩展名'), 'pdf,docx');
    fireEvent.change(within(dialog).getByLabelText('单文件上限（MB）'), { target: { value: '20' } });
    fireEvent.change(within(dialog).getByLabelText('单批数量'), { target: { value: '5' } });
    await user.click(within(dialog).getByRole('button', { name: '保存规则' }));
    await waitFor(() => expect(attachmentManagementApi.createRule).toHaveBeenCalledWith({
      moduleCode: 'COURSEWORK', fileCategory: 'DOCUMENT', ruleName: '课程材料',
      allowedExtensions: ['pdf', 'docx'], maxFileSizeBytes: 20 * 1024 * 1024,
      maxBatchCount: 5, previewEnabled: true
    }));

    await user.click(screen.getByRole('button', { name: '编辑-任务打卡图片' }));
    dialog = screen.getByRole('dialog', { name: '编辑附件规则' });
    const nameInput = within(dialog).getByLabelText('规则名称');
    await user.clear(nameInput);
    await user.type(nameInput, '任务凭证图片');
    await user.click(within(dialog).getByRole('button', { name: '保存规则' }));
    await waitFor(() => expect(attachmentManagementApi.updateRule).toHaveBeenCalledWith(
      rule.id, expect.objectContaining({ ruleName: '任务凭证图片', versionNo: 0 })
    ));

    await user.click(screen.getByRole('button', { name: '停用-任务打卡图片' }));
    await waitFor(() => expect(attachmentManagementApi.disableRule).toHaveBeenCalledWith(rule.id, 0));
    expect(attachmentManagementApi.listRules).toHaveBeenCalledTimes(4);
  });

  it('筛选文件并在抽屉查看完整关系历史', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('任务打卡图片');
    await user.click(screen.getByRole('tab', { name: '文件台账' }));
    expect(await screen.findByText('阅读打卡.jpg')).toBeInTheDocument();

    await user.type(screen.getByLabelText('文件名称'), '阅读');
    await user.click(screen.getByRole('button', { name: '查询文件' }));
    await waitFor(() => expect(attachmentManagementApi.listFiles)
      .toHaveBeenLastCalledWith(expect.objectContaining({ originalName: '阅读' })));

    await user.click(screen.getByRole('button', { name: '查看关系-阅读打卡.jpg' }));
    expect(await screen.findByText('1874244142494647604')).toBeInTheDocument();
    expect(screen.getByText('活动')).toBeInTheDocument();
  });

  it('加载失败后可重试', async () => {
    vi.mocked(attachmentManagementApi.listRules)
      .mockRejectedValueOnce(new Error('网络异常'))
      .mockResolvedValueOnce([rule]);
    renderPage(true, false, false);
    expect(await screen.findByText('网络异常')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText('任务打卡图片')).toBeInTheDocument();
    expect(attachmentManagementApi.listRules).toHaveBeenCalledTimes(2);
  });
});
