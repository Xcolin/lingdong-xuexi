<template>
  <view class="ld-page has-tabbar">
    <view class="ld-topbar">
      <view class="ld-topbar-brand">
        <view class="ld-topbar-logo">校</view>
        <view>
          <text class="ld-topbar-name">{{ context?.displayName || '机构工作台' }}</text>
          <text class="ld-topbar-meta">{{ context?.username || '' }}</text>
        </view>
      </view>
      <button class="ld-logout" :disabled="loggingOut" @tap="logout">退出</button>
    </view>

    <view v-if="reviewEnabled || reviewLoading || reviewError" class="ld-card review-card">
      <view class="review-info">
        <text class="ld-card-title">任务审核</text>
        <text class="review-count">
          <template v-if="reviewLoading">正在加载待审核任务…</template>
          <template v-else-if="reviewError">{{ reviewError }}</template>
          <template v-else>{{ reviewTotal === 0 ? '暂无待审核任务' : `待审核任务：${reviewTotal} 项` }}</template>
        </text>
      </view>
      <button v-if="reviewEnabled && !reviewLoading" class="review-button" @tap="openReviews">
        {{ reviewError ? '重试' : '查看' }}
      </button>
    </view>

    <OrganizationExceptionSummary />

    <view v-if="loading" class="ld-loading">正在加载</view>
    <template v-else>
      <view class="ld-heading">
        <text class="ld-heading-title">管理组织</text>
        <text class="ld-heading-sub">当前账号负责的组织范围</text>
      </view>
      <view class="ld-card org-card">
        <view v-for="organization in context?.organizations || []" :key="organization.id" class="organization-row">
          <view class="org-badge">{{ organizationTypeName(organization.typeCode).slice(0, 1) }}</view>
          <view class="organization-main">
            <text class="organization-name">{{ organization.name }}</text>
            <text class="organization-type">{{ organizationTypeName(organization.typeCode) }}</text>
          </view>
        </view>
      </view>

      <view class="ld-group">
      <button v-if="studentRelationshipEnabled" class="ld-cell" @tap="openStudentRelationships()">
        <view class="ld-cell-icon blue">员</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">学员关系</text>
          <text class="ld-cell-sub">转班与离校关系管理</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="classManagementEnabled" class="ld-cell" @tap="openClassManagement()">
        <view class="ld-cell-icon teal">班</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">班级管理</text>
          <text class="ld-cell-sub">维护班级与启用状态</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="teacherManagementEnabled" class="ld-cell" @tap="openTeacherManagement()">
        <view class="ld-cell-icon purple">师</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">教师管理</text>
          <text class="ld-cell-sub">账号、班级绑定与密码重置</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="exceptionReportEnabled" class="ld-cell" @tap="openExceptionReports()">
        <view class="ld-cell-icon orange">报</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">异常报备</text>
          <text class="ld-cell-sub">处理出勤异常报备记录</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="parentMobileManualRecoveryEnabled" class="ld-cell" @tap="openParentMobileRecovery()">
        <view class="ld-cell-icon warn-soft">换</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">家长换号核验</text>
          <text class="ld-cell-sub">线下核验后为家长换绑手机号</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="studentAccountCancellationEnabled" class="ld-cell" @tap="openStudentCancellation()">
        <view class="ld-cell-icon danger-soft">销</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">学生账号注销</text>
          <text class="ld-cell-sub">注销学生账号及其数据</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      </view>
    </template>
  <AppTabBar :items="ORG_TABS" :active="0" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import OrganizationExceptionSummary from '@/components/OrganizationExceptionSummary.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { ORG_TABS } from '@/config/tabbar';
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

function openStudentRelationships(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/organization-students/organization-students' });
}

function openClassManagement(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/organization-classes/organization-classes' });
}

function openTeacherManagement(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/organization-teachers/organization-teachers' });
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
.org-card { padding: 12rpx 28rpx; }
.organization-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 26rpx 0;
  border-top: 2rpx solid $ld-line;
}
.organization-row:first-child { border-top: 0; }
.org-badge {
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
.organization-main { min-width: 0; }
.organization-name, .organization-type { display: block; }
.organization-name { color: $ld-text; font-size: 29rpx; font-weight: 600; word-break: break-word; }
.organization-type { margin-top: 6rpx; color: $ld-text-muted; font-size: $ld-font-mini; }
.icon-warn { background: $ld-warning-soft; color: $ld-warning; }
.icon-danger { background: $ld-danger-soft; color: $ld-danger; }
</style>
