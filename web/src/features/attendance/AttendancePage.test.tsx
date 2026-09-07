import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { authApi, type CurrentUser } from '../../api/auth';
import { capabilityApi, type ClientCapabilities } from '../../api/capability';
import { ApiRequestError } from '../../api/http';
import { attendanceApi, type AttendanceRecord, type AttendanceResult } from './api';
import { AttendancePage } from './AttendancePage';

vi.mock('../../api/auth', () => ({ authApi: { currentUser: vi.fn() } }));
vi.mock('../../api/capability', () => ({ capabilityApi: { web: vi.fn() } }));
vi.mock('./api', () => ({ attendanceApi: { list: vi.fn(), classes: vi.fn(), roster: vi.fn(), batch: vi.fn(), details: vi.fn() } }));
const user: CurrentUser = { userId: '1874244142494647001', sessionId: '2', username: 'test', displayName: '老师', clientType: 'WEB', roleCodes: ['CUSTOM'], permissionCodes: ['ATTENDANCE_READ', 'ATTENDANCE_RECORD'] };
const record: AttendanceRecord = { id: '1874244142494647101', studentId: '1874244142494647201', studentName: '小明', classOrganizationId: '1874244142494647301', className: '历史一班', attendanceDate: '2026-09-01', status: 'LATE', checkinTime: '09:00:00', checkoutTime: null, source: 'MANUAL', recordedBy: user.userId, recorderName: '王老师', versionNo: 3, createdAt: '2026-09-01T09:00:00', updatedAt: '2026-09-01T10:00:00' };
function mount() { return render(<ConfigProvider locale={zhCN}><AttendancePage currentUser={user} /></ConfigProvider>); }
async function choose(label: string, value: string) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: label }));
  fireEvent.click(await screen.findByText(value, { selector: '.ant-select-item-option-content' }));
}
describe('考勤台账', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(authApi.currentUser).mockResolvedValue(user);
    vi.mocked(capabilityApi.web).mockResolvedValue({ attendanceManagementEnabled: true } as ClientCapabilities);
    vi.mocked(attendanceApi.list).mockResolvedValue({ items: [record], page: 1, pageSize: 20, total: 21 });
    vi.mocked(attendanceApi.classes).mockImplementation(async (operational) => [{ classOrganizationId: record.classOrganizationId, className: operational ? '可点名一班' : '历史一班' }]);
    vi.mocked(attendanceApi.roster).mockResolvedValue([{ studentId: record.studentId, studentName: record.studentName, record }, { studentId: '1874244142494647202', studentName: '小红', record: null }]);
    vi.mocked(attendanceApi.batch).mockResolvedValue([record]);
    vi.mocked(attendanceApi.details).mockResolvedValue({ record, actions: [{ id: '1874244142494647401', actionType: 'CORRECT', operatorUserId: user.userId, operatorName: '王老师', beforeStatus: 'NORMAL', afterStatus: 'LATE', beforeCheckinTime: '08:00:00', afterCheckinTime: '09:00:00', beforeCheckoutTime: null, afterCheckoutTime: null, createdAt: '2026-09-01T10:00:00' }] });
  });
  it.each(['PARENT', 'STUDENT'])('%s 只读且不请求操作班级', async (role) => {
    vi.mocked(authApi.currentUser).mockResolvedValue({ ...user, roleCodes: [role] });
    mount(); await screen.findByText('小明');
    expect(screen.queryByRole('button', { name: '班级点名' })).not.toBeInTheDocument();
    expect(attendanceApi.classes).not.toHaveBeenCalledWith(true);
  });
  it('直达重新检查能力，关闭时不加载任何业务数据', async () => {
    vi.mocked(capabilityApi.web).mockResolvedValue({ attendanceManagementEnabled: false } as ClientCapabilities);
    mount(); await screen.findByText('考勤功能已停用或无访问权限');
    expect(attendanceApi.list).not.toHaveBeenCalled();
  });
  it('组合筛选、翻页和重置使用同一组查询条件', async () => {
    mount(); await screen.findByText('小明');
    await choose('筛选班级', '历史一班'); await choose('筛选状态', '迟到');
    fireEvent.change(screen.getByLabelText('学生关键字'), { target: { value: ' 小明 ' } });
    fireEvent.change(screen.getByLabelText('学生ID'), { target: { value: record.studentId } });
    fireEvent.change(screen.getByLabelText('开始日期'), { target: { value: '2026-09-01' } });
    fireEvent.change(screen.getByLabelText('结束日期'), { target: { value: '2026-09-07' } });
    fireEvent.click(screen.getByRole('button', { name: '查询' }));
    const query = { classOrganizationId: record.classOrganizationId, studentId: record.studentId, keyword: '小明', status: 'LATE', dateFrom: '2026-09-01', dateTo: '2026-09-07', page: 1, pageSize: 20 };
    await waitFor(() => expect(attendanceApi.list).toHaveBeenLastCalledWith(query));
    fireEvent.click(screen.getByTitle('2'));
    await waitFor(() => expect(attendanceApi.list).toHaveBeenLastCalledWith({ ...query, page: 2 }));
    fireEvent.click(screen.getByRole('button', { name: '重置' }));
    await waitFor(() => expect(attendanceApi.list).toHaveBeenLastCalledWith({ page: 1, pageSize: 20 }));
  });
  it('名单不默认勾选已有记录，显式选择后单请求携带版本', async () => {
    mount(); await screen.findByText('小明');
    fireEvent.click(screen.getByRole('button', { name: '班级点名' }));
    await choose('点名班级', '可点名一班');
    fireEvent.change(screen.getByLabelText('考勤日期'), { target: { value: '2026-09-01' } });
    await screen.findByText('小红');
    expect(screen.getByRole('button', { name: '提交考勤' })).toBeDisabled();
    await choose('小明的考勤状态', '正常');
    fireEvent.click(screen.getByRole('button', { name: '提交考勤' }));
    await waitFor(() => expect(attendanceApi.batch).toHaveBeenCalledExactlyOnceWith({ classOrganizationId: record.classOrganizationId, attendanceDate: '2026-09-01', items: [{ studentId: record.studentId, status: 'NORMAL', checkinTime: '09:00:00', checkoutTime: null, versionNo: 3 }] }));
  });
  it('版本冲突保留输入，提供重新加载名单命令', async () => {
    vi.mocked(attendanceApi.batch).mockRejectedValue(new ApiRequestError(409, 'ATTENDANCE_VERSION_CONFLICT', '考勤记录已被更新，请刷新后重试'));
    mount(); await screen.findByText('小明'); fireEvent.click(screen.getByRole('button', { name: '班级点名' }));
    await choose('点名班级', '可点名一班'); await screen.findByText('小红');
    await choose('小红的考勤状态', '缺勤');
    fireEvent.click(screen.getByRole('button', { name: '提交考勤' }));
    await screen.findByText('考勤记录已被更新，请刷新后重试');
    expect(screen.getByRole('button', { name: '重新加载名单' })).toBeEnabled();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
  it('历史详情展示变更前后状态、时分时间与操作者', async () => {
    mount(); await screen.findByText('小明'); fireEvent.click(screen.getByRole('button', { name: '查看小明的考勤详情' }));
    const drawer = await screen.findByRole('dialog');
    await within(drawer).findByText('更正');
    expect(within(drawer).getByText('正常 → 迟到')).toBeInTheDocument();
    expect(within(drawer).getByText('08:00:00 → 09:00:00')).toBeInTheDocument();
    expect(attendanceApi.details).toHaveBeenCalledWith(record.id);
  });
  it('新查询完成后旧请求不可覆盖，停用后晚到响应不可恢复缓存', async () => {
    let resolveOld!: (result: AttendanceResult) => void;
    vi.mocked(attendanceApi.list).mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }));
    mount(); await screen.findByRole('heading', { name: '考勤台账' });
    fireEvent.click(screen.getByRole('button', { name: '查询' }));
    await screen.findByText('小明');
    await act(async () => resolveOld({ items: [{ ...record, studentName: '旧数据' }], total: 1, page: 1, pageSize: 20 }));
    expect(screen.queryByText('旧数据')).not.toBeInTheDocument();
    vi.mocked(capabilityApi.web).mockResolvedValue({ attendanceManagementEnabled: false } as ClientCapabilities);
    fireEvent.focus(window);
    await screen.findByText('考勤功能已停用或无访问权限');
    expect(screen.queryByText('小明')).not.toBeInTheDocument();
  });
});
