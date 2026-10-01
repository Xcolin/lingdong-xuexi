<template>
  <view class="ld-page">
    <view class="ld-heading">
      <text class="ld-heading-title">家长登录</text>
      <view class="ld-heading-rule" />
    </view>

    <view v-if="loading" class="ld-loading">正在加载</view>
    <view v-else-if="!context?.enabled" class="ld-empty">
      <text class="ld-empty-main">服务暂不可用</text>
      <text class="ld-empty-sub">请联系管理员确认家长登录开关</text>
    </view>
    <form v-else @submit="submitLogin">
      <!-- #ifdef MP-WEIXIN -->
      <button v-if="context?.wechatEnabled && !wechatBinding" class="ld-btn ld-btn-primary wechat-button"
              :loading="wechatSubmitting" :disabled="submitting || wechatSubmitting" @tap="loginByWechat">
        <text class="wechat-mark">微</text>
        <text>微信快捷登录</text>
      </button>
      <view v-if="context?.wechatEnabled && !wechatBinding" class="separator-row">
        <view class="separator-line" /><text class="separator-text">或使用手机号</text><view class="separator-line" />
      </view>
      <!-- #endif -->

      <view v-if="wechatBinding" class="ld-card binding-band">
        <text class="ld-card-title">绑定家长手机号</text>
        <text class="ld-card-sub">完成验证后即绑定当前微信</text>
        <button class="ld-btn ld-btn-ghost back-button" :disabled="submitting" @tap="cancelWechatBinding">返回</button>
      </view>

      <view v-else class="ld-card">
        <view class="ld-tabs tabs-in-card">
          <button :class="['ld-tab', { active: mode === 'SMS' }]" @tap="switchMode('SMS')">验证码登录</button>
          <button :class="['ld-tab', { active: mode === 'PASSWORD' }]" @tap="switchMode('PASSWORD')">密码登录</button>
        </view>

        <view class="ld-field first-field">
          <text class="ld-field-label">手机号</text>
          <input v-model="mobile" class="ld-input" type="number" maxlength="11"
                 placeholder="请输入手机号" :disabled="submitting" />
        </view>

        <view v-if="mode === 'SMS' || wechatBinding" class="ld-field code-field">
          <text class="ld-field-label">验证码</text>
          <view class="code-row">
            <input v-model="code" class="ld-input code-input" type="number" maxlength="6"
                   placeholder="6位验证码" :disabled="submitting" />
            <button class="code-button" :disabled="sendingCode || countdown > 0" :loading="sendingCode" @tap="sendCode">
              {{ countdown > 0 ? `${countdown}秒` : '获取验证码' }}
            </button>
          </view>
        </view>

        <view v-else class="ld-field">
          <text class="ld-field-label">密码</text>
          <input v-model="password" class="ld-input" password maxlength="64"
                 placeholder="请输入密码" :disabled="submitting" />
        </view>

        <checkbox-group v-if="mode === 'SMS' || wechatBinding" class="agreement-row" @change="changeAgreement">
          <label class="agreement-label">
            <checkbox value="accepted" color="#167c5a" :checked="agreementAccepted" />
            <text class="agreement-text">我已阅读并同意当前用户协议</text>
          </label>
        </checkbox-group>

        <text v-if="errorMessage" class="ld-error-text error-gap">{{ errorMessage }}</text>
        <button class="ld-btn ld-btn-primary submit-button" form-type="submit" :loading="submitting" :disabled="submitting">
          {{ wechatBinding ? '验证并绑定' : '登录' }}
        </button>
      </view>
    </form>
  </view>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  bindParentWechat,
  exchangeParentWechatSession,
  getParentAuthContext,
  issueParentSmsCode,
  issueParentWechatBindingSmsCode,
  loginParentByPassword,
  loginParentBySms,
  type ParentAuthContext,
  type ParentSessionResult
} from '@/api/auth';
import {
  getOrCreateParentDeviceId,
  getParentDeviceName,
  saveParentSession
} from '@/session/parent-session';

const loading = ref(true);
const context = ref<ParentAuthContext | null>(null);
const mode = ref<'SMS' | 'PASSWORD'>('SMS');
const mobile = ref('');
const code = ref('');
const password = ref('');
const agreementAccepted = ref(false);
const submitting = ref(false);
const wechatSubmitting = ref(false);
const wechatBinding = ref(false);
const bindingTicket = ref('');
const sendingCode = ref(false);
const countdown = ref(0);
const errorMessage = ref('');
const deviceId = getOrCreateParentDeviceId();
let timer: ReturnType<typeof setInterval> | undefined;

onLoad(async () => {
  try {
    context.value = await getParentAuthContext();
  } catch {
    context.value = null;
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => {
  code.value = '';
  password.value = '';
  if (timer) clearInterval(timer);
});

async function sendCode(): Promise<void> {
  if (!validateMobile() || sendingCode.value || countdown.value > 0) return;
  sendingCode.value = true;
  errorMessage.value = '';
  try {
    const result = wechatBinding.value
      ? await issueParentWechatBindingSmsCode(mobile.value)
      : await issueParentSmsCode(mobile.value);
    startCountdown(result.retryAfterSeconds);
  } catch (error) {
    handleRequestError(error, '验证码发送失败');
  } finally {
    sendingCode.value = false;
  }
}

async function submitLogin(): Promise<void> {
  if (!context.value?.enabled || !validateMobile()) return;
  if ((mode.value === 'SMS' || wechatBinding.value) && !/^\d{6}$/.test(code.value)) {
    errorMessage.value = '请输入6位验证码';
    return;
  }
  if ((mode.value === 'SMS' || wechatBinding.value) && !agreementAccepted.value) {
    errorMessage.value = '请先同意当前用户协议';
    return;
  }
  if (!wechatBinding.value && mode.value === 'PASSWORD' && !password.value) {
    errorMessage.value = '请输入密码';
    return;
  }

  submitting.value = true;
  errorMessage.value = '';
  try {
    const common = { mobile: mobile.value, deviceId, deviceName: getParentDeviceName() };
    const result = wechatBinding.value
      ? await bindParentWechat({
          bindingTicket: bindingTicket.value,
          ...common,
          smsCode: code.value,
          agreementAccepted: true,
          agreementVersion: context.value.agreementVersion
        })
      : mode.value === 'SMS'
        ? await loginParentBySms({
          ...common,
          code: code.value,
          agreementAccepted: true,
          agreementVersion: context.value.agreementVersion
        })
        : await loginParentByPassword({ ...common, password: password.value });
    saveParentSession(result.session, mobile.value);
    wechatBinding.value = false;
    bindingTicket.value = '';
    clearSecrets();
    await routeAfterLogin(result);
  } catch (error) {
    clearSecrets();
    handleRequestError(error, '登录未能完成');
  } finally {
    submitting.value = false;
  }
}

async function loginByWechat(): Promise<void> {
  if (!context.value?.wechatEnabled || wechatSubmitting.value) return;
  wechatSubmitting.value = true;
  errorMessage.value = '';
  try {
    const temporaryCode = await getWechatTemporaryCode();
    const result = await exchangeParentWechatSession({
      temporaryCode,
      deviceId,
      deviceName: getParentDeviceName()
    });
    if (result.bindingRequired && result.bindingTicket) {
      bindingTicket.value = result.bindingTicket;
      wechatBinding.value = true;
      mode.value = 'SMS';
      clearSecrets();
      return;
    }
    if (!result.session) throw new Error('微信登录结果无效，请使用手机号登录');
    saveParentSession(result.session.session, mobile.value);
    await routeAfterLogin(result.session);
  } catch (error) {
    handleRequestError(error, '微信登录未能完成，请使用手机号登录');
  } finally {
    wechatSubmitting.value = false;
  }
}

function getWechatTemporaryCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (result) => result.code ? resolve(result.code) : reject(new Error('微信授权未能完成')),
      fail: () => reject(new Error('微信授权未能完成'))
    });
  });
}

function cancelWechatBinding(): void {
  wechatBinding.value = false;
  bindingTicket.value = '';
  agreementAccepted.value = false;
  clearSecrets();
  errorMessage.value = '';
}

function routeAfterLogin(result: ParentSessionResult): Promise<unknown> {
  const url = result.onboardingRequired || result.agreementAcceptanceRequired
    ? '/pages/parent-onboarding/parent-onboarding'
    : '/pages/parent-home/parent-home';
  return uni.redirectTo({ url });
}

function validateMobile(): boolean {
  if (/^1[3-9]\d{9}$/.test(mobile.value)) return true;
  errorMessage.value = '手机号格式不正确';
  return false;
}

function changeAgreement(event: { detail: { value: string[] } }): void {
  agreementAccepted.value = event.detail.value.includes('accepted');
}

function switchMode(nextMode: 'SMS' | 'PASSWORD'): void {
  mode.value = nextMode;
  clearSecrets();
  errorMessage.value = '';
}

function clearSecrets(): void {
  code.value = '';
  password.value = '';
}

function startCountdown(seconds: number): void {
  countdown.value = Math.max(1, seconds);
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1);
    if (countdown.value === 0 && timer) {
      clearInterval(timer);
      timer = undefined;
    }
  }, 1000);
}

function handleRequestError(error: unknown, fallback: string): void {
  errorMessage.value = error instanceof Error ? error.message : fallback;
  if (errorMessage.value === '认证失败') errorMessage.value = '手机号或密码错误';
}
</script>

<style lang="scss" scoped>
.wechat-button { background: #07c160; box-shadow: 0 6rpx 16rpx rgba(7, 193, 96, 0.28); }
.wechat-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  margin-right: 14rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.22);
  font-size: 24rpx;
}
.separator-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 36rpx 8rpx;
}
.separator-line { flex: 1; height: 2rpx; background: $ld-line; }
.separator-text { color: $ld-text-muted; font-size: $ld-font-caption; }
.binding-band { display: flex; flex-direction: column; }
.back-button { margin-top: 28rpx; }
.tabs-in-card { margin-top: 0; }
.first-field { margin-top: 28rpx; }
.code-field { margin-top: $ld-gap-block; }
.code-row { display: flex; gap: 20rpx; margin-top: 14rpx; }
.code-input { flex: 1; margin-top: 0; }
.code-button {
  width: 220rpx;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border-radius: $ld-radius-md;
  background: $ld-primary-soft;
  color: $ld-primary;
  font-size: $ld-font-caption;
  font-weight: 600;
}
.code-button::after { border: 0; }
.code-button[disabled] { background: #f0f3f1; color: $ld-text-muted; }
.agreement-row { margin-top: 28rpx; }
.agreement-label { display: flex; align-items: center; }
.agreement-text { color: $ld-text-secondary; font-size: $ld-font-caption; }
.error-gap { margin-top: 20rpx; }
.submit-button { margin-top: 32rpx; }
</style>
