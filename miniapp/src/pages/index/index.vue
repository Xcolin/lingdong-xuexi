<template>
  <view class="page-shell">
    <view class="brand-area">
      <view class="brand-mark">灵</view>
      <text class="brand-name">灵动学习</text>
    </view>

    <view class="entry-area">
      <view v-if="loading" class="status-row">
        <view class="loading-dot" />
        <text>正在加载</text>
      </view>
      <button v-else-if="studentLoginEnabled" class="primary-button" @tap="openStudentLogin">
        学生登录
      </button>
      <text v-else class="unavailable-text">服务暂不可用</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { getStudentSession } from '@/session/student-session';

const loading = ref(true);
const studentLoginEnabled = ref(false);

onShow(async () => {
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    studentLoginEnabled.value = capabilities.studentCodeLoginEnabled || capabilities.studentQrLoginEnabled;
    if (studentLoginEnabled.value && getStudentSession()) {
      await uni.redirectTo({ url: '/pages/student-home/student-home' });
    }
  } catch {
    studentLoginEnabled.value = false;
  } finally {
    loading.value = false;
  }
});

function openStudentLogin(): void {
  uni.navigateTo({ url: '/pages/student-login/student-login' });
}
</script>

<style lang="scss" scoped>
.page-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  padding: 132rpx 48rpx 96rpx;
  box-sizing: border-box;
  background: #f4f7f5;
}

/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */

.brand-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 28rpx;
}

.brand-mark {
  width: 120rpx;
  height: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  background: #167c5a;
  color: #ffffff;
  font-size: 52rpx;
  font-weight: 700;
}

.brand-name {
  color: #1c2b28;
  font-size: 40rpx;
  font-weight: 700;
}

.entry-area {
  width: 100%;
  max-width: 640rpx;
  min-height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.primary-button {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  background: #167c5a;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 600;
}

.primary-button::after {
  border: 0;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  color: #708078;
  font-size: 28rpx;
}

.loading-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #e26d4f;
}

.unavailable-text {
  color: #9a4b38;
  font-size: 28rpx;
}
</style>
