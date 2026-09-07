<template>
  <view class="page-shell">
    <view class="page-heading">
      <text class="heading-title">家长关系邀请</text>
      <view class="heading-rule" />
    </view>
    <view v-if="loading" class="state-view">正在加载</view>
    <view v-else-if="!enabled" class="state-view disabled-state">家长关系功能未启用</view>
    <view v-else class="invitation-form">
      <view class="field-group">
        <text class="field-label">手机号</text>
        <input v-model="mobile" class="field-input" type="number" maxlength="11"
               placeholder="请输入手机号" :disabled="submitting" />
      </view>
      <view class="field-group">
        <text class="field-label">验证码</text>
        <input v-model="smsCode" class="field-input" type="number" maxlength="6"
               placeholder="6位验证码" :disabled="submitting" />
      </view>
      <checkbox-group class="agreement-row" @change="changeAgreement">
        <label><checkbox value="accepted" color="#167c5a" :checked="agreementAccepted" />我已阅读并同意当前用户协议</label>
      </checkbox-group>
      <text v-if="errorMessage" class="error-message">{{ errorMessage }}</text>
      <view class="action-row">
        <button class="reject-button" :disabled="submitting" @tap="rejectInvitation">拒绝邀请</button>
        <button class="accept-button" :loading="submitting" :disabled="submitting" @tap="acceptInvitation">确认接受</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getParentAuthContext } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import {
  acceptParentRelationshipInvitation,
  rejectParentRelationshipInvitation
} from '@/api/parent-relationship';
import {
  getOrCreateParentDeviceId,
  getParentDeviceName,
  saveParentSession
} from '@/session/parent-session';

const invitationId = ref('');
const loading = ref(true);
const enabled = ref(false);
const agreementVersion = ref('');
const mobile = ref('');
const smsCode = ref('');
const agreementAccepted = ref(false);
const submitting = ref(false);
const errorMessage = ref('');

onLoad(async (options) => {
  invitationId.value = typeof options?.invitationId === 'string' ? options.invitationId : '';
  try {
    const [capabilities, context] = await Promise.all([
      getMiniappCapabilities(), getParentAuthContext()
    ]);
    enabled.value = capabilities.parentRelationshipManagementEnabled && Boolean(invitationId.value);
    agreementVersion.value = context.agreementVersion;
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    loading.value = false;
  }
});

async function acceptInvitation(): Promise<void> {
  if (!validateCommon() || !agreementAccepted.value) {
    if (!agreementAccepted.value && !errorMessage.value) errorMessage.value = '请先同意当前用户协议';
    return;
  }
  submitting.value = true;
  errorMessage.value = '';
  try {
    const result = await acceptParentRelationshipInvitation(invitationId.value, payload(true));
    saveParentSession(result.session, mobile.value);
    const url = result.onboardingRequired || result.agreementAcceptanceRequired
      ? '/pages/parent-onboarding/parent-onboarding'
      : '/pages/parent-home/parent-home';
    await uni.reLaunch({ url });
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function rejectInvitation(): Promise<void> {
  if (!validateCommon()) return;
  submitting.value = true;
  errorMessage.value = '';
  try {
    await rejectParentRelationshipInvitation(invitationId.value, payload(false));
    await new Promise<UniApp.ShowModalRes>((resolve) => uni.showModal({
      title: '已拒绝邀请', content: '本次家长关系邀请已关闭。', showCancel: false, success: resolve
    }));
    await uni.reLaunch({ url: '/pages/index/index' });
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    submitting.value = false;
  }
}

function payload(accepted: boolean) {
  return {
    mobile: mobile.value,
    smsCode: smsCode.value,
    clientType: 'MINIAPP' as const,
    deviceId: getOrCreateParentDeviceId(),
    deviceName: getParentDeviceName(),
    agreementAccepted: accepted,
    agreementVersion: agreementVersion.value
  };
}

function validateCommon(): boolean {
  errorMessage.value = '';
  if (!/^1[3-9]\d{9}$/.test(mobile.value)) {
    errorMessage.value = '手机号格式不正确';
    return false;
  }
  if (!/^\d{6}$/.test(smsCode.value)) {
    errorMessage.value = '请输入6位验证码';
    return false;
  }
  return true;
}

function changeAgreement(event: { detail: { value: string[] } }): void {
  agreementAccepted.value = event.detail.value.includes('accepted');
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 64rpx 40rpx; box-sizing: border-box; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.page-heading { width: 100%; max-width: 720rpx; margin: 0 auto 56rpx; }
.heading-title { display: block; color: #1c2b28; font-size: 42rpx; font-weight: 700; }
.heading-rule { width: 72rpx; height: 8rpx; margin-top: 18rpx; border-radius: 4rpx; background: #e26d4f; }
.state-view { min-height: 320rpx; display: flex; align-items: center; justify-content: center; color: #71817a; font-size: 28rpx; }
.disabled-state { color: #9a4b38; }
.invitation-form { width: 100%; max-width: 720rpx; display: flex; flex-direction: column; gap: 32rpx; margin: 0 auto; }
.field-label { display: block; margin-bottom: 12rpx; color: #455a52; font-size: 25rpx; }
.field-input { width: 100%; height: 88rpx; padding: 0 22rpx; border: 2rpx solid #cdd8d3; border-radius: 10rpx; box-sizing: border-box; background: #ffffff; font-size: 29rpx; }
.agreement-row { color: #52675f; font-size: 25rpx; line-height: 1.5; }
.error-message { padding: 20rpx 22rpx; border-left: 6rpx solid #b54734; background: #fff1ed; color: #9b3e2f; font-size: 25rpx; }
.action-row { display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; }
.reject-button, .accept-button { height: 86rpx; margin: 0; border-radius: 10rpx; font-size: 28rpx; }
.reject-button { background: #ffffff; color: #a34332; }
.accept-button { background: #167c5a; color: #ffffff; }
.reject-button::after, .accept-button::after { border: 0; }
</style>
