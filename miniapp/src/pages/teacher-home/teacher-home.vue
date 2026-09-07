<template>
  <view class="page-shell">
    <view class="top-bar">
      <view class="identity-area">
        <text class="display-name">{{ context?.displayName || '教师工作台' }}</text>
        <text class="account-text">{{ context?.username || '' }}</text>
      </view>
      <button class="logout-button" :disabled="loggingOut" @tap="logout">退出</button>
    </view>
    <view v-if="loading" class="state-text">正在加载</view>
    <template v-else>
      <view class="section-band">
        <text class="section-title">我的班级</text>
        <view v-for="item in context?.classes || []" :key="item.classId" class="class-row">
          <text class="class-name">{{ item.className }}</text>
          <text class="school-name">{{ item.schoolName }}</text>
        </view>
      </view>
      <button v-if="taskEnabled" class="workbench-button" @tap="openTasks">班级任务</button>
      <button v-if="exceptionReportEnabled" class="workbench-button" @tap="openExceptionReports">异常报备</button>
      <button v-if="attendanceEnabled" class="workbench-button" @tap="openAttendance">考勤台账</button>
      <button v-if="accountSecurityEnabled" class="security-button" @tap="openSecurity">账号安全</button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { logoutOrganization } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { useAttendanceEntry } from '@/composables/use-attendance-entry';
const { attendanceEnabled, openAttendance } = useAttendanceEntry('teacher');
import { getTeacherWorkbenchContext, type TeacherWorkbenchContext } from '@/api/teacher-workbench';
import { clearOrganizationSession, getOrganizationSession } from '@/session/organization-session';

const context = ref<TeacherWorkbenchContext | null>(null);
const loading = ref(true);
const loggingOut = ref(false);
const taskEnabled = ref(false);
const accountSecurityEnabled = ref(false);
const exceptionReportEnabled = ref(false);

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) return leave();
  loading.value = true;
  try {
    const [capabilities, workbench] = await Promise.all([
      getMiniappCapabilities(), getTeacherWorkbenchContext(session.accessToken)
    ]);
    context.value = workbench;
    taskEnabled.value = capabilities.learningTaskManagementEnabled
      && workbench.permissionCodes.includes('LEARNING_TASK_READ_MANAGED');
    exceptionReportEnabled.value = capabilities.studentExceptionReportEnabled === true
      && workbench.permissionCodes.includes('EXCEPTION_REPORT_READ');
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
  } catch {
    await leave();
  } finally {
    loading.value = false;
  }
});

function openTasks(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/managed-tasks/managed-tasks?identity=teacher' });
}

function openExceptionReports(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/exception-reports/exception-reports?identity=teacher' });
}

function openSecurity(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/account-security/account-security?identity=organization' });
}

async function logout(): Promise<void> {
  const session = getOrganizationSession();
  if (!session || loggingOut.value) return;
  loggingOut.value = true;
  try { await logoutOrganization(session.accessToken); } catch { /* 本地会话仍需清理。 */ }
  finally { loggingOut.value = false; await leave(); }
}

async function leave(): Promise<void> {
  clearOrganizationSession();
  await uni.reLaunch({ url: '/pages/index/index' });
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; background: #f4f7f5; }
.top-bar { min-height: 144rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; padding: 32rpx 40rpx; box-sizing: border-box; border-bottom: 2rpx solid #dbe3df; background: #fff; }
.identity-area { min-width: 0; flex: 1; }
.display-name, .account-text, .section-title, .class-name, .school-name { display: block; }
.display-name { color: #1c2b28; font-size: 36rpx; font-weight: 700; }
.account-text, .school-name { margin-top: 8rpx; color: #708078; font-size: 24rpx; }
.logout-button { width: 112rpx; height: 64rpx; margin: 0; background: transparent; color: #9a4b38; font-size: 26rpx; }
.logout-button::after, .workbench-button::after, .security-button::after { border: 0; }
.state-text { min-height: 360rpx; display: flex; align-items: center; justify-content: center; color: #708078; }
.section-band { margin-top: 28rpx; border-block: 2rpx solid #dbe3df; background: #fff; }
.section-title { padding: 28rpx 40rpx; color: #1c2b28; font-size: 30rpx; font-weight: 700; }
.class-row { padding: 24rpx 40rpx; border-top: 2rpx solid #edf1ef; }
.class-name { color: #1c2b28; font-size: 28rpx; }
.workbench-button, .security-button { width: calc(100% - 80rpx); height: 88rpx; margin: 32rpx 40rpx 0; border-radius: 8rpx; background: #167c5a; color: #fff; font-size: 29rpx; }
.security-button { border: 2rpx solid #167c5a; background: #fff; color: #167c5a; }
</style>
