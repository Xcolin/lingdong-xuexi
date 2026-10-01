<template>
  <view class="page-shell">
    <view class="hero-area">
      <view class="hero-logo">灵</view>
      <text class="hero-name">灵动伴随</text>
      <text class="hero-slogan">自律成长，家校相伴</text>
    </view>

    <view class="entry-area">
      <view v-if="loading" class="status-row">
        <view class="loading-dot" />
        <text>正在加载</text>
      </view>
      <view v-else-if="studentLoginEnabled || parentLoginEnabled || organizationLoginEnabled" class="ld-group">
        <button v-if="parentLoginEnabled" class="ld-cell" @tap="openParentLogin">
          <view class="ld-cell-icon orange">家</view>
          <view class="ld-cell-body">
            <text class="ld-cell-title">{{ parentSessionExists ? '进入家长端' : '家长登录' }}</text>
            <text class="ld-cell-sub">审核任务 · 家庭奖励 · 成长周报</text>
          </view>
          <text class="ld-cell-arrow">›</text>
        </button>
        <button v-if="studentLoginEnabled" class="ld-cell" @tap="openStudentLogin">
          <view class="ld-cell-icon teal">学</view>
          <view class="ld-cell-body">
            <text class="ld-cell-title">{{ studentSessionExists ? '进入学生端' : '学生登录' }}</text>
            <text class="ld-cell-sub">今日任务 · 积分 · 成长复盘</text>
          </view>
          <text class="ld-cell-arrow">›</text>
        </button>
        <button v-if="organizationLoginEnabled" class="ld-cell" @tap="openOrganizationLogin">
          <view class="ld-cell-icon blue">校</view>
          <view class="ld-cell-body">
            <text class="ld-cell-title">{{ organizationSessionExists ? '进入机构或教师工作台' : '机构与教师登录' }}</text>
            <text class="ld-cell-sub">班级管理 · 点名 · 任务发布</text>
          </view>
          <text class="ld-cell-arrow">›</text>
        </button>
      </view>
      <view v-else class="ld-empty">
        <text class="ld-empty-main">服务暂不可用</text>
        <text class="ld-empty-sub">请稍后重试，或联系管理员确认服务状态</text>
      </view>
    </view>

    <text class="footer-mark">灵动伴随 · 自律成长管理系统</text>
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
  box-sizing: border-box;
  background: $ld-bg;
}

/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */

.hero-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 96rpx 48rpx 72rpx;
  background:
    radial-gradient(520rpx 320rpx at 88% -10%, rgba(226, 109, 79, 0.14), transparent 70%),
    radial-gradient(620rpx 400rpx at 0% 0%, rgba(22, 124, 90, 0.12), transparent 70%),
    $ld-card;
  border-bottom-left-radius: 48rpx;
  border-bottom-right-radius: 48rpx;
}

.hero-logo {
  width: 148rpx;
  height: 148rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 36rpx;
  background: $ld-gradient-brand;
  color: #ffffff;
  font-size: 64rpx;
  font-weight: 700;
  box-shadow: $ld-shadow-float;
}

.hero-name {
  margin-top: 32rpx;
  color: $ld-text;
  font-size: 48rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
}

.hero-slogan {
  margin-top: 14rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
  letter-spacing: 2rpx;
}

.entry-area {
  flex: 1;
  width: 100%;
  padding: 36rpx 32rpx 0;
  box-sizing: border-box;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 56rpx 0;
  color: $ld-text-muted;
  font-size: $ld-font-body;
}

.loading-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $ld-accent;
  animation: ld-pulse 1.2s ease-in-out infinite;
}

@keyframes ld-pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.15); }
}

.footer-mark {
  padding: 40rpx 0 48rpx;
  color: $ld-text-muted;
  font-size: $ld-font-mini;
  text-align: center;
  opacity: 0.8;
}
</style>