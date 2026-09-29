<template>
  <view class="page-shell">
    <view class="top-bar">
      <view class="identity-area">
        <text class="display-name">{{ context?.displayName || '机构工作台' }}</text>
        <text class="account-text">{{ context?.username || '' }}</text>
      </view>
      <button class="logout-button" :disabled="loggingOut" @tap="logout">退出</button>
    </view>

      <view v-if="reviewEnabled || reviewLoading || reviewError" class="review-summary">
        <text v-if="reviewLoading">正在加载待审核任务…</text>
        <template v-else-if="reviewError"><text>{{ reviewError }}</text><button @tap="refreshReviews">重试待审核查询</button></template>
        <text v-else>{{ reviewTotal === 0 ? '暂无待审核任务' : `待审核任务：${reviewTotal} 项` }}</text>
        <button v-if="reviewEnabled && !reviewLoading" @tap="openReviews">查看待审核任务</button>
      </view>
    <OrganizationExceptionSummary />
    <view v-if="loading" class="state-text">正在加载</view>
    <template v-else>
      <view class="section-band">
        <text class="section-title">管理组织</text>
        <view v-for="organization in context?.organizations || []" :key="organization.id" class="organization-row">
          <view class="organization-main">
            <text class="organization-name">{{ organization.name }}</text>
            <text class="organization-type">{{ organizationTypeName(organization.typeCode) }}</text>
          </view>
        </view>
      </view>
      <button v-if="studentRelationshipEnabled" class="workbench-button" @tap="openStudentRelationships">学员关系</button>
      <button v-if="classManagementEnabled" class="workbench-button" @tap="openClassManagement">班级管理</button>
      <button v-if="teacherManagementEnabled" class="workbench-button" @tap="openTeacherManagement">教师管理</button>
      <button v-if="taskManagementEnabled" class="workbench-button" @tap="openTaskManagement">机构任务</button>
      <button v-if="exceptionReportEnabled" class="workbench-button" @tap="openExceptionReports">异常报备</button>
      <button v-if="attendanceEnabled" class="workbench-button" @tap="openAttendance">考勤台账</button>
      <button v-if="parentMobileManualRecoveryEnabled" class="recovery-button" @tap="openParentMobileRecovery">家长换号核验</button>
      <button v-if="studentAccountCancellationEnabled" class="cancellation-button" @tap="openStudentCancellation">学生账号注销</button>
      <button v-if="accountSecurityEnabled" class="security-button" @tap="openAccountSecurity">账号安全</button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import OrganizationExceptionSummary from '@/components/OrganizationExceptionSummary.vue';
import { useManagedReviewSummary } from '@/composables/use-managed-review-summary';
const { reviewEnabled, reviewLoading, reviewTotal, reviewError, refreshReviews, openReviews } = useManagedReviewSummary('organization');
import { onShow } from '@dcloudio/uni-app';
import { logoutOrganization } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { useAttendanceEntry } from '@/composables/use-attendance-entry';
const { attendanceEnabled, openAttendance } = useAttendanceEntry('organization');
import { ApiError } from '@/api/http';
import {
  getOrganizationWorkbenchContext,
  type OrganizationWorkbenchContext
} from '@/api/organization-workbench';
import {
  clearOrganizationSession,
  getOrganizationSession
} from '@/session/organization-session';

const context = ref<OrganizationWorkbenchContext | null>(null);
const loading = ref(true);
const loggingOut = ref(false);
const accountSecurityEnabled = ref(false);
const studentRelationshipEnabled = ref(false);
const classManagementEnabled = ref(false);
const teacherManagementEnabled = ref(false);
const taskManagementEnabled = ref(false);
const exceptionReportEnabled = ref(false);
const parentMobileManualRecoveryEnabled = ref(false);
const studentAccountCancellationEnabled = ref(false);

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) return leaveWorkbench();
  loading.value = true;
  try {
    const [capabilities, workbench] = await Promise.all([
      getMiniappCapabilities(),
      getOrganizationWorkbenchContext(session.accessToken)
    ]);
    if (!capabilities.organizationMiniappAuthEnabled) return leaveWorkbench();
    context.value = workbench;
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
    studentRelationshipEnabled.value = capabilities.studentOrganizationRelationshipEnabled;
    classManagementEnabled.value = capabilities.classManagementEnabled === true;
    teacherManagementEnabled.value = capabilities.teacherManagementEnabled === true
      && workbench.permissionCodes.includes('TEACHER_READ');
    taskManagementEnabled.value = capabilities.learningTaskManagementEnabled
      && workbench.permissionCodes.includes('LEARNING_TASK_READ_MANAGED');
    exceptionReportEnabled.value = capabilities.studentExceptionReportEnabled === true
      && workbench.permissionCodes.includes('EXCEPTION_REPORT_READ');
    parentMobileManualRecoveryEnabled.value = capabilities.parentMobileManualRecoveryEnabled === true;
    studentAccountCancellationEnabled.value = capabilities.studentAccountCancellationEnabled === true;
  } catch (error) {
    if (error instanceof ApiError
        && (error.statusCode === 401 || error.statusCode === 403 || error.code === 'FEATURE_DISABLED')) {
      await leaveWorkbench();
      return;
    }
    uni.showToast({ title: '工作台加载失败，请稍后重试', icon: 'none' });
  } finally {
    loading.value = false;
  }
});

async function logout(): Promise<void> {
  const session = getOrganizationSession();
  if (!session || loggingOut.value) return;
  loggingOut.value = true;
  try {
    await logoutOrganization(session.accessToken);
  } catch {
    // 服务端不可用时仍清除本地机构会话，不影响家长和学生会话。
  } finally {
    loggingOut.value = false;
    await leaveWorkbench();
  }
}

function openAccountSecurity(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/account-security/account-security?identity=organization' });
}

function openStudentRelationships(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/organization-students/organization-students' });
}

function openClassManagement(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/organization-classes/organization-classes' });
}

function openTeacherManagement(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/organization-teachers/organization-teachers' });
}

function openTaskManagement(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/managed-tasks/managed-tasks?identity=organization' });
}

function openExceptionReports(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/exception-reports/exception-reports?identity=organization' });
}

function openParentMobileRecovery(): Promise<unknown> {
  return uni.navigateTo({
    url: '/pages/organization-parent-mobile-recovery/organization-parent-mobile-recovery'
  });
}

function openStudentCancellation(): Promise<unknown> {
  return uni.navigateTo({
    url: '/pages/organization-student-cancellation/organization-student-cancellation'
  });
}

async function leaveWorkbench(): Promise<void> {
  clearOrganizationSession();
  context.value = null;
  await uni.reLaunch({ url: '/pages/index/index' });
}

function organizationTypeName(typeCode: string): string {
  const names: Record<string, string> = {
    REGION: '区域', SCHOOL: '学校', CAMPUS: '校区', GRADE: '年级', CLASS: '班级'
  };
  return names[typeCode] || typeCode;
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.top-bar { min-height: 144rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; padding: 32rpx 40rpx; box-sizing: border-box; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.identity-area { min-width: 0; flex: 1; }
.display-name, .account-text, .section-title, .organization-name, .organization-type { display: block; }
.display-name { overflow: hidden; color: #1c2b28; font-size: 36rpx; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.account-text { margin-top: 8rpx; color: #708078; font-size: 24rpx; }
.logout-button { width: 112rpx; height: 64rpx; margin: 0; padding: 0; background: transparent; color: #9a4b38; font-size: 26rpx; }
.logout-button::after { border: 0; }
.state-text { min-height: 360rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 28rpx; }
.section-band { margin-top: 28rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.section-title { padding: 28rpx 40rpx; color: #1c2b28; font-size: 30rpx; font-weight: 700; }
.organization-row { min-height: 104rpx; display: flex; align-items: center; padding: 0 40rpx; border-top: 2rpx solid #edf1ef; }
.organization-main { min-width: 0; }
.organization-name { color: #1c2b28; font-size: 28rpx; word-break: break-word; }
.organization-type { margin-top: 8rpx; color: #708078; font-size: 22rpx; }
.workbench-button, .recovery-button, .cancellation-button, .security-button { width: calc(100% - 80rpx); height: 88rpx; margin: 32rpx 40rpx 0; border-radius: 10rpx; background: #167c5a; color: #ffffff; font-size: 29rpx; }
.recovery-button { background: #b54732; }
.cancellation-button { background: #b42318; }
.security-button { background: #ffffff; color: #167c5a; border: 2rpx solid #167c5a; }
.workbench-button::after, .recovery-button::after, .cancellation-button::after, .security-button::after { border: 0; }
.review-summary { margin: 28rpx 40rpx; padding: 24rpx; background: #fff; border-radius: 12rpx; color: #40514c; font-size: 28rpx; }.review-summary button { margin-top: 20rpx; font-size: 28rpx; }
</style>
