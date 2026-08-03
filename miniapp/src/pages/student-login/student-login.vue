<template>
  <view class="page-shell">
    <view class="page-heading">
      <text class="heading-title">学生登录</text>
      <view class="heading-rule" />
    </view>

    <view v-if="capabilityLoading" class="loading-state">正在加载</view>
    <view v-else-if="!studentLoginEnabled" class="disabled-state">服务暂不可用</view>
    <form v-else class="login-form" @submit="submitLogin">
      <view class="field-group">
        <text class="field-label">学生账号</text>
        <input v-model="studentAccount" class="field-input" type="number" maxlength="8"
               placeholder="8位学生账号" :disabled="submitting" />
      </view>

      <view class="field-group">
        <text class="field-label">登录码</text>
        <input v-model="loginCode" class="field-input" type="number" maxlength="4" password
               placeholder="4位登录码" :disabled="submitting" />
      </view>

      <view v-if="captchaVisible" class="captcha-section">
        <view class="field-group">
          <text class="field-label">图形验证码</text>
          <input v-model="captchaAnswer" class="field-input" maxlength="8"
                 placeholder="验证码" :disabled="submitting" />
        </view>
        <button class="captcha-image-button" :disabled="captchaLoading" @tap="refreshCaptcha">
          <image v-if="captchaImage" class="captcha-image" :src="captchaImage" mode="aspectFit" />
          <text v-else>{{ captchaLoading ? '加载中' : '刷新' }}</text>
        </button>
      </view>

      <text v-if="errorMessage" class="error-message">{{ errorMessage }}</text>
      <button class="submit-button" form-type="submit" :loading="submitting"
              :disabled="submitting || locked">
        {{ locked ? lockedText : '登录' }}
      </button>
    </form>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { ApiError } from '@/api/http';
import { getMiniappCapabilities } from '@/api/capability';
import { issueStudentCaptcha, loginStudentByCode } from '@/api/auth';
import { getDeviceName, getOrCreateDeviceId, saveStudentSession } from '@/session/student-session';

const studentAccount = ref('');
const loginCode = ref('');
const captchaAnswer = ref('');
const captchaChallengeId = ref('');
const captchaImage = ref('');
const captchaVisible = ref(false);
const captchaLoading = ref(false);
const submitting = ref(false);
const capabilityLoading = ref(true);
const studentLoginEnabled = ref(false);
const errorMessage = ref('');
const lockedUntil = ref<Date | null>(null);
const now = ref(Date.now());
const deviceId = getOrCreateDeviceId();
let timer: ReturnType<typeof setInterval> | undefined;

const locked = computed(() => Boolean(lockedUntil.value && lockedUntil.value.getTime() > now.value));
const lockedText = computed(() => {
  if (!lockedUntil.value) return '暂时锁定';
  const seconds = Math.max(1, Math.ceil((lockedUntil.value.getTime() - now.value) / 1000));
  return `${seconds}秒后重试`;
});

onLoad(async () => {
  try {
    const capabilities = await getMiniappCapabilities();
    studentLoginEnabled.value = capabilities.studentCodeLoginEnabled;
  } catch {
    studentLoginEnabled.value = false;
  } finally {
    capabilityLoading.value = false;
  }
});

onBeforeUnmount(() => {
  loginCode.value = '';
  captchaAnswer.value = '';
  if (timer) clearInterval(timer);
});

async function submitLogin(): Promise<void> {
  errorMessage.value = '';
  if (!/^\d{8}$/.test(studentAccount.value) || !/^\d{4}$/.test(loginCode.value)) {
    errorMessage.value = '账号或登录码格式不正确';
    return;
  }
  if (captchaVisible.value && !captchaAnswer.value.trim()) {
    errorMessage.value = '请输入图形验证码';
    return;
  }

  submitting.value = true;
  try {
    const session = await loginStudentByCode({
      studentAccount: studentAccount.value,
      loginCode: loginCode.value,
      deviceId,
      deviceName: getDeviceName(),
      captchaChallengeId: captchaChallengeId.value || undefined,
      captchaAnswer: captchaAnswer.value.trim() || undefined
    });
    const account = studentAccount.value;
    loginCode.value = '';
    captchaAnswer.value = '';
    saveStudentSession(session, account);
    await uni.redirectTo({ url: '/pages/student-home/student-home' });
  } catch (error) {
    await handleLoginError(error);
  } finally {
    submitting.value = false;
  }
}

async function handleLoginError(error: unknown): Promise<void> {
  loginCode.value = '';
  captchaAnswer.value = '';
  if (!(error instanceof ApiError)) {
    errorMessage.value = '网络请求未能完成';
    return;
  }
  if (error.code === 'CAPTCHA_REQUIRED') {
    captchaVisible.value = true;
    errorMessage.value = '请完成图形验证码';
    await refreshCaptcha();
    return;
  }
  if (error.code === 'STUDENT_ACCOUNT_LOCKED' && error.lockedUntil) {
    lockedUntil.value = new Date(error.lockedUntil);
    startLockTimer();
    errorMessage.value = '账号暂时锁定';
    return;
  }
  if (error.code === 'FEATURE_DISABLED') {
    studentLoginEnabled.value = false;
    errorMessage.value = '';
    return;
  }
  errorMessage.value = error.message || '账号或登录码错误';
}

async function refreshCaptcha(): Promise<void> {
  if (!/^\d{8}$/.test(studentAccount.value)) {
    errorMessage.value = '请输入8位学生账号';
    return;
  }
  captchaLoading.value = true;
  captchaAnswer.value = '';
  try {
    const challenge = await issueStudentCaptcha(studentAccount.value, deviceId);
    captchaChallengeId.value = challenge.challengeId;
    captchaImage.value = challenge.imageBase64;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '验证码加载失败';
  } finally {
    captchaLoading.value = false;
  }
}

function startLockTimer(): void {
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    now.value = Date.now();
    if (!locked.value && timer) {
      clearInterval(timer);
      timer = undefined;
      lockedUntil.value = null;
      errorMessage.value = '';
    }
  }, 1000);
}
</script>

<style lang="scss" scoped>
.page-shell {
  min-height: 100vh;
  padding: 72rpx 40rpx 64rpx;
  box-sizing: border-box;
  background: #f4f7f5;
}

/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */

.page-heading {
  width: 100%;
  max-width: 720rpx;
  margin: 0 auto 64rpx;
}

.heading-title {
  display: block;
  color: #1c2b28;
  font-size: 44rpx;
  font-weight: 700;
}

.heading-rule {
  width: 72rpx;
  height: 8rpx;
  margin-top: 20rpx;
  border-radius: 4rpx;
  background: #e26d4f;
}

.loading-state,
.disabled-state {
  width: 100%;
  max-width: 720rpx;
  min-height: 320rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  color: #708078;
  font-size: 30rpx;
}

.disabled-state { color: #9a4b38; }

.login-form {
  width: 100%;
  max-width: 720rpx;
  display: flex;
  flex-direction: column;
  gap: 36rpx;
  margin: 0 auto;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.field-label {
  color: #40514c;
  font-size: 26rpx;
  font-weight: 600;
}

.field-input {
  width: 100%;
  height: 92rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
  border: 2rpx solid #c8d3cf;
  border-radius: 12rpx;
  background: #ffffff;
  color: #1c2b28;
  font-size: 32rpx;
}

.captcha-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260rpx;
  align-items: end;
  gap: 20rpx;
}

.captcha-image-button {
  width: 260rpx;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 12rpx;
  background: #ffffff;
  color: #167c5a;
  font-size: 26rpx;
}

.captcha-image-button::after {
  border: 2rpx solid #c8d3cf;
  border-radius: 12rpx;
}

.captcha-image {
  width: 248rpx;
  height: 84rpx;
}

.error-message {
  min-height: 40rpx;
  color: #b34f3b;
  font-size: 26rpx;
  line-height: 40rpx;
  word-break: break-word;
}

.submit-button {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 12rpx;
  border-radius: 12rpx;
  background: #167c5a;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 600;
}

.submit-button::after { border: 0; }

.submit-button[disabled] {
  background: #91aaa1;
  color: #ffffff;
}
</style>
