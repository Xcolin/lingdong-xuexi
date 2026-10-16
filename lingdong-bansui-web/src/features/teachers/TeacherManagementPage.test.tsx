vi.mock('../../api/classes',()=>({classApi:{lookup:vi.fn(async(_permission:string,kind:string)=>{const flatten=(nodes:any[],schoolId?:string):any[]=>nodes.flatMap(n=>[{...n,schoolOrganizationId:schoolId},...flatten(n.children??[],['SCHOOL','TRAINING_INSTITUTION'].includes(n.typeCode)?n.id:schoolId)]);const nodes=flatten(await organizationApi.listTree());return nodes.filter(n=>kind==='SCHOOL'?['SCHOOL','TRAINING_INSTITUTION'].includes(n.typeCode):n.typeCode===kind);})}}));
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { act,render,screen,waitFor } from '@testing-library/react';
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
      page: 1, pageSize: 10, total: 1
    });
    vi.mocked(organizationApi.listTree).mockResolvedValue([{
      id: '1874244142494647101', parentId: null, code: 'SCHOOL_1', name: '第一学校',
      typeCode: 'SCHOOL', path: '/1874244142494647101/', sortOrder: 10,
      status: 'ENABLED', effectiveStatus: 'ENABLED', versionNo: 1, adminDivisionCode: null,
      children: [{
        id: '1874244142494647201', parentId: '1874244142494647101', code: 'CLASS_1', name: '一班',
        typeCode: 'CLASS', path: '/1874244142494647101/1874244142494647201/', sortOrder: 10,
        status: 'ENABLED', effectiveStatus: 'ENABLED', versionNo: 1, adminDivisionCode: null, children: []
      }]
    }]);
  });

  it('培训机构作为教师所属根组织参与筛选并沿用schoolId请求', async () => {
    const user = userEvent.setup();
    const nodes = await organizationApi.listTree();
    vi.mocked(organizationApi.listTree).mockResolvedValue(nodes.map(node => ({ ...node, typeCode: 'TRAINING_INSTITUTION', name: '培训中心' })));
    renderPage();
    await screen.findByText('张老师');
    await user.click(screen.getByLabelText('学校'));
    await user.click(screen.getByTitle('培训中心'));
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(teachersApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ schoolId: nodes[0].id, page: 1, pageSize: 10 })));
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

it('用户入口创建教师后已打开教师目录刷新一次',async()=>{
 vi.mocked(teachersApi.list).mockClear();vi.mocked(teachersApi.list).mockResolvedValue({items:[],page:1,pageSize:10,total:0});vi.mocked(organizationApi.listTree).mockResolvedValue([]);
 renderPage();await waitFor(()=>expect(teachersApi.list).toHaveBeenCalledTimes(1));
 const {notifyTeacherProfileChanged}=await import('../users/teacherProfileChanged');act(()=>notifyTeacherProfileChanged());
 await waitFor(()=>expect(teachersApi.list).toHaveBeenCalledTimes(2));
});
