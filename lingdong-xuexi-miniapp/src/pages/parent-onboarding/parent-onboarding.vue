<template>
  <view class="page-shell">
    <view v-if="loading" class="loading-state">正在加载</view>
    <view v-else-if="state?.agreementAcceptanceRequired" class="content-shell">
      <text class="page-title">用户协议更新</text>
      <view class="title-rule" />
      <view class="content-band">
        <text class="content-title">当前协议版本</text>
        <text class="version-text">{{ state.currentAgreementVersion }}</text>
        <text class="content-description">请确认接受当前用户协议后继续使用家长端。</text>
      </view>
      <text v-if="errorMessage" class="error-message">{{ errorMessage }}</text>
      <button class="primary-button" :loading="submitting" :disabled="submitting" @tap="acceptAgreement">同意并继续</button>
    </view>

    <view v-else-if="state?.onboardingRequired" class="content-shell">
      <text class="page-title">家长首次引导</text>
      <view class="title-rule" />
      <view class="step-row">
        <view v-for="(_, index) in steps" :key="index" :class="['step-dot', { active: index <= current }]" />
      </view>
      <view class="content-band">
        <text class="step-count">{{ current + 1 }} / {{ steps.length }}</text>
        <text class="content-title">{{ steps[current].title }}</text>
        <text class="content-description">{{ steps[current].description }}</text>
      </view>
      <text v-if="errorMessage" class="error-message">{{ errorMessage }}</text>
      <view class="action-row">
        <button v-if="current > 0" class="secondary-button" @tap="current--">上一步</button>
        <button v-if="current < steps.length - 1" class="primary-button" @tap="current++">下一步</button>
        <button v-else class="primary-button" :loading="submitting" :disabled="submitting" @tap="complete">完成引导</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  acceptParentAgreement,
  completeParentOnboarding,
  getParentState,
  type ParentAuthState
} from '@/api/auth';
import { getParentSession } from '@/session/parent-session';

const steps = [
  { title: '欢迎使用', description: '建立清晰、稳定的家庭学习节奏。' },
  { title: '任务模式', description: '独立安排家庭任务，也可管理与机构关联学生的家庭任务。' },
  { title: '产品理念', description: '用明确目标、及时反馈和正向激励支持学生持续成长。' },
  { title: '专注模式', description: '按学生实际情况安排专注时段，减少无关干扰。' }
];

const loading = ref(true);
const submitting = ref(false);
const state = ref<ParentAuthState | null>(null);
const current = ref(0);
const errorMessage = ref('');

onLoad(async () => {
  const session = getParentSession();
  if (!session) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const loaded = await getParentState(session.accessToken);
    if (!loaded.onboardingRequired && !loaded.agreementAcceptanceRequired) {
      await uni.redirectTo({ url: '/pages/parent-home/parent-home' });
      return;
    }
    state.value = loaded;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '家长状态加载失败';
  } finally {
    loading.value = false;
  }
});

async function acceptAgreement(): Promise<void> {
  const session = getParentSession();
  if (!session || !state.value) return;
  submitting.value = true;
  errorMessage.value = '';
  try {
    await acceptParentAgreement(session.accessToken, state.value.currentAgreementVersion);
    if (!state.value.onboardingRequired) {
      await uni.redirectTo({ url: '/pages/parent-home/parent-home' });
      return;
    }
    state.value = { ...state.value, agreementAcceptanceRequired: false };
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '协议接受状态保存失败';
  } finally {
    submitting.value = false;
  }
}

async function complete(): Promise<void> {
  const session = getParentSession();
  if (!session) return;
  submitting.value = true;
  errorMessage.value = '';
  try {
    await completeParentOnboarding(session.accessToken);
    await uni.redirectTo({ url: '/pages/parent-home/parent-home' });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '引导状态保存失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 64rpx 40rpx; box-sizing: border-box; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.loading-state { min-height: 520rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 30rpx; }
.content-shell { width: 100%; max-width: 720rpx; margin: 0 auto; }
.page-title { display: block; color: #1c2b28; font-size: 44rpx; font-weight: 700; }
.title-rule { width: 72rpx; height: 8rpx; margin-top: 20rpx; border-radius: 4rpx; background: #e26d4f; }
.step-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12rpx; margin: 52rpx 0 36rpx; }
.step-dot { height: 8rpx; border-radius: 4rpx; background: #c8d3cf; }
.step-dot.active { background: #167c5a; }
.content-band { min-height: 320rpx; display: flex; flex-direction: column; justify-content: center; gap: 24rpx; margin-top: 52rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; }
.step-count, .version-text { color: #e26d4f; font-size: 26rpx; font-weight: 600; }
.content-title { color: #1c2b28; font-size: 38rpx; font-weight: 700; }
.content-description { color: #52645e; font-size: 30rpx; line-height: 48rpx; }
.error-message { display: block; min-height: 40rpx; margin-top: 28rpx; color: #b34f3b; font-size: 26rpx; line-height: 40rpx; }
.action-row { display: flex; justify-content: flex-end; gap: 20rpx; margin-top: 36rpx; }
.primary-button, .secondary-button { min-width: 220rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; margin: 36rpx 0 0 auto; border-radius: 12rpx; font-size: 30rpx; font-weight: 600; }
.action-row .primary-button, .action-row .secondary-button { margin-top: 0; }
.primary-button { background: #167c5a; color: #ffffff; }
.primary-button::after { border: 0; }
.secondary-button { margin-left: 0; background: #ffffff; color: #167c5a; }
.secondary-button::after { border: 2rpx solid #167c5a; border-radius: 12rpx; }
.primary-button[disabled] { background: #91aaa1; color: #ffffff; }
</style>
