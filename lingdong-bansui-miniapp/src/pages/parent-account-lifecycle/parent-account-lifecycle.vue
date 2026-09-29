<template>
  <view class="page-shell">
    <view v-if="loading && !state" class="state-view">加载中</view>
    <view v-else-if="errorMessage" class="state-view error-state">
      <text>{{ errorMessage }}</text>
      <button class="secondary-command" @tap="loadState">重试</button>
    </view>
    <template v-else-if="state">
      <view class="summary-band">
        <text class="page-title">账号与手机号</text>
        <view class="summary-row"><text>当前手机号</text><text>{{ state.maskedMobile }}</text></view>
        <view class="summary-row"><text>活动学生关系</text><text>{{ state.activeStudentRelationshipCount }}</text></view>
        <view class="summary-row"><text>注销状态</text><text>{{ cancellationLabel }}</text></view>
      </view>

      <view class="action-band">
        <view class="section-heading">
          <text class="section-title">更换手机号</text>
          <button v-if="!mobileFlowOpen" class="text-command" @tap="startMobileFlow">开始</button>
        </view>
        <view v-if="mobileFlowOpen" class="form-stack">
          <template v-if="mobileStage === 'CURRENT'">
            <text class="field-note">先验证当前手机号 {{ state.maskedMobile }}</text>
            <button class="secondary-command" :disabled="submitting" @tap="sendCurrentCode">发送当前手机号验证码</button>
            <input v-model="currentCode" class="field-input" maxlength="6" type="number" placeholder="当前手机号验证码" />
            <button class="primary-command" :disabled="!sixDigits(currentCode) || submitting" @tap="confirmCurrentMobile">验证当前手机号</button>
          </template>
          <template v-else>
            <input v-model="newMobile" class="field-input" maxlength="11" type="number" placeholder="新手机号" />
            <button class="secondary-command" :disabled="!validMobile(newMobile) || submitting" @tap="sendNewCode">发送新手机号验证码</button>
            <input v-model="newCode" class="field-input" maxlength="6" type="number" placeholder="新手机号验证码" />
            <button class="primary-command" :disabled="!sixDigits(newCode) || submitting" @tap="completeMobileChange">确认更换手机号</button>
          </template>
          <button class="text-command cancel-command" :disabled="submitting" @tap="cancelMobileFlow">取消</button>
        </view>
      </view>

      <view class="danger-band">
        <text class="section-title">账号注销</text>
        <text v-if="state.activeStudentRelationshipCount > 0" class="field-note">请先解除全部学生关系</text>
        <template v-else-if="state.cancellationStatus === 'NONE'">
          <button v-if="!cancellationFlowOpen" class="danger-command" @tap="cancellationFlowOpen = true">申请注销</button>
          <view v-else class="form-stack">
            <text class="field-note">申请后进入 7 天冷静期，期间账号仍可登录并撤销。</text>
            <button class="secondary-command" :disabled="submitting" @tap="sendCancellationCode">发送注销验证码</button>
            <input v-model="cancellationCode" class="field-input" maxlength="6" type="number" placeholder="注销验证码" />
            <input v-model="confirmation" class="field-input" maxlength="4" placeholder="请输入“确认注销”" />
            <button class="danger-command" :disabled="!sixDigits(cancellationCode) || confirmation !== '确认注销' || submitting" @tap="submitCancellation">提交注销申请</button>
            <button class="text-command cancel-command" :disabled="submitting" @tap="cancellationFlowOpen = false">取消</button>
          </view>
        </template>
        <template v-else-if="state.cancellationStatus === 'COOLING_OFF'">
          <text class="field-note">冷静期截止 {{ formatTime(state.coolingEndsAt) }}</text>
          <button class="secondary-command" :disabled="submitting" @tap="confirmRevokeCancellation">撤销注销申请</button>
        </template>
        <text v-else class="warning-text">冷静期已结束，账号待后续注销处理</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  changeParentMobile,
  getParentAccountLifecycle,
  issueCurrentMobileCode,
  issueNewMobileCode,
  issueParentCancellationCode,
  requestParentCancellation,
  revokeParentCancellation,
  verifyCurrentMobile,
  type ParentAccountLifecycleState
} from '@/api/parent-account-lifecycle';
import { clearParentSession, getParentSession, type StoredParentSession } from '@/session/parent-session';

const session = ref<StoredParentSession | null>(null);
const state = ref<ParentAccountLifecycleState | null>(null);
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const mobileFlowOpen = ref(false);
const mobileStage = ref<'CURRENT' | 'NEW'>('CURRENT');
const currentCode = ref('');
const ticket = ref('');
const newMobile = ref('');
const newCode = ref('');
const cancellationFlowOpen = ref(false);
const cancellationCode = ref('');
const confirmation = ref('');

const cancellationLabel = computed(() => {
  if (state.value?.cancellationStatus === 'COOLING_OFF') return '冷静期中';
  if (state.value?.cancellationStatus === 'READY_FOR_FINALIZATION') return '待执行注销';
  return '未申请';
});

onShow(async () => {
  session.value = getParentSession();
  if (!session.value) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    if (!capabilities.parentAccountLifecycleEnabled) {
      await uni.redirectTo({ url: '/pages/parent-home/parent-home' });
      return;
    }
    await loadState();
  } catch (error) {
    errorMessage.value = toMessage(error);
  }
});

async function loadState(): Promise<void> {
  if (!session.value) return;
  loading.value = true;
  errorMessage.value = '';
  try {
    state.value = await getParentAccountLifecycle(session.value.accessToken);
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    loading.value = false;
  }
}

function startMobileFlow(): void {
  mobileFlowOpen.value = true;
  mobileStage.value = 'CURRENT';
  currentCode.value = '';
  ticket.value = '';
  newMobile.value = '';
  newCode.value = '';
}

function cancelMobileFlow(): void {
  mobileFlowOpen.value = false;
}

async function sendCurrentCode(): Promise<void> {
  await execute(async () => {
    await issueCurrentMobileCode(requireToken());
    toast('验证码已发送');
  });
}

async function confirmCurrentMobile(): Promise<void> {
  await execute(async () => {
    const result = await verifyCurrentMobile(requireToken(), currentCode.value);
    ticket.value = result.ticket;
    mobileStage.value = 'NEW';
  });
}

async function sendNewCode(): Promise<void> {
  await execute(async () => {
    await issueNewMobileCode(requireToken(), ticket.value, newMobile.value);
    toast('验证码已发送');
  });
}

async function completeMobileChange(): Promise<void> {
  await execute(async () => {
    await changeParentMobile(requireToken(), ticket.value, newMobile.value, newCode.value);
    clearParentSession();
    toast('手机号已更换，请重新登录');
    await uni.reLaunch({ url: '/pages/parent-login/parent-login' });
  });
}

async function sendCancellationCode(): Promise<void> {
  await execute(async () => {
    await issueParentCancellationCode(requireToken());
    toast('验证码已发送');
  });
}

async function submitCancellation(): Promise<void> {
  await execute(async () => {
    await requestParentCancellation(requireToken(), cancellationCode.value, confirmation.value);
    cancellationFlowOpen.value = false;
    cancellationCode.value = '';
    confirmation.value = '';
    await loadState();
    toast('注销申请已进入冷静期');
  });
}

function confirmRevokeCancellation(): void {
  uni.showModal({
    title: '撤销注销申请',
    content: '账号将继续正常使用。',
    success: (result) => { if (result.confirm) void revokeCancellationRequest(); }
  });
}

async function revokeCancellationRequest(): Promise<void> {
  await execute(async () => {
    await revokeParentCancellation(requireToken());
    await loadState();
    toast('注销申请已撤销');
  });
}

async function execute(action: () => Promise<void>): Promise<void> {
  if (submitting.value) return;
  submitting.value = true;
  try {
    await action();
  } catch (error) {
    toast(toMessage(error), 'none');
  } finally {
    submitting.value = false;
  }
}

function requireToken(): string {
  if (!session.value) throw new Error('家长会话已失效');
  return session.value.accessToken;
}

function validMobile(value: string): boolean {
  return /^1[3-9]\d{9}$/.test(value);
}

function sixDigits(value: string): boolean {
  return /^\d{6}$/.test(value);
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '';
}

function toast(title: string, icon: 'success' | 'none' = 'success'): void {
  uni.showToast({ title, icon });
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; background: #f4f7f5; color: #1c2b28; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.state-view { padding: 120rpx 40rpx; text-align: center; color: #708078; font-size: 28rpx; }
.error-state { color: #9a4b38; }
.summary-band, .action-band, .danger-band { padding: 40rpx; background: #ffffff; border-bottom: 2rpx solid #dbe3df; }
.action-band, .danger-band { margin-top: 24rpx; }
.page-title { display: block; margin-bottom: 32rpx; font-size: 40rpx; font-weight: 700; }
.summary-row { display: flex; justify-content: space-between; gap: 24rpx; padding: 22rpx 0; border-top: 2rpx solid #edf1ef; font-size: 28rpx; }
.section-heading { display: flex; align-items: center; justify-content: space-between; }
.section-title { display: block; font-size: 32rpx; font-weight: 700; }
.form-stack { display: flex; flex-direction: column; gap: 24rpx; margin-top: 28rpx; }
.field-note, .warning-text { display: block; color: #708078; font-size: 25rpx; line-height: 1.6; }
.warning-text { margin-top: 24rpx; color: #9a4b38; }
.field-input { height: 88rpx; padding: 0 24rpx; border: 2rpx solid #cbd7d1; border-radius: 8rpx; box-sizing: border-box; background: #ffffff; font-size: 28rpx; }
.primary-command, .secondary-command, .danger-command { width: 100%; min-height: 84rpx; margin: 0; border-radius: 8rpx; font-size: 28rpx; }
.primary-command { background: #167c5a; color: #ffffff; }
.secondary-command { background: #edf5f1; color: #135f47; }
.danger-command { margin-top: 28rpx; background: #9a4b38; color: #ffffff; }
.text-command { width: auto; min-height: 56rpx; margin: 0; padding: 0 16rpx; background: transparent; color: #167c5a; font-size: 26rpx; }
.cancel-command { align-self: center; color: #708078; }
button::after { border: 0; }
button[disabled] { opacity: 0.45; }
</style>
