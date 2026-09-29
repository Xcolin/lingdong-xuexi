<template>
  <view class="page-shell">
    <view class="page-heading">
      <text class="heading-title">家长登录</text>
      <view class="heading-rule" />
    </view>

    <view v-if="loading" class="loading-state">正在加载</view>
    <view v-else-if="!context?.enabled" class="disabled-state">服务暂不可用</view>
    <form v-else class="login-form" @submit="submitLogin">
      <!-- #ifdef MP-WEIXIN -->
      <button v-if="context?.wechatEnabled && !wechatBinding" class="wechat-button"
              :loading="wechatSubmitting" :disabled="submitting || wechatSubmitting" @tap="loginByWechat">
        <text class="wechat-mark">微</text>
        <text>微信快捷登录</text>
      </button>
      <view v-if="context?.wechatEnabled && !wechatBinding" class="separator-row">
        <view class="separator-line" /><text>或使用手机号</text><view class="separator-line" />
      </view>
      <!-- #endif -->

      <view v-if="wechatBinding" class="binding-heading">
        <text class="binding-title">绑定家长手机号</text>
        <button class="back-button" :disabled="submitting" @tap="cancelWechatBinding">返回</button>
      </view>

      <view v-else class="mode-switch">
        <button :class="['mode-button', { active: mode === 'SMS' }]" @tap="switchMode('SMS')">验证码登录</button>
        <button :class="['mode-button', { active: mode === 'PASSWORD' }]" @tap="switchMode('PASSWORD')">密码登录</button>
      </view>

      <view class="field-group">
        <text class="field-label">手机号</text>
        <input v-model="mobile" class="field-input" type="number" maxlength="11"
               placeholder="请输入手机号" :disabled="submitting" />
      </view>

      <view v-if="mode === 'SMS' || wechatBinding" class="field-group">
        <text class="field-label">验证码</text>
        <view class="code-row">
          <input v-model="code" class="field-input" type="number" maxlength="6"
                 placeholder="6位验证码" :disabled="submitting" />
          <button class="code-button" :disabled="sendingCode || countdown > 0" :loading="sendingCode" @tap="sendCode">
            {{ countdown > 0 ? `${countdown}秒` : '获取验证码' }}
          </button>
        </view>
      </view>

      <view v-else class="field-group">
        <text class="field-label">密码</text>
        <input v-model="password" class="field-input" password maxlength="64"
               placeholder="请输入密码" :disabled="submitting" />
      </view>

      <checkbox-group v-if="mode === 'SMS' || wechatBinding" class="agreement-row" @change="changeAgreement">
        <label><checkbox value="accepted" color="#167c5a" :checked="agreementAccepted" />我已阅读并同意当前用户协议</label>
      </checkbox-group>

      <text v-if="errorMessage" class="error-message">{{ errorMessage }}</text>
      <button class="submit-button" form-type="submit" :loading="submitting" :disabled="submitting">
        {{ wechatBinding ? '验证并绑定' : '登录' }}
      </button>
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
.page-shell { min-height: 100vh; padding: 72rpx 40rpx 64rpx; box-sizing: border-box; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.page-heading { width: 100%; max-width: 720rpx; margin: 0 auto 64rpx; }
.heading-title { display: block; color: #1c2b28; font-size: 44rpx; font-weight: 700; }
.heading-rule { width: 72rpx; height: 8rpx; margin-top: 20rpx; border-radius: 4rpx; background: #e26d4f; }
.loading-state, .disabled-state { min-height: 320rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 30rpx; }
.disabled-state { color: #9a4b38; }
.login-form { width: 100%; max-width: 720rpx; display: flex; flex-direction: column; gap: 36rpx; margin: 0 auto; }
.wechat-button { width: 100%; height: 96rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; border-radius: 12rpx; background: #07c160; color: #ffffff; font-size: 30rpx; font-weight: 600; }
.wechat-button::after { border: 0; }
.wechat-button[disabled] { background: #8fcfac; color: #ffffff; }
.wechat-mark { width: 48rpx; height: 48rpx; display: flex; align-items: center; justify-content: center; border: 2rpx solid #ffffff; border-radius: 50%; font-size: 24rpx; }
.separator-row { height: 36rpx; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 20rpx; color: #708078; font-size: 24rpx; }
.separator-line { height: 2rpx; background: #d4ded9; }
.binding-heading { min-height: 76rpx; display: flex; align-items: center; justify-content: space-between; border-bottom: 2rpx solid #dbe3df; }
.binding-title { color: #1c2b28; font-size: 32rpx; font-weight: 700; }
.back-button { min-width: 112rpx; height: 60rpx; margin: 0; padding: 0 20rpx; background: transparent; color: #167c5a; font-size: 26rpx; }
.back-button::after { border: 0; }
.mode-switch { height: 76rpx; display: grid; grid-template-columns: repeat(2, 1fr); padding: 6rpx; border: 2rpx solid #c8d3cf; border-radius: 12rpx; box-sizing: border-box; background: #e9efec; }
.mode-button { height: 60rpx; display: flex; align-items: center; justify-content: center; padding: 0; border-radius: 8rpx; background: transparent; color: #62756e; font-size: 26rpx; }
.mode-button::after { border: 0; }
.mode-button.active { background: #ffffff; color: #167c5a; font-weight: 600; }
.field-group { display: flex; flex-direction: column; gap: 14rpx; }
.field-label { color: #40514c; font-size: 26rpx; font-weight: 600; }
.field-input { width: 100%; height: 92rpx; padding: 0 28rpx; border: 2rpx solid #c8d3cf; border-radius: 12rpx; box-sizing: border-box; background: #ffffff; color: #1c2b28; font-size: 32rpx; }
.code-row { display: grid; grid-template-columns: minmax(0, 1fr) 220rpx; gap: 20rpx; }
.code-button { width: 220rpx; height: 92rpx; display: flex; align-items: center; justify-content: center; padding: 0; border-radius: 12rpx; background: #ffffff; color: #167c5a; font-size: 26rpx; }
.code-button::after { border: 2rpx solid #167c5a; border-radius: 12rpx; }
.agreement-row { color: #40514c; font-size: 26rpx; line-height: 44rpx; }
.agreement-row checkbox { transform: scale(.82); }
.error-message { min-height: 40rpx; color: #b34f3b; font-size: 26rpx; line-height: 40rpx; word-break: break-word; }
.submit-button { width: 100%; height: 96rpx; display: flex; align-items: center; justify-content: center; border-radius: 12rpx; background: #167c5a; color: #ffffff; font-size: 32rpx; font-weight: 600; }
.submit-button::after { border: 0; }
.submit-button[disabled] { background: #91aaa1; color: #ffffff; }
</style>
