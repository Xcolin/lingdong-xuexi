<template>
  <view class="page-shell">
    <view class="page-heading">
      <text class="heading-title">机构与教师登录</text>
      <view class="heading-rule" />
    </view>

    <view v-if="capabilityLoading" class="state-text">正在加载</view>
    <view v-else-if="!serviceEnabled" class="state-text disabled">服务暂不可用</view>
    <form v-else class="login-form" @submit="submit">
      <view class="field-group">
        <text class="field-label">账号</text>
        <input v-model="username" class="field-input" maxlength="64"
               placeholder="请输入机构管理员或教师账号" :disabled="submitting" />
      </view>
      <view class="field-group">
        <text class="field-label">密码</text>
        <input v-model="password" class="field-input" maxlength="64" password
               placeholder="请输入密码" :disabled="submitting" />
      </view>
      <text v-if="errorMessage" class="error-message">{{ errorMessage }}</text>
      <button class="submit-button" form-type="submit" :loading="submitting" :disabled="submitting">
        登录
      </button>
    </form>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { loginOrganizationByPassword } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
import { getOrganizationWorkbenchContext } from '@/api/organization-workbench';
import { getTeacherWorkbenchContext } from '@/api/teacher-workbench';
import {
  getOrCreateOrganizationDeviceId,
  getOrganizationDeviceName,
  saveOrganizationSession
} from '@/session/organization-session';

const username = ref('');
const password = ref('');
const submitting = ref(false);
const capabilityLoading = ref(true);
const serviceEnabled = ref(false);
const errorMessage = ref('');

onLoad(async () => {
  try {
    const capabilities = await getMiniappCapabilities();
    serviceEnabled.value = capabilities.organizationMiniappAuthEnabled;
  } catch {
    serviceEnabled.value = false;
  } finally {
    capabilityLoading.value = false;
  }
});

async function submit(): Promise<void> {
  if (submitting.value) return;
  errorMessage.value = '';
  if (!username.value.trim() || !password.value) {
    errorMessage.value = '请输入账号和密码';
    return;
  }
  submitting.value = true;
  try {
    const session = await loginOrganizationByPassword({
      username: username.value.trim(),
      password: password.value,
      deviceId: getOrCreateOrganizationDeviceId(),
      deviceName: getOrganizationDeviceName()
    });
    saveOrganizationSession(session);
    password.value = '';
    try {
      await getOrganizationWorkbenchContext(session.accessToken);
      await uni.redirectTo({ url: '/pages/organization-home/organization-home' });
    } catch {
      await getTeacherWorkbenchContext(session.accessToken);
      await uni.redirectTo({ url: '/pages/teacher-home/teacher-home' });
    }
  } catch (error) {
    password.value = '';
    if (error instanceof ApiError && error.code === 'FEATURE_DISABLED') {
      serviceEnabled.value = false;
      return;
    }
    errorMessage.value = error instanceof ApiError ? '账号或密码错误' : '网络请求未能完成';
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 72rpx 40rpx 64rpx; box-sizing: border-box; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.page-heading { width: 100%; max-width: 720rpx; margin: 0 auto 64rpx; }
.heading-title { display: block; color: #1c2b28; font-size: 44rpx; font-weight: 700; }
.heading-rule { width: 72rpx; height: 8rpx; margin-top: 20rpx; border-radius: 4rpx; background: #e26d4f; }
.state-text { min-height: 320rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 30rpx; }
.state-text.disabled { color: #9a4b38; }
.login-form { width: 100%; max-width: 720rpx; display: flex; flex-direction: column; gap: 36rpx; margin: 0 auto; }
.field-group { display: flex; flex-direction: column; gap: 14rpx; }
.field-label { color: #40514c; font-size: 26rpx; font-weight: 600; }
.field-input { width: 100%; height: 92rpx; padding: 0 28rpx; box-sizing: border-box; border: 2rpx solid #c8d3cf; border-radius: 12rpx; background: #ffffff; color: #1c2b28; font-size: 32rpx; }
.error-message { min-height: 40rpx; color: #b34f3b; font-size: 26rpx; line-height: 40rpx; word-break: break-word; }
.submit-button { width: 100%; height: 96rpx; display: flex; align-items: center; justify-content: center; border-radius: 12rpx; background: #167c5a; color: #ffffff; font-size: 32rpx; font-weight: 600; }
.submit-button::after { border: 0; }
.submit-button[disabled] { background: #91aaa1; color: #ffffff; }
</style>
