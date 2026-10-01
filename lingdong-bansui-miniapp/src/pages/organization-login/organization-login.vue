<template>
  <view class="ld-page">
    <view class="ld-heading">
      <text class="ld-heading-title">机构与教师登录</text>
      <view class="ld-heading-rule" />
      <text class="ld-heading-sub">使用机构管理员或教师账号密码登录工作台</text>
    </view>

    <view v-if="capabilityLoading" class="ld-loading">正在加载</view>
    <view v-else-if="!serviceEnabled" class="ld-empty">
      <text class="ld-empty-main">服务暂不可用</text>
      <text class="ld-empty-sub">请联系管理员确认机构小程序登录开关</text>
    </view>
    <form v-else class="ld-card" @submit="submit">
      <view class="ld-field first-field">
        <text class="ld-field-label">账号</text>
        <input v-model="username" class="ld-input" maxlength="64"
               placeholder="请输入机构管理员或教师账号" :disabled="submitting" />
      </view>
      <view class="ld-field">
        <text class="ld-field-label">密码</text>
        <input v-model="password" class="ld-input" maxlength="64" password
               placeholder="请输入密码" :disabled="submitting" />
      </view>
      <text v-if="errorMessage" class="ld-error-text error-gap">{{ errorMessage }}</text>
      <button class="ld-btn ld-btn-primary submit-button" form-type="submit" :loading="submitting" :disabled="submitting">
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
.first-field { margin-top: 0; }
.error-gap { margin-top: 24rpx; }
.submit-button { margin-top: 36rpx; }
</style>
