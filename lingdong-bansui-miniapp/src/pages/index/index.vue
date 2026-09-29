<template>
  <view class="page-shell">
    <view class="brand-area">
      <view class="brand-mark">灵</view>
      <text class="brand-name">灵动伴随</text>
    </view>

    <view class="entry-area">
      <view v-if="loading" class="status-row">
        <view class="loading-dot" />
        <text>正在加载</text>
      </view>
      <template v-else-if="studentLoginEnabled || parentLoginEnabled || organizationLoginEnabled">
        <button v-if="parentLoginEnabled" class="primary-button" @tap="openParentLogin">
          {{ parentSessionExists ? '进入家长端' : '家长登录' }}
        </button>
        <button v-if="studentLoginEnabled" class="secondary-button" @tap="openStudentLogin">
          {{ studentSessionExists ? '进入学生端' : '学生登录' }}
        </button>
        <button v-if="organizationLoginEnabled" class="secondary-button" @tap="openOrganizationLogin">
          {{ organizationSessionExists ? '进入机构或教师工作台' : '机构与教师登录' }}
        </button>
      </template>
      <text v-else class="unavailable-text">服务暂不可用</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { getParentAuthContext } from '@/api/auth';
import { getParentSession } from '@/session/parent-session';
import { getStudentSession } from '@/session/student-session';
import { getOrganizationSession } from '@/session/organization-session';

const loading = ref(true);
const studentLoginEnabled = ref(false);
const parentLoginEnabled = ref(false);
const organizationLoginEnabled = ref(false);
const studentSessionExists = ref(false);
const parentSessionExists = ref(false);
const organizationSessionExists = ref(false);

onShow(async () => {
  loading.value = true;
  const [capabilityResult, parentContextResult] = await Promise.allSettled([
    getMiniappCapabilities(), getParentAuthContext()
  ]);
  studentLoginEnabled.value = capabilityResult.status === 'fulfilled'
    && (capabilityResult.value.studentCodeLoginEnabled || capabilityResult.value.studentQrLoginEnabled);
  parentLoginEnabled.value = parentContextResult.status === 'fulfilled' && parentContextResult.value.enabled;
  organizationLoginEnabled.value = capabilityResult.status === 'fulfilled'
    && capabilityResult.value.organizationMiniappAuthEnabled;
  studentSessionExists.value = Boolean(getStudentSession());
  parentSessionExists.value = Boolean(getParentSession());
  organizationSessionExists.value = Boolean(getOrganizationSession());
  loading.value = false;
});

function openStudentLogin(): void {
  uni.navigateTo({ url: studentSessionExists.value
    ? '/pages/student-home/student-home'
    : '/pages/student-login/student-login' });
}

function openParentLogin(): void {
  uni.navigateTo({ url: parentSessionExists.value
    ? '/pages/parent-onboarding/parent-onboarding'
    : '/pages/parent-login/parent-login' });
}

function openOrganizationLogin(): void {
  uni.navigateTo({ url: '/pages/organization-login/organization-login' });
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
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: 24rpx;
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

.secondary-button {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  background: #ffffff;
  color: #167c5a;
  font-size: 32rpx;
  font-weight: 600;
}

.secondary-button::after { border: 2rpx solid #167c5a; border-radius: 12rpx; }

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
