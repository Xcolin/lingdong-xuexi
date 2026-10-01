<template>
  <view class="ld-page has-tabbar">
    <view class="ld-topbar">
      <view class="ld-topbar-brand">
        <view class="ld-topbar-logo">灵</view>
        <view>
          <text class="ld-topbar-name">家长端</text>
          <text class="ld-topbar-meta">{{ session?.mobile || '' }}</text>
        </view>
      </view>
      <button class="ld-logout" :disabled="loggingOut" @tap="logout">退出</button>
    </view>

    <view v-if="reviewEnabled" class="ld-card review-card">
      <view class="review-info">
        <text class="ld-card-title">任务审核</text>
        <text class="review-count">{{ reviewLoading ? '正在加载待审核任务…' : reviewError || (reviewTotal === 0 ? '暂无待审核任务' : `待审核任务：${reviewTotal} 项`) }}</text>
      </view>
      <button class="review-button" @tap="openReviews">查看</button>
    </view>

    <view class="ld-heading">
      <text class="ld-heading-title">快捷入口</text>
      <text class="ld-heading-sub">审核任务、奖励与孩子的成长动态</text>
    </view>

    <view class="ld-group">
      <button v-if="rewardEnabled" class="ld-cell" @tap="openRewards">
        <view class="ld-cell-icon gold">奖</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">家庭奖励与兑换</text>
          <text class="ld-cell-sub">上架奖励并处理孩子的兑换申请</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="rankEnabled" class="ld-cell" @tap="openRank(false)">
        <view class="ld-cell-icon teal">榜</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">班级匿名排行</text>
          <text class="ld-cell-sub">主动开启后查看班级名次</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="rankWithdrawalEnabled" class="ld-cell" @tap="openRank(true)">
        <view class="ld-cell-icon teal">授</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">排行查看授权</text>
          <text class="ld-cell-sub">撤回已开启的查看授权</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="attendanceEnabled" class="ld-cell" @tap="openAttendance()">
        <view class="ld-cell-icon ">勤</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">考勤记录</text>
          <text class="ld-cell-sub">查看孩子的到离园记录</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
    </view>
  <AppTabBar :items="PARENT_TABS" :active="0" />
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
import AppTabBar from '@/components/AppTabBar.vue';
import { PARENT_TABS } from '@/config/tabbar';
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
</style>
