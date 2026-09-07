<template>
  <view class="page-shell">
    <view class="page-heading">
      <text class="heading-title">学生登录</text>
      <view class="heading-rule" />
    </view>

    <view v-if="capabilityLoading" class="loading-state">正在加载</view>
    <view v-else-if="!serviceEnabled" class="disabled-state">服务暂不可用</view>
    <form v-else class="login-form" @submit="submitLogin">
      <view v-if="modeCount > 1" class="login-mode-switch"
            :style="{ gridTemplateColumns: `repeat(${modeCount}, minmax(0, 1fr))` }">
        <button v-if="studentLoginEnabled" :class="['mode-button', { active: loginMode === 'ACCOUNT' }]" @tap="switchMode('ACCOUNT')">账号登录</button>
        <button v-if="studentQrLoginEnabled" :class="['mode-button', { active: loginMode === 'QR' }]" @tap="switchMode('QR')">扫码登录</button>
        <!-- #ifdef MP-WEIXIN -->
        <button v-if="studentWechatAuthEnabled" :class="['mode-button', { active: loginMode === 'WECHAT' }]" @tap="switchMode('WECHAT')">微信登录</button>
        <!-- #endif -->
      </view>

      <view v-if="loginMode === 'ACCOUNT'" class="field-group">
        <text class="field-label">学生账号</text>
        <input v-model="studentAccount" class="field-input" type="number" maxlength="8"
               placeholder="8位学生账号" :disabled="submitting" />
      </view>

      <view v-else-if="loginMode === 'QR'" class="qr-login-section">
        <button v-if="!qrContent" class="scan-button" :disabled="submitting" @tap="scanLoginQr">扫描登录二维码</button>
        <view v-else class="scan-success">
          <text>二维码已识别</text>
          <button class="rescan-button" :disabled="submitting" @tap="scanLoginQr">重新扫码</button>
        </view>
      </view>

      <!-- #ifdef MP-WEIXIN -->
      <view v-else class="wechat-login-section">
        <button v-if="!wechatBindingTicket" class="wechat-login-button" :disabled="submitting" @tap="submitLogin">
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

      <view v-if="loginMode !== 'WECHAT' || (wechatBindingTicket && !wechatVerificationTicket)" class="field-group">
        <text class="field-label">登录码</text>
        <input v-model="loginCode" class="field-input" type="number" maxlength="4" password
               placeholder="4位登录码" :disabled="submitting" />
      </view>

      <view v-if="loginMode === 'WECHAT' && wechatVerificationTicket" class="field-group">
        <text class="field-label">主监护人短信验证码</text>
        <input v-model="wechatSmsCode" class="field-input" type="number" maxlength="6"
               placeholder="6位短信验证码" :disabled="submitting" />
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
              :disabled="submitting || locked || (loginMode === 'QR' && !qrContent)">
        {{ locked ? lockedText : submitButtonText }}
      </button>
    </form>
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
      throw new Error('不是灵动学习登录二维码');
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

.login-mode-switch {
  width: 100%;
  height: 76rpx;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  padding: 6rpx;
  box-sizing: border-box;
  border: 2rpx solid #c8d3cf;
  border-radius: 12rpx;
  background: #e9efec;
}

.mode-button {
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 8rpx;
  background: transparent;
  color: #62756e;
  font-size: 26rpx;
}

.mode-button::after { border: 0; }
.mode-button.active { background: #ffffff; color: #167c5a; font-weight: 600; }

.qr-login-section { min-height: 100rpx; display: flex; align-items: center; }
.scan-button {
  width: 100%;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  background: #ffffff;
  color: #167c5a;
  font-size: 30rpx;
}
.scan-button::after { border: 2rpx solid #167c5a; border-radius: 12rpx; }
.scan-success { width: 100%; display: flex; align-items: center; justify-content: space-between; color: #167c5a; }
.rescan-button { margin: 0; padding: 0 24rpx; background: transparent; color: #167c5a; font-size: 26rpx; }
.rescan-button::after { border: 0; }

.wechat-login-section { min-height: 100rpx; display: flex; align-items: center; }
.wechat-login-button { width: 100%; height: 92rpx; border-radius: 12rpx; background: #07c160; color: #ffffff; font-size: 30rpx; font-weight: 600; }
.wechat-login-button::after { border: 0; }
.wechat-binding-hint { color: #40514c; font-size: 27rpx; line-height: 42rpx; }

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
