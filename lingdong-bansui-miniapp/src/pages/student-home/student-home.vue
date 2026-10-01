<template>
  <view class="ld-page has-tabbar">
    <view class="ld-topbar">
      <view class="ld-topbar-brand">
        <view class="ld-topbar-logo">灵</view>
        <view>
          <text class="ld-topbar-name">灵动伴随</text>
          <text class="ld-topbar-meta">{{ session?.studentAccount || '' }}</text>
        </view>
      </view>
      <button class="ld-logout" :disabled="loggingOut" @tap="logout">退出</button>
    </view>

    <view class="ld-heading">
      <text class="ld-heading-title">我的</text>
      <text class="ld-heading-sub">账号信息与个人功能</text>
    </view>

    <view class="ld-group">
      <button v-if="attendanceEnabled" class="ld-cell" @tap="openAttendance()">
        <view class="ld-cell-icon teal">勤</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">我的考勤</text>
          <text class="ld-cell-sub">查看到离园记录</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="accountSecurityEnabled" class="ld-cell" @tap="openAccountSecurity()">
        <view class="ld-cell-icon soft">安</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">账号安全</text>
          <text class="ld-cell-sub">查看设备和登录提醒</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
    </view>
  <AppTabBar :items="STUDENT_TABS" :active="3" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { STUDENT_TABS } from '@/config/tabbar';
import { onShow } from '@dcloudio/uni-app';
import { logoutStudent } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { useAttendanceEntry } from '@/composables/use-attendance-entry';
const { attendanceEnabled, openAttendance } = useAttendanceEntry('student');
import { clearStudentSession, getStudentSession, type StoredStudentSession } from '@/session/student-session';

const session = ref<StoredStudentSession | null>(null);
const loggingOut = ref(false);
const learningTaskEnabled = ref(false);
const growthPointEnabled = ref(false);
const rewardExchangeEnabled = ref(false);
const dailyGrowthReviewEnabled = ref(false);
const accountSecurityEnabled = ref(false);

onShow(async () => {
  session.value = getStudentSession();
  if (!session.value) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
    growthPointEnabled.value = capabilities.growthPointQueryEnabled;
    rewardExchangeEnabled.value = capabilities.rewardExchangeEnabled;
    dailyGrowthReviewEnabled.value = capabilities.dailyGrowthReviewEnabled;
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
  } catch {
    learningTaskEnabled.value = false;
    growthPointEnabled.value = false;
    rewardExchangeEnabled.value = false;
    dailyGrowthReviewEnabled.value = false;
    accountSecurityEnabled.value = false;
  }
});

function openAccountSecurity(): void {
  uni.navigateTo({ url: '/pages/account-security/account-security?identity=student' });
}

async function logout(): Promise<void> {
  if (!session.value || loggingOut.value) return;
  loggingOut.value = true;
  try {
    await logoutStudent(session.value.accessToken);
  } catch {
    // 本地会话始终清除，服务端令牌失效由过期和后续鉴权兜底。
  } finally {
    clearStudentSession();
    session.value = null;
    loggingOut.value = false;
    await uni.reLaunch({ url: '/pages/index/index' });
  }
}
</script>
