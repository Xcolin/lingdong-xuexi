<template>
  <view class="ld-page">
    <view class="ld-heading">
      <text class="ld-heading-title">学生登录</text>
      <view class="ld-heading-rule" />
    </view>

    <view v-if="capabilityLoading" class="ld-loading">正在加载</view>
    <view v-else-if="!serviceEnabled" class="ld-empty">
      <text class="ld-empty-main">服务暂不可用</text>
      <text class="ld-empty-sub">请联系管理员确认学生登录开关</text>
    </view>
    <template v-else>
      <view v-if="modeCount > 1" class="ld-tabs">
        <button v-if="studentLoginEnabled" :class="['ld-tab', { active: loginMode === 'ACCOUNT' }]" @tap="switchMode('ACCOUNT')">账号登录</button>
        <button v-if="studentQrLoginEnabled" :class="['ld-tab', { active: loginMode === 'QR' }]" @tap="switchMode('QR')">扫码登录</button>
        <!-- #ifdef MP-WEIXIN -->
        <button v-if="studentWechatAuthEnabled" :class="['ld-tab', { active: loginMode === 'WECHAT' }]" @tap="switchMode('WECHAT')">微信登录</button>
        <!-- #endif -->
      </view>

      <view class="ld-card">
        <view v-if="loginMode === 'ACCOUNT'" class="ld-field first-field">
          <text class="ld-field-label">学生账号</text>
          <input v-model="studentAccount" class="ld-input" type="number" maxlength="8"
                 placeholder="8位学生账号" :disabled="submitting" />
        </view>

        <view v-else-if="loginMode === 'QR'" class="qr-login-section">
          <button v-if="!qrContent" class="ld-btn ld-btn-secondary" :disabled="submitting" @tap="scanLoginQr">扫描登录二维码</button>
          <view v-else class="scan-success">
            <text class="scan-success-text">二维码已识别</text>
            <button class="rescan-button" :disabled="submitting" @tap="scanLoginQr">重新扫码</button>
          </view>
        </view>

        <!-- #ifdef MP-WEIXIN -->
        <view v-else class="qr-login-section">
          <button v-if="!wechatBindingTicket" class="ld-btn wechat-login-button" :disabled="submitting" @tap="submitLogin">
            微信授权登录
          </button>
          <text v-else-if="!wechatVerificationTicket" class="wechat-binding-hint">
            首次绑定需验证学生账号、登录码和主监护人手机号
          </text>
          <text v-else class="wechat-binding-hint">
            验证码已发送至 {{ wechatMaskedMobile }}
          </text>
        </view>
        <!-- #endif -->

        <view v-if="loginMode !== 'WECHAT' || (wechatBindingTicket && !wechatVerificationTicket)" class="ld-field">
          <text class="ld-field-label">登录码</text>
          <input v-model="loginCode" class="ld-input" type="number" maxlength="4" password
                 placeholder="4位登录码" :disabled="submitting" />
        </view>

        <view v-if="loginMode === 'WECHAT' && wechatVerificationTicket" class="ld-field">
          <text class="ld-field-label">主监护人短信验证码</text>
          <input v-model="wechatSmsCode" class="ld-input" type="number" maxlength="6"
                 placeholder="6位短信验证码" :disabled="submitting" />
        </view>

        <view v-if="captchaVisible" class="captcha-section">
          <view class="ld-field captcha-field">
            <text class="ld-field-label">图形验证码</text>
            <input v-model="captchaAnswer" class="ld-input" maxlength="8"
                   placeholder="验证码" :disabled="submitting" />
          </view>
          <button class="captcha-image-button" :disabled="captchaLoading" @tap="refreshCaptcha">
            <image v-if="captchaImage" class="captcha-image" :src="captchaImage" mode="aspectFit" />
            <text v-else>{{ captchaLoading ? '加载中' : '刷新' }}</text>
          </button>
        </view>

        <text v-if="errorMessage" class="ld-error-text error-gap">{{ errorMessage }}</text>
        <button class="ld-btn ld-btn-primary submit-button" form-type="submit" :loading="submitting"
                :disabled="submitting || locked || (loginMode === 'QR' && !qrContent)" @tap="submitLogin">
          {{ locked ? lockedText : submitButtonText }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { ApiError } from '@/api/http';
import { getMiniappCapabilities } from '@/api/capability';
import {
  bindStudentWechat,
  exchangeStudentWechatSession,
  issueStudentCaptcha,
  issueStudentQrCaptcha,
  issueStudentWechatBindingCode,
  loginStudentByCode,
  loginStudentByQr
} from '@/api/auth';
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
const studentQrLoginEnabled = ref(false);
const studentWechatAuthEnabled = ref(false);
const loginMode = ref<'ACCOUNT' | 'QR' | 'WECHAT'>('ACCOUNT');
const qrContent = ref('');
const qrCaptchaRequired = ref(false);
const errorMessage = ref('');
const wechatBindingTicket = ref('');
const wechatVerificationTicket = ref('');
const wechatMaskedMobile = ref('');
const wechatSmsCode = ref('');
const lockedUntil = ref<Date | null>(null);
const now = ref(Date.now());
const deviceId = getOrCreateDeviceId();
let timer: ReturnType<typeof setInterval> | undefined;

const locked = computed(() => Boolean(lockedUntil.value && lockedUntil.value.getTime() > now.value));
const serviceEnabled = computed(() => studentLoginEnabled.value || studentQrLoginEnabled.value || studentWechatAuthEnabled.value);
const modeCount = computed(() => Number(studentLoginEnabled.value)
  + Number(studentQrLoginEnabled.value) + Number(studentWechatAuthEnabled.value));
const submitButtonText = computed(() => {
  // #ifdef MP-WEIXIN
  if (loginMode.value !== 'WECHAT') return '登录';
  if (!wechatBindingTicket.value) return '微信授权登录';
  return wechatVerificationTicket.value ? '验证并绑定' : '发送主监护人验证码';
  // #endif
  return '登录';
});
const lockedText = computed(() => {
  if (!lockedUntil.value) return '暂时锁定';
  const seconds = Math.max(1, Math.ceil((lockedUntil.value.getTime() - now.value) / 1000));
  return `${seconds}秒后重试`;
});

onLoad(async () => {
  try {
    const capabilities = await getMiniappCapabilities();
    studentLoginEnabled.value = capabilities.studentCodeLoginEnabled;
    studentQrLoginEnabled.value = capabilities.studentQrLoginEnabled;
    // #ifdef MP-WEIXIN
    studentWechatAuthEnabled.value = capabilities.studentWechatAuthEnabled === true;
    // #endif
    loginMode.value = studentWechatAuthEnabled.value ? 'WECHAT'
      : capabilities.studentQrLoginEnabled ? 'QR' : 'ACCOUNT';
  } catch {
    studentLoginEnabled.value = false;
  } finally {
    capabilityLoading.value = false;
  }
});

onBeforeUnmount(() => {
  loginCode.value = '';
  captchaAnswer.value = '';
  qrContent.value = '';
  resetWechatBinding();
  if (timer) clearInterval(timer);
});

async function submitLogin(): Promise<void> {
  errorMessage.value = '';
  if (loginMode.value === 'WECHAT') {
    await submitWechatLogin();
    return;
  }
  if (loginMode.value === 'ACCOUNT' && !/^\d{8}$/.test(studentAccount.value)) {
    errorMessage.value = '账号或登录码格式不正确';
    return;
  }
  if (loginMode.value === 'QR' && !qrContent.value) {
    errorMessage.value = '请先扫描登录二维码';
    return;
  }
  if (!/^\d{4}$/.test(loginCode.value)) {
    errorMessage.value = '请输入4位登录码';
    return;
  }
  if (captchaVisible.value && !captchaAnswer.value.trim()) {
    errorMessage.value = '请输入图形验证码';
    return;
  }

  submitting.value = true;
  try {
    if (loginMode.value === 'QR') {
      const session = await loginStudentByQr({
        qrContent: qrContent.value,
        loginCode: loginCode.value,
        deviceId,
        deviceName: getDeviceName(),
        captchaChallengeId: captchaChallengeId.value || undefined,
        captchaAnswer: captchaAnswer.value.trim() || undefined
      });
      clearSensitiveInputs();
      saveStudentSession(session, session.studentAccount);
      await uni.redirectTo({ url: '/pages/student-home/student-home' });
      return;
    }
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

async function submitWechatLogin(): Promise<void> {
  if (!studentWechatAuthEnabled.value || submitting.value) return;
  if (wechatBindingTicket.value && !wechatVerificationTicket.value) {
    if (!/^\d{8}$/.test(studentAccount.value) || !/^\d{4}$/.test(loginCode.value)) {
      errorMessage.value = '请输入8位学生账号和4位登录码';
      return;
    }
    if (captchaVisible.value && !captchaAnswer.value.trim()) {
      errorMessage.value = '请输入图形验证码';
      return;
    }
  }
  if (wechatVerificationTicket.value && !/^\d{6}$/.test(wechatSmsCode.value)) {
    errorMessage.value = '请输入6位短信验证码';
    return;
  }

  submitting.value = true;
  try {
    if (!wechatBindingTicket.value) {
      const temporaryCode = await getWechatTemporaryCode();
      const exchange = await exchangeStudentWechatSession({
        temporaryCode, deviceId, deviceName: getDeviceName()
      });
      if (exchange.session) {
        saveStudentSession(exchange.session, exchange.session.studentAccount);
        await uni.redirectTo({ url: '/pages/student-home/student-home' });
        return;
      }
      wechatBindingTicket.value = exchange.bindingTicket || '';
      if (!wechatBindingTicket.value) throw new Error('微信绑定凭证未返回');
      return;
    }
    if (!wechatVerificationTicket.value) {
      const challenge = await issueStudentWechatBindingCode({
        bindingTicket: wechatBindingTicket.value,
        studentAccount: studentAccount.value,
        loginCode: loginCode.value,
        deviceId,
        deviceName: getDeviceName(),
        captchaChallengeId: captchaChallengeId.value || undefined,
        captchaAnswer: captchaAnswer.value.trim() || undefined
      });
      wechatVerificationTicket.value = challenge.verificationTicket;
      wechatMaskedMobile.value = challenge.maskedMobile;
      loginCode.value = '';
      captchaAnswer.value = '';
      captchaVisible.value = false;
      return;
    }
    const session = await bindStudentWechat({
      verificationTicket: wechatVerificationTicket.value,
      smsCode: wechatSmsCode.value,
      deviceId,
      deviceName: getDeviceName()
    });
    saveStudentSession(session, session.studentAccount);
    resetWechatBinding();
    await uni.redirectTo({ url: '/pages/student-home/student-home' });
  } catch (error) {
    await handleLoginError(error);
  } finally {
    submitting.value = false;
  }
}

function getWechatTemporaryCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: (result) => result.code ? resolve(result.code) : reject(new Error('微信授权未完成')),
      fail: () => reject(new Error('微信授权未完成'))
    });
    // #endif
    // #ifndef MP-WEIXIN
    reject(new Error('当前客户端不支持微信授权'));
    // #endif
  });
}

async function handleLoginError(error: unknown): Promise<void> {
  loginCode.value = '';
  captchaAnswer.value = '';
  if (!(error instanceof ApiError)) {
    errorMessage.value = '网络请求未能完成';
    return;
  }
  if (error.code === 'CAPTCHA_REQUIRED') {
    if (loginMode.value === 'QR') {
      qrContent.value = '';
      qrCaptchaRequired.value = true;
      captchaVisible.value = false;
      errorMessage.value = '请重新扫码后完成图形验证码';
    } else {
      captchaVisible.value = true;
      errorMessage.value = '请完成图形验证码';
      await refreshCaptcha();
    }
    return;
  }
  if (error.code === 'STUDENT_ACCOUNT_LOCKED' && error.lockedUntil) {
    lockedUntil.value = new Date(error.lockedUntil);
    startLockTimer();
    errorMessage.value = '账号暂时锁定';
    if (loginMode.value === 'QR') qrContent.value = '';
    return;
  }
  if (error.code === 'FEATURE_DISABLED') {
    if (loginMode.value === 'WECHAT') {
      studentWechatAuthEnabled.value = false;
      resetWechatBinding();
      loginMode.value = studentQrLoginEnabled.value ? 'QR' : 'ACCOUNT';
    } else if (loginMode.value === 'QR') {
      studentQrLoginEnabled.value = false;
      if (studentLoginEnabled.value) loginMode.value = 'ACCOUNT';
    } else {
      studentLoginEnabled.value = false;
      if (studentQrLoginEnabled.value) loginMode.value = 'QR';
    }
    clearSensitiveInputs();
    errorMessage.value = '';
    return;
  }
  if (loginMode.value === 'WECHAT') {
    if (error.code === 'CAPTCHA_REQUIRED') {
      captchaVisible.value = true;
      errorMessage.value = '请完成图形验证码';
      await refreshCaptcha();
      return;
    }
    if (error.code === 'STUDENT_WECHAT_TICKET_INVALID'
        || error.code === 'STUDENT_WECHAT_BINDING_UNAVAILABLE') {
      resetWechatBinding();
      errorMessage.value = error.message || '微信绑定未完成，请使用账号登录';
      return;
    }
    errorMessage.value = error.message || '微信登录未完成，请使用账号登录';
    return;
  }
  if (loginMode.value === 'QR') {
    qrContent.value = '';
    captchaVisible.value = false;
    errorMessage.value = error.code === 'STUDENT_QR_TICKET_INVALID'
      ? '登录二维码已失效，请重新扫码'
      : '登录码错误，请重新扫码';
    return;
  }
  errorMessage.value = error.message || '账号或登录码错误';
}

async function refreshCaptcha(): Promise<void> {
  if (loginMode.value === 'QR') {
    await refreshQrCaptcha();
    return;
  }
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

async function scanLoginQr(): Promise<void> {
  errorMessage.value = '';
  captchaVisible.value = false;
  captchaAnswer.value = '';
  captchaChallengeId.value = '';
  captchaImage.value = '';
  try {
    const content = await scanQrContent();
    if (!content.startsWith('lingdong-learning://student-login?ticket=')) {
      throw new Error('不是灵动伴随登录二维码');
    }
    qrContent.value = content;
    if (qrCaptchaRequired.value) {
      await refreshQrCaptcha();
    }
  } catch (error) {
    qrContent.value = '';
    errorMessage.value = error instanceof Error ? error.message : '扫码未完成';
  }
}

async function refreshQrCaptcha(): Promise<void> {
  if (!qrContent.value) {
    errorMessage.value = '请重新扫描登录二维码';
    return;
  }
  captchaLoading.value = true;
  try {
    const challenge = await issueStudentQrCaptcha(qrContent.value, deviceId);
    captchaChallengeId.value = challenge.challengeId;
    captchaImage.value = challenge.imageBase64;
    captchaVisible.value = true;
  } catch (error) {
    qrContent.value = '';
    captchaVisible.value = false;
    errorMessage.value = error instanceof Error ? error.message : '验证码加载失败';
  } finally {
    captchaLoading.value = false;
  }
}

function scanQrContent(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.scanCode({
      scanType: ['qrCode'],
      success: (result) => resolve(result.result),
      fail: () => reject(new Error('扫码未完成'))
    });
  });
}

function switchMode(mode: 'ACCOUNT' | 'QR' | 'WECHAT'): void {
  loginMode.value = mode;
  clearSensitiveInputs();
  errorMessage.value = '';
}

function clearSensitiveInputs(): void {
  loginCode.value = '';
  captchaAnswer.value = '';
  captchaChallengeId.value = '';
  captchaImage.value = '';
  captchaVisible.value = false;
  qrContent.value = '';
  qrCaptchaRequired.value = false;
  resetWechatBinding();
}

function resetWechatBinding(): void {
  wechatBindingTicket.value = '';
  wechatVerificationTicket.value = '';
  wechatMaskedMobile.value = '';
  wechatSmsCode.value = '';
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
.first-field { margin-top: 0; }

.qr-login-section { min-height: 100rpx; display: flex; align-items: center; }
.scan-success {
  width: 100%;
  min-height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28rpx;
  border-radius: $ld-radius-md;
  background: $ld-primary-soft;
}
.scan-success-text { color: $ld-primary; font-size: 28rpx; font-weight: 600; }
.rescan-button {
  height: 60rpx;
  display: flex;
  align-items: center;
  margin: 0;
  padding: 0 24rpx;
  border-radius: $ld-radius-pill;
  background: $ld-card;
  color: $ld-primary;
  font-size: $ld-font-caption;
}
.rescan-button::after { border: 2rpx solid $ld-primary-border; border-radius: $ld-radius-pill; }

.wechat-login-button { background: #07c160; box-shadow: 0 6rpx 16rpx rgba(7, 193, 96, 0.28); }
.wechat-binding-hint { color: $ld-text-secondary; font-size: 26rpx; line-height: 42rpx; }

.captcha-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240rpx;
  align-items: end;
  gap: 20rpx;
}
.captcha-field { margin-top: $ld-gap-block; }
.captcha-image-button {
  width: 240rpx;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 14rpx 0 0;
  padding: 0;
  border-radius: $ld-radius-md;
  background: #fbfdfc;
  border: 2rpx solid $ld-line-strong;
  color: $ld-primary;
  font-size: $ld-font-caption;
}
.captcha-image-button::after { border: 0; }
.captcha-image { width: 228rpx; height: 84rpx; }

.error-gap { margin-top: $ld-gap-block; }
.submit-button { margin-top: $ld-gap-block; }
</style>