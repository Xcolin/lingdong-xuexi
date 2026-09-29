<template>
  <view class="page-shell">
    <view class="top-bar">
      <view>
        <text class="brand-name">灵动伴随</text>
        <text class="account-text">{{ session?.mobile || '' }}</text>
      </view>
      <button class="logout-button" :disabled="loggingOut" @tap="logout">退出</button>
    </view>
    <view class="content-band">
      <text class="welcome-title">家长端</text>
      <button v-if="familyTaskEnabled" class="relationship-entry" @tap="openFamilyTasks">家庭任务</button>
      <button v-if="rewardEnabled" class="relationship-entry" @tap="openRewards">家庭奖励与兑换</button>
      <button v-if="weeklyEnabled" class="relationship-entry" @tap="openWeekly">孩子成长周报</button>
      <button v-if="rankEnabled" class="relationship-entry" @tap="openRank(false)">班级匿名排行</button>
      <button v-if="rankWithdrawalEnabled" class="relationship-entry" @tap="openRank(true)">排行查看授权</button>
      <button v-if="attendanceEnabled" class="relationship-entry" @tap="openAttendance">考勤记录</button>
      <view v-if="reviewEnabled" class="status-text"><text v-if="reviewLoading">正在加载待审核任务…</text><text v-else-if="reviewError">{{ reviewError }}</text><text v-else>{{ reviewTotal === 0 ? '暂无待审核任务' : `待审核任务：${reviewTotal} 项` }}</text><button class="relationship-entry" @tap="openReviews">查看待审核任务</button></view>
      <button v-if="relationshipEnabled" class="relationship-entry" @tap="openRelationships">家长关系</button>
      <button v-if="accountSecurityEnabled" class="relationship-entry" @tap="openAccountSecurity">账号安全</button>
      <button v-if="accountLifecycleEnabled" class="relationship-entry" @tap="openAccountLifecycle">账号与手机号</button>
      <button v-if="studentWechatAuthEnabled" class="relationship-entry" @tap="openStudentWechat">学生微信绑定</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { getParentAuthContext, getParentState, logoutParent } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { rankApi } from '@/api/anonymous-rank';
import { parentReviewApi } from '@/api/parent-task-review';
import { canReview } from '@/models/parent-task-reviews';
import { canReadWeekly } from '@/api/parent-weekly-review';
import { canManageFamily } from '@/models/family-task-draft';
import { canUseParentRewards } from '@/api/parent-reward';
import { useAttendanceEntry } from '@/composables/use-attendance-entry';
const { attendanceEnabled, openAttendance } = useAttendanceEntry('parent');
import {
  clearParentSession,
  getParentSession,
  type StoredParentSession
} from '@/session/parent-session';

const session = ref<StoredParentSession | null>(null);
const loggingOut = ref(false);
const relationshipEnabled = ref(false);
const accountSecurityEnabled = ref(false);
const accountLifecycleEnabled = ref(false);
const studentWechatAuthEnabled = ref(false);
const weeklyEnabled = ref(false);
const familyTaskEnabled = ref(false);
const rewardEnabled = ref(false);
function openRewards() { return uni.navigateTo({ url: '/pages/parent-rewards/parent-rewards' }); }
function openFamilyTasks() { return uni.navigateTo({ url: '/pages/family-tasks/family-tasks' }); }
function openWeekly() { return uni.navigateTo({ url: '/pages/parent-weekly-reviews/parent-weekly-reviews' }); }
const reviewEnabled = ref(false), reviewLoading = ref(false), reviewTotal = ref(0), reviewError = ref('');
let homeRequest = 0;
function openReviews() { return uni.navigateTo({ url: '/pages/parent-task-reviews/parent-task-reviews' }); }
onHide(() => { homeRequest++; reviewEnabled.value = false; weeklyEnabled.value = false; familyTaskEnabled.value = false; rewardEnabled.value = false; });
const rankEnabled = ref(false), rankWithdrawalEnabled = ref(false);
function openRank(withdraw: boolean) {
  return uni.navigateTo({ url: `/pages/anonymous-ranks/anonymous-ranks${withdraw ? '?withdraw=true' : ''}` });
}

onShow(async () => {
  const request = ++homeRequest; reviewEnabled.value = false; reviewError.value = ''; reviewTotal.value = 0;
  weeklyEnabled.value = false;
  familyTaskEnabled.value = false;
  rewardEnabled.value = false;
  rankEnabled.value = false; rankWithdrawalEnabled.value = false;
  session.value = getParentSession();
  if (!session.value) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const [context, state, capabilities, user] = await Promise.all([
      getParentAuthContext(), getParentState(session.value.accessToken), getMiniappCapabilities(), rankApi.me(session.value.accessToken)
    ]);
    if (request !== homeRequest) return;
    if (!context.enabled) {
      clearParentSession();
      await uni.reLaunch({ url: '/pages/index/index' });
      return;
    }
    if (state.onboardingRequired || state.agreementAcceptanceRequired) {
      await uni.redirectTo({ url: '/pages/parent-onboarding/parent-onboarding' });
      return;
    }
    relationshipEnabled.value = capabilities.parentRelationshipManagementEnabled;
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
    accountLifecycleEnabled.value = capabilities.parentAccountLifecycleEnabled;
    studentWechatAuthEnabled.value = capabilities.studentWechatAuthEnabled === true;
    rankWithdrawalEnabled.value = user.clientType === 'MINIAPP' && user.roleCodes.includes('PARENT') && !user.roleCodes.includes('SYS_AUDITOR');
    rankEnabled.value = rankWithdrawalEnabled.value && capabilities.anonymousClassRankEnabled === true
      && user.permissionCodes.includes('MINIAPP_ANONYMOUS_CLASS_RANK_READ');
    reviewEnabled.value = canReview(user, capabilities.learningTaskManagementEnabled);
    weeklyEnabled.value = canReadWeekly(user);
    familyTaskEnabled.value = capabilities.learningTaskManagementEnabled && canManageFamily(user, 'LEARNING_TASK_READ_MANAGED');
    rewardEnabled.value = capabilities.rewardExchangeEnabled && canUseParentRewards(user);
    if (reviewEnabled.value) {
      reviewLoading.value = true;
      try { const result = await parentReviewApi.list(session.value.accessToken); if (request === homeRequest) reviewTotal.value = result.total; }
      catch { if (request === homeRequest) reviewError.value = '待审核任务加载失败，请进入列表重试'; }
      finally { if (request === homeRequest) reviewLoading.value = false; }
    }
  } catch {
    if (request !== homeRequest) return;
    clearParentSession();
    await uni.reLaunch({ url: '/pages/index/index' });
  }
});

async function logout(): Promise<void> {
  if (!session.value || loggingOut.value) return;
  loggingOut.value = true;
  try {
    await logoutParent(session.value.accessToken);
  } catch {
    // 服务端不可用时仍清除本地家长会话，不影响独立的学生会话。
  } finally {
    clearParentSession();
    session.value = null;
    loggingOut.value = false;
    await uni.reLaunch({ url: '/pages/index/index' });
  }
}

function openRelationships(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/parent-relationships/parent-relationships' });
}

function openAccountSecurity(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/account-security/account-security?identity=parent' });
}

function openAccountLifecycle(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/parent-account-lifecycle/parent-account-lifecycle' });
}

function openStudentWechat(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/parent-student-wechat/parent-student-wechat' });
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.top-bar { min-height: 144rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; padding: 32rpx 40rpx; box-sizing: border-box; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.brand-name, .account-text { display: block; }
.brand-name { color: #1c2b28; font-size: 36rpx; font-weight: 700; }
.account-text { margin-top: 8rpx; color: #708078; font-size: 24rpx; }
.logout-button { width: 112rpx; height: 64rpx; margin: 0; padding: 0; background: transparent; color: #9a4b38; font-size: 26rpx; }
.logout-button::after { border: 0; }
.content-band { padding: 72rpx 40rpx; }
.welcome-title { display: block; color: #1c2b28; font-size: 42rpx; font-weight: 700; }
.status-text { display: block; margin-top: 48rpx; padding: 48rpx 0; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; color: #708078; font-size: 28rpx; }
.relationship-entry { width: 100%; height: 88rpx; margin: 32rpx 0 0; border-radius: 10rpx; background: #167c5a; color: #ffffff; font-size: 29rpx; }
.relationship-entry::after { border: 0; }
</style>
