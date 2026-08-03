<template>
  <view class="page-shell">
    <view class="top-bar">
      <view>
        <text class="brand-name">灵动学习</text>
        <text class="account-text">{{ session?.studentAccount || '' }}</text>
      </view>
      <button class="logout-button" :disabled="loggingOut" @tap="logout">退出</button>
    </view>
    <view class="content-band">
      <text class="welcome-title">欢迎回来</text>
      <button v-if="learningTaskEnabled" class="task-entry" @tap="openTasks">
        <view>
          <text class="task-entry-title">学习任务</text>
          <text class="task-entry-subtitle">查看家庭、机构和教师发布的任务</text>
        </view>
        <text class="task-entry-arrow">›</text>
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { logoutStudent } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { clearStudentSession, getStudentSession, type StoredStudentSession } from '@/session/student-session';

const session = ref<StoredStudentSession | null>(null);
const loggingOut = ref(false);
const learningTaskEnabled = ref(false);

onShow(async () => {
  session.value = getStudentSession();
  if (!session.value) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
  } catch {
    learningTaskEnabled.value = false;
  }
});

function openTasks(): void {
  uni.navigateTo({ url: '/pages/task-list/task-list' });
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

<style lang="scss" scoped>
.page-shell {
  min-height: 100vh;
  background: #f4f7f5;
}

/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */

.top-bar {
  min-height: 144rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  padding: 32rpx 40rpx;
  box-sizing: border-box;
  background: #ffffff;
  border-bottom: 2rpx solid #dce4e1;
}

.brand-name,
.account-text { display: block; }

.brand-name {
  color: #1c2b28;
  font-size: 34rpx;
  font-weight: 700;
}

.account-text {
  margin-top: 8rpx;
  color: #708078;
  font-size: 24rpx;
}

.logout-button {
  width: 128rpx;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  border-radius: 10rpx;
  background: #fff4f0;
  color: #a44835;
  font-size: 26rpx;
}

.logout-button::after {
  border: 2rpx solid #e8b4a8;
  border-radius: 10rpx;
}

.content-band { padding: 64rpx 40rpx; }

.welcome-title {
  color: #1c2b28;
  font-size: 44rpx;
  font-weight: 700;
}

.task-entry {
  width: 100%;
  min-height: 132rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 44rpx 0 0;
  padding: 24rpx 30rpx;
  box-sizing: border-box;
  border-radius: 10rpx;
  background: #ffffff;
  text-align: left;
}

.task-entry::after { border: 2rpx solid #d5e0db; border-radius: 10rpx; }

.task-entry-title,
.task-entry-subtitle { display: block; }

.task-entry-title { color: #1c2b28; font-size: 32rpx; font-weight: 650; }

.task-entry-subtitle { margin-top: 10rpx; color: #708078; font-size: 24rpx; }

.task-entry-arrow { color: #167c5a; font-size: 48rpx; line-height: 1; }
</style>
