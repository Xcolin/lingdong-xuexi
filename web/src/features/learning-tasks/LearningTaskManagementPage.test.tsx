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
  batchPublish: vi.fn(),
  listOrganizations: vi.fn(),
  listStudents: vi.fn(),
  listTeachers: vi.fn()
}));

vi.mock('./api', () => ({ learningTaskApi }));

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
});
