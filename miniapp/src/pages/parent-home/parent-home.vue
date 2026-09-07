<template>
  <view class="page-shell">
    <view class="top-bar">
      <view>
        <text class="brand-name">灵动学习</text>
        <text class="account-text">{{ session?.mobile || '' }}</text>
      </view>
      <button class="logout-button" :disabled="loggingOut" @tap="logout">退出</button>
    </view>
    <view class="content-band">
      <text class="welcome-title">家长端</text>
      <button v-if="attendanceEnabled" class="relationship-entry" @tap="openAttendance">考勤记录</button>
      <text class="status-text">暂无待办事项</text>
      <button v-if="relationshipEnabled" class="relationship-entry" @tap="openRelationships">家长关系</button>
      <button v-if="accountSecurityEnabled" class="relationship-entry" @tap="openAccountSecurity">账号安全</button>
      <button v-if="accountLifecycleEnabled" class="relationship-entry" @tap="openAccountLifecycle">账号与手机号</button>
      <button v-if="studentWechatAuthEnabled" class="relationship-entry" @tap="openStudentWechat">学生微信绑定</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getParentAuthContext, getParentState, logoutParent } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
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

onShow(async () => {
  session.value = getParentSession();
  if (!session.value) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const [context, state, capabilities] = await Promise.all([
      getParentAuthContext(), getParentState(session.value.accessToken), getMiniappCapabilities()
    ]);
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
  } catch {
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
