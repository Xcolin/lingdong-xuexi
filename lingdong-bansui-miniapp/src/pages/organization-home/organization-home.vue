<template>
  <view class="ld-page has-tabbar prototype-workspace"><NativeStatusSpacer/>
    
    <view class="ld-topbar"><text class="brand-title">灵动伴随</text><button class="space-switch" @tap="switchOrganization">{{ selectedOrganization?.name || '选择当前组织' }} ⌄</button></view>
    <view class="workspace-intro"><view class="intro-heading"><view class="school-art"><image class="school-reference-art" src="/static/pixel/default-school.png" mode="aspectFit"/></view><view><text class="workspace-intro-title">{{ selectedOrganization?.typeCode === 'SCHOOL' ? '学校工作台' : '机构工作台' }}</text><text class="workspace-intro-caption">组织有序 · 教学相长</text></view></view><view class="workspace-stats"><view v-for="label in ['校区','年级','班级','成长伙伴','成长小主人']" :key="label"><text class="stats-value">—</text><text>{{ label }}</text></view></view></view>
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

<view v-if="loading" class="ld-loading">正在加载</view>
<view class="workspace-shortcuts">
<button v-if="context?.permissionCodes.includes('STUDENT_TRANSFER_VIEW')" class="workspace-shortcut" @tap="openTransferRecords"><view class="shortcut-symbol"><PrototypeIcon name="school" tone="white" :size="44"/></view><view class="shortcut-copy"><text class="shortcut-title">转学申请</text><text>查看申请 · 审核与处理</text></view><text class="shortcut-arrow">›</text></button>
<button v-if="context?.permissionCodes.includes('CLASS_READ')" class="workspace-shortcut" @tap="openHierarchy"><view class="shortcut-symbol"><PrototypeIcon name="people" tone="white" :size="44"/></view><view class="shortcut-copy"><text class="shortcut-title">组织管理</text><text>校区 · 年级 · 班级 · 成员</text></view><text class="shortcut-arrow">›</text></button>
<button v-if="taskManagementEnabled" class="workspace-shortcut" @tap="openTasks"><view class="shortcut-symbol"><PrototypeIcon name="book" tone="white" :size="44"/></view><view class="shortcut-copy"><text class="shortcut-title">教学任务</text><text>发布与查看任务进度</text></view><text class="shortcut-arrow">›</text></button>
<button v-if="teacherManagementEnabled" class="workspace-shortcut" @tap="openTeacherManagement"><view class="shortcut-symbol"><PrototypeIcon name="user" tone="white" :size="44"/></view><view class="shortcut-copy"><text class="shortcut-title">教师管理</text><text>成长伙伴账号与任教</text></view><text class="shortcut-arrow">›</text></button>
</view>
<AppTabBar :items="ORG_TABS" :active="0" />
  </view>
</template>

<script setup lang="ts">
import NativeStatusSpacer from "@/components/NativeStatusSpacer.vue";
function openHierarchy(){uni.navigateTo({url:'/pages/organization-hierarchy/organization-hierarchy'});}
function openTransferRecords(){uni.navigateTo({url:'/pages/transfer-records/transfer-records'});}
function switchOrganization(){uni.navigateTo({url:"/pages/my-organizations/my-organizations?identity=organization&choose=1"});}

function openMembers(){uni.navigateTo({url:"/pages/organization-members/organization-members"});}function openEnrollment(){uni.navigateTo({url:"/pages/institution-enrollment/institution-enrollment"});}
import PrototypeIcon from '@/components/PrototypeIcon.vue';
function openTasks(){return uni.navigateTo({url:'/pages/managed-tasks/managed-tasks?identity=organization'});}
function openAccountSettings() { return uni.navigateTo({url: '/pages/account-settings/account-settings?identity=organization'}); }
import { ref, computed } from 'vue';
import OrganizationExceptionSummary from '@/components/OrganizationExceptionSummary.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { ORG_TABS } from '@/config/tabbar';
import { useManagedReviewSummary } from '@/composables/use-managed-review-summary';
const { reviewEnabled, reviewLoading, reviewTotal, reviewError, refreshReviews, openReviews } = useManagedReviewSummary('organization');
import { onShow,onHide,onUnload } from '@dcloudio/uni-app';
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
const selectedOrganization = computed(() => context.value?.organizations.length === 1 ? context.value.organizations[0] : null);
const accountSecurityEnabled = ref(false);
const studentRelationshipEnabled = ref(false);
const classManagementEnabled = ref(false);
const teacherManagementEnabled = ref(false);
const taskManagementEnabled = ref(false);
const exceptionReportEnabled = ref(false);
const parentMobileManualRecoveryEnabled = ref(false);
const studentAccountCancellationEnabled = ref(false);

let homeRevision=0;
function hideWorkspace(){homeRevision++;context.value=null;loading.value=false;}
onHide(hideWorkspace);onUnload(hideWorkspace);
onShow(async () => {
  const n=++homeRevision;context.value=null;
  const session = getOrganizationSession();
  if (!session) return leaveWorkbench();
  loading.value = true;
  try {
    const [capabilities, workbench] = await Promise.all([
      getMiniappCapabilities(),
      getOrganizationWorkbenchContext(session.accessToken)
    ]);
    if(n!==homeRevision||getOrganizationSession()?.accessToken!==session.accessToken)return;
    if (!capabilities.organizationMiniappAuthEnabled) return leaveWorkbench();
        const selected = uni.getStorageSync('lingdong.selected-organization') as {accessToken?: string; organizationId?: string} | undefined;
    context.value = selected?.accessToken === session.accessToken && selected.organizationId
      ? {...workbench, organizations: workbench.organizations.filter(item => item.id === selected.organizationId)}
      : {...workbench, organizations: []};
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
    if(n!==homeRevision||getOrganizationSession()?.accessToken!==session.accessToken)return;
    if (error instanceof ApiError
        && (error.statusCode === 401 || error.statusCode === 403 || error.code === 'FEATURE_DISABLED')) {
      await leaveWorkbench();
      return;
    }
    uni.showToast({ title: '工作台加载失败，请稍后重试', icon: 'none' });
  } finally {
    if(n===homeRevision)loading.value = false;
  }
});

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
    REGION: '区域', SCHOOL: '学校', TRAINING_INSTITUTION: '培训机构', FAMILY: '家庭', CAMPUS: '校区', GRADE: '年级', CLASS: '班级', COURSE_CATEGORY: '课程分类'
  };
  return names[typeCode] || typeCode;
}
</script>

<style scoped>
.prototype-workspace{min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;background:#f5f8f4;color:#182d26;padding:24rpx 24rpx 150rpx}.page-shell{padding-bottom:48rpx}.ld-topbar{display:flex;align-items:center;gap:18rpx;background:linear-gradient(120deg,#21664f,#0b4b3d);margin:-24rpx -24rpx 28rpx;padding:28rpx 26rpx 50rpx;color:white;border-radius:24rpx 24rpx 0 0}.ld-topbar-brand{display:flex;align-items:center;gap:18rpx;flex:1}.ld-topbar-name{display:block;font-size:32rpx;font-weight:650}.ld-topbar-meta{display:block;font-size:23rpx;opacity:.8;margin-top:8rpx}.ld-topbar-logo,.profile-avatar{width:80rpx;height:80rpx;border-radius:50%;background:#ffffff25;display:flex;align-items:center;justify-content:center}.profile-copy{flex:1}.ld-card,.ld-group,.form-band,.band,.search-band{background:#fff;border:1rpx solid #e5ece5;border-radius:24rpx;padding:24rpx;margin-bottom:20rpx}.workspace-intro{background:#fff;border-radius:24rpx;padding:30rpx 24rpx;margin-bottom:20rpx}.workspace-intro-title{display:block;font-size:34rpx;font-weight:700}.workspace-intro-caption{display:block;font-size:24rpx;color:#778078;margin-top:10rpx}.workspace-shortcuts{display:flex;flex-direction:column;gap:16rpx;margin-bottom:16rpx}.workspace-shortcut{display:flex;align-items:center;flex-wrap:wrap;text-align:left;background:#fff;border-radius:24rpx;padding:22rpx 26rpx;font-size:30rpx;font-weight:600;color:#182d26;min-height:120rpx}.workspace-shortcut>text{width:100%;padding-left:80rpx;font-size:23rpx;font-weight:400;color:#7b837d}.shortcut-symbol,.ld-cell-icon,.class-badge{width:64rpx;height:64rpx;border-radius:14rpx;background:#138655;display:flex;align-items:center;justify-content:center;margin-right:18rpx}.ld-heading{margin:28rpx 0 18rpx}.ld-heading-title,.form-title,.section-title{display:block;font-size:30rpx;font-weight:650}.ld-heading-sub{display:block;font-size:23rpx;color:#7b837d;margin-top:8rpx}.ld-cell,.class-row,.teacher-row,.organization-row{display:flex;align-items:center;padding:24rpx 0;border-bottom:1rpx solid #edf1eb;text-align:left;background:#fff;color:#182d26}.ld-cell:last-child,.class-row:last-child,.teacher-row:last-child{border-bottom:0}.ld-cell-body,.class-main,.organization-main{flex:1}.ld-cell-title,.class-name,.organization-name{display:block;font-size:29rpx;font-weight:600}.ld-cell-sub,.school-name,.organization-type{display:block;font-size:23rpx;color:#7d847e;margin-top:8rpx}.ld-cell-arrow{font-size:34rpx;color:#65726a}.prototype-section-header{display:flex;align-items:center;justify-content:center;font-size:32rpx;font-weight:700;margin-bottom:28rpx;gap:12rpx}.mode-control,.search-band,.action-band,.form-actions,.paging{display:flex;gap:14rpx;align-items:center;flex-wrap:wrap;margin-bottom:24rpx}.mode-control button{flex:1}.active{background:#e1f3e4;color:#0c7748}.field-label{display:block;margin:24rpx 0 12rpx;font-size:26rpx}.text-input,.search-input,.picker-value{background:#f8faf7;border:1rpx solid #e0e7df;border-radius:16rpx;padding:18rpx;min-height:72rpx;box-sizing:border-box;font-size:26rpx}.search-input{flex:1;min-width:180rpx}.check-row{display:flex;align-items:center;gap:12rpx;padding:16rpx 0}.state-text{font-size:26rpx;color:#7a837c;padding:24rpx}.review-card{display:flex;align-items:center;gap:20rpx}.review-info{flex:1}.ld-card-title{display:block;font-size:30rpx;font-weight:650}.review-count{display:block;margin-top:12rpx;font-size:24rpx;color:#718077}button{margin:0;font-size:25rpx;line-height:1.5;background:#eaf2e9;color:#21563e;padding:14rpx 20rpx;border-radius:16rpx}button::after{border:0}.primary-button,.save-button,.submit-button,.create-button{background:#137c53;color:white}.logout-button{margin-top:30rpx;color:#b65c50}.ld-logout,.space-switch{font-size:23rpx;flex-shrink:0}.org-badge{margin-right:18rpx;background:#e4f2e4;border-radius:14rpx;padding:16rpx}.error{color:#a43b31}.check-list{margin:18rpx 0}
</style>

<style scoped>
.prototype-workspace{padding:20rpx 22rpx 150rpx;}.ld-topbar{display:flex;flex-direction:column;gap:8rpx;margin:-20rpx -22rpx 0;padding:20rpx 24rpx 106rpx;border-radius:0;background:linear-gradient(120deg,#1c6553,#0e5041);}.brand-title{font-size:30rpx;font-weight:650;}.space-switch{background:transparent;color:#fff;padding:0;font-size:26rpx;}.workspace-intro{position:relative;margin-top:-84rpx;padding:22rpx 24rpx 20rpx;border:1rpx solid #e4ece3;border-radius:22rpx;}.intro-heading{display:flex;align-items:center;gap:22rpx;}.school-art{width:126rpx;height:126rpx;background:#edf5e8;border-radius:18rpx;display:flex;align-items:center;justify-content:center;}.workspace-intro-title{font-size:34rpx;}.workspace-intro-caption{font-size:23rpx;margin-top:10rpx;}.workspace-stats{display:flex;border-top:1rpx solid #eef1eb;margin-top:20rpx;padding-top:16rpx;text-align:center;}.workspace-stats>view{flex:1;color:#748078;font-size:21rpx;}.workspace-stats>view+view{border-left:1rpx solid #edf1eb;}.workspace-stats text{display:block;}.workspace-stats .stats-value{color:#26362d;font-size:30rpx;font-weight:650;margin-bottom:5rpx;}.workspace-shortcuts{gap:14rpx;}.workspace-shortcut{flex-wrap:nowrap;padding:18rpx 24rpx;min-height:116rpx;border-radius:22rpx;}.shortcut-symbol{width:70rpx;height:70rpx;flex-shrink:0;background:linear-gradient(140deg,#159b67,#08784f);margin-right:28rpx;}.shortcut-copy{flex:1;}.shortcut-copy text{display:block;font-size:23rpx;color:#7b837d;font-weight:400;}.shortcut-copy .shortcut-title{font-size:29rpx;font-weight:650;color:#23352a;margin-bottom:6rpx;}.workspace-shortcut>.shortcut-arrow{width:auto;padding:0;font-size:36rpx;color:#53695c;}
</style>

<style scoped>.school-reference-art{width:150rpx;height:150rpx;display:block;flex-shrink:0}.school-art{width:150rpx;height:150rpx;flex-shrink:0}</style>
