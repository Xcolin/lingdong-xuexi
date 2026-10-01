<template>
  <view class="ld-page has-tabbar">
    <view class="ld-topbar">
      <view class="ld-topbar-brand">
        <view class="ld-topbar-logo">师</view>
        <view>
          <text class="ld-topbar-name">{{ context?.displayName || '教师工作台' }}</text>
          <text class="ld-topbar-meta">{{ context?.username || '' }}</text>
        </view>
      </view>
      <button class="ld-logout" :disabled="loggingOut" @tap="logout">退出</button>
    </view>

    <view v-if="loading" class="ld-loading">正在加载</view>
    <template v-else>
      <view class="ld-heading">
        <text class="ld-heading-title">我的</text>
        <text class="ld-heading-sub">任教班级与账号设置</text>
      </view>
      <view class="ld-card class-card">
        <view v-for="item in context?.classes || []" :key="item.classId" class="class-row">
          <view class="class-badge">班</view>
          <view class="class-main">
            <text class="class-name">{{ item.className }}</text>
            <text class="school-name">{{ item.schoolName }}</text>
          </view>
        </view>
      </view>

      <view class="ld-heading">
        <text class="ld-heading-title">账号</text>
      </view>
      <view class="ld-group">
        <button v-if="accountSecurityEnabled" class="ld-cell" @tap="openSecurity">
          <view class="ld-cell-icon soft">安</view>
          <view class="ld-cell-body">
            <text class="ld-cell-title">账号安全</text>
            <text class="ld-cell-sub">登录设备与安全事件</text>
          </view>
          <text class="ld-cell-arrow">›</text>
        </button>
      </view>
      </view>
    </template>
  <AppTabBar :items="TEACHER_TABS" :active="3" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { TEACHER_TABS } from '@/config/tabbar';
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
.review-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 32rpx;
}
.review-count {
  display: block;
  margin-top: 10rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
}
.review-button {
  width: 132rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  border-radius: $ld-radius-pill;
  background: $ld-primary;
  color: #ffffff;
  font-size: $ld-font-caption;
  font-weight: 600;
  flex-shrink: 0;
}
.review-button::after { border: 0; }
.class-card { padding: 12rpx 28rpx; }
.class-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 26rpx 0;
  border-top: 2rpx solid $ld-line;
}
.class-row:first-child { border-top: 0; }
.class-badge {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18rpx;
  background: $ld-primary-soft;
  color: $ld-primary;
  font-size: 30rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.class-main { min-width: 0; }
.class-name, .school-name { display: block; }
.class-name { color: $ld-text; font-size: 29rpx; font-weight: 600; }
.school-name { margin-top: 6rpx; color: $ld-text-muted; font-size: $ld-font-mini; }
</style>
