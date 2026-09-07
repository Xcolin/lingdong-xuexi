import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { teachersApi } from '../../api/teachers';
import { organizationApi } from '../../api/organization';
import { TeacherManagementPage } from './TeacherManagementPage';

vi.mock('../../api/teachers', () => ({
  teachersApi: {
    list: vi.fn(), create: vi.fn(), updateProfile: vi.fn(), changeStatus: vi.fn(),
    resetPassword: vi.fn(), bindClass: vi.fn(), unbindClass: vi.fn(), batch: vi.fn()
  }
}));
vi.mock('../../api/organization', () => ({ organizationApi: { listTree: vi.fn() } }));

const permissions = [
  'TEACHER_READ', 'TEACHER_CREATE', 'TEACHER_UPDATE', 'TEACHER_STATUS_CHANGE',
  'TEACHER_PASSWORD_RESET', 'TEACHER_CLASS_ASSIGN', 'TEACHER_BATCH_MANAGE'
];

function renderPage(permissionCodes = permissions) {
  return render(<ConfigProvider locale={zhCN}>
    <TeacherManagementPage permissionCodes={permissionCodes} />
  </ConfigProvider>);
}

describe('教师管理页面', () => {
  beforeEach(() => {
    vi.mocked(teachersApi.list).mockResolvedValue({
      items: [{
        id: '1874244142494647001', username: 'teacher001', displayName: '张老师',
        mobile: '138****8000', status: 'ENABLED', schoolId: '1874244142494647101',
        schoolName: '第一学校', classOrganizationIds: ['1874244142494647201'],
        createdAt: '2026-09-06T09:00:00', updatedAt: '2026-09-06T09:00:00'
      }],
      page: 1, pageSize: 20, total: 1
    });
    vi.mocked(organizationApi.listTree).mockResolvedValue([{
      id: '1874244142494647101', parentId: null, code: 'SCHOOL_1', name: '第一学校',
      typeCode: 'SCHOOL', path: '/1874244142494647101/', sortOrder: 10,
      status: 'ENABLED', effectiveStatus: 'ENABLED', versionNo: 1,
      children: [{
        id: '1874244142494647201', parentId: '1874244142494647101', code: 'CLASS_1', name: '一班',
        typeCode: 'CLASS', path: '/1874244142494647101/1874244142494647201/', sortOrder: 10,
        status: 'ENABLED', effectiveStatus: 'ENABLED', versionNo: 1, children: []
      }]
    }]);
  });

  it('加载教师目录并显示脱敏手机号与学校', async () => {
    renderPage();
    expect(await screen.findByText('张老师')).toBeInTheDocument();
    expect(screen.getByText('138****8000')).toBeInTheDocument();
    expect(screen.getByText('第一学校')).toBeInTheDocument();
  });

  it('按动态权限隐藏新增、编辑、密码和批量操作', async () => {
    renderPage(['TEACHER_READ']);
    await screen.findByText('张老师');
    expect(screen.queryByRole('button', { name: '新增教师' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑教师-张老师' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '重置密码-张老师' })).not.toBeInTheDocument();
    expect(screen.queryByText('批量操作')).not.toBeInTheDocument();
  });

  it('有批量权限时提供稳定的行选择和批量操作入口', async () => {
    renderPage();
    await screen.findByText('张老师');
    expect(screen.getByRole('checkbox', { name: 'Select all' })).toBeInTheDocument();
    expect(screen.getByText('批量操作')).toBeInTheDocument();
  });

  it('新增教师无班级分配权限时不显示初始班级控件', async () => {
    const user = userEvent.setup();
    renderPage(['TEACHER_READ', 'TEACHER_CREATE']);
    await screen.findByText('张老师');

    await user.click(screen.getByRole('button', { name: '新增教师' }));

    expect(screen.getByText('新增教师', { selector: '.ant-modal-title' })).toBeInTheDocument();
    expect(screen.queryByText('初始班级')).not.toBeInTheDocument();
  });

  it('缺少基础操作权限时不显示空壳批量入口', async () => {
    renderPage(['TEACHER_READ', 'TEACHER_BATCH_MANAGE']);
    await screen.findByText('张老师');

    expect(screen.queryByRole('checkbox', { name: 'Select all' })).not.toBeInTheDocument();
    expect(screen.queryByText('批量操作')).not.toBeInTheDocument();
  });
});
