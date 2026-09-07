import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { getAttendanceUser, type AttendanceIdentity } from '@/api/attendance';
import { getOrganizationSession } from '@/session/organization-session';
import { getParentSession } from '@/session/parent-session';
import { getStudentSession } from '@/session/student-session';

/** 每次返回首页都复核开关和当前身份权限，网络失败时隐藏入口。 */
export function useAttendanceEntry(identity: AttendanceIdentity) {
  const attendanceEnabled = ref(false);
  let revision = 0;
  onShow(async () => {
    const current = ++revision;
    attendanceEnabled.value = false;
    const session = identity === 'parent' ? getParentSession()
      : identity === 'student' ? getStudentSession() : getOrganizationSession();
    if (!session) return;
    try {
      const capabilities = await getMiniappCapabilities();
      if (current !== revision || capabilities.attendanceManagementEnabled !== true) return;
      const user = await getAttendanceUser(session.accessToken);
      if (current === revision) attendanceEnabled.value = !user.roleCodes.includes('SYS_AUDITOR')
        && user.permissionCodes.includes('ATTENDANCE_READ');
    } catch { if (current === revision) attendanceEnabled.value = false; }
  });
  function openAttendance() {
    if (attendanceEnabled.value) void uni.navigateTo({ url: `/pages/attendance/attendance?identity=${identity}` });
  }
  return { attendanceEnabled, openAttendance };
}
