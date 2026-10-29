<template>
  <view class="prototype-workspace page-shell">
    <PrototypePageHeader title="账号注销申请" icon="shield"/>
    <view v-if="loading && !state" class="state-view">加载中</view>
    <view v-else-if="errorMessage" class="state-view error-state">
      <text>{{ errorMessage }}</text>
      <button class="text-command" @tap="loadState">重试</button>
    </view>
    <template v-else-if="state">
      <!-- 8.4 对照 07-10：不可恢复警示 -->
      <view class="alert-band">
        <PrototypeIcon name="shield" tone="amber" :size="40"/>
        <view class="alert-copy">
          <text class="alert-title">注销账号后将不可恢复</text>
          <text class="alert-text">所有的个人信息、成长记录、关系链路等将被永久删除，且无法找回。请谨慎操作。</text>
        </view>
      </view>

      <view v-if="state.activeStudentRelationshipCount > 0" class="form-band">
        <text class="block-note">当前账号仍存在 {{ state.activeStudentRelationshipCount }} 条活动学生关系，须先解除全部学生关系后才能申请注销。</text>
        <button class="secondary-command" @tap="goBack">返回账号与手机号</button>
      </view>
      <view v-else-if="state.cancellationStatus === 'COOLING_OFF'" class="form-band">
        <text class="block-note">注销申请已进入冷静期，截止 {{ formatTime(state.coolingEndsAt) }}。期间账号仍可登录并撤销。</text>
        <button class="secondary-command" :disabled="submitting" @tap="confirmRevoke">撤销注销申请</button>
        <button class="text-command" @tap="goBack">返回</button>
      </view>
      <view v-else-if="state.cancellationStatus === 'READY_FOR_FINALIZATION'" class="form-band">
        <text class="block-note">冷静期已结束，账号待后续注销处理。</text>
        <button class="text-command" @tap="goBack">返回</button>
      </view>

      <template v-else-if="canRequestCancellation">
        <!-- 注销原因四选 -->
        <view class="form-band">
          <text class="band-title">请选择注销原因（单选）</text>
          <label v-for="option in REASONS" :key="option.value" class="reason-row" @tap="reason = option.value">
            <view class="radio-dot" :class="{ active: reason === option.value }"/>
            <text class="reason-label">{{ option.label }}</text>
          </label>
        </view>

        <!-- 补充说明 0/200 -->
        <view class="form-band">
          <view class="note-head"><text class="band-title">请补充说明原因</text><text class="band-sub">（可选）</text></view>
          <textarea v-model="note" class="note-area" maxlength="200" placeholder="请补充说明原因" :disabled="submitting"/>
          <text class="note-count">{{ note.length }}/200</text>
        </view>

        <!-- 安全确认：沿用既有短信验证码链路 -->
        <view class="form-band">
          <text class="band-title">安全确认</text>
          <text class="band-sub-line">申请后进入 7 天冷静期，期间账号仍可登录并撤销。</text>
          <button class="secondary-command" :disabled="submitting" @tap="sendCode">发送注销验证码</button>
          <input v-model="code" class="field-input" maxlength="6" type="number" placeholder="注销验证码" :disabled="submitting"/>
          <input v-model="confirmation" class="field-input" maxlength="4" placeholder="请输入“确认注销”" :disabled="submitting"/>
        </view>

        <!-- 条款确认 -->
        <view class="agree-row" @tap="agreed = !agreed">
          <view class="checkbox-box" :class="{ active: agreed }"/>
          <text class="agree-text">我已阅读并同意<text class="agree-link">《账号注销须知》</text>，理解并接受注销后所有数据将被永久删除。</text>
        </view>

        <button
          class="danger-command"
          :disabled="!validForm() || submitting"
          @tap="submitCancellation"
        >提交注销申请</button>
      </template>
      <view v-else class="state-view">当前注销资格无法确认，请返回后重试。</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import PrototypePageHeader from '@/components/PrototypePageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  getParentAccountLifecycle,
  issueParentCancellationCode,
  requestParentCancellation,
  revokeParentCancellation,
  type ParentAccountLifecycleState
} from '@/api/parent-account-lifecycle';
import { clearParentSession, getParentSession, type StoredParentSession } from '@/session/parent-session';

/** 8.4 对照 07-10 注销原因四选。 */
const REASONS = [
  { value: 'UNUSED', label: '不再使用灵动伴随' },
  { value: 'RE_REGISTER', label: '更换手机号/重新注册' },
  { value: 'GRADUATED', label: '孩子已毕业' },
  { value: 'OTHER', label: '其他原因' }
] as const;

const session = ref<StoredParentSession | null>(null);
const state = ref<ParentAccountLifecycleState | null>(null);
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const reason = ref('');
const note = ref('');
const agreed = ref(false);
const code = ref('');
const confirmation = ref('');
const enabled = ref(false);
const canRequestCancellation = computed(() => enabled.value && !loading.value && !errorMessage.value
  && state.value?.cancellationStatus === 'NONE' && state.value.activeStudentRelationshipCount === 0);
function validForm(): boolean {
  return canRequestCancellation.value && agreed.value && sixDigits(code.value)
    && confirmation.value === '确认注销' && note.value.length <= 200 && REASONS.some(option => option.value === reason.value);
}

onShow(async () => {
  enabled.value = false;
  state.value = null;
  agreed.value = false;
  code.value = '';
  confirmation.value = '';
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
    enabled.value = true;
    await loadState();
  } catch (error) {
    errorMessage.value = toMessage(error);
  }
});

async function loadState(): Promise<void> {
  if (!enabled.value || !session.value) return;
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

async function sendCode(): Promise<void> {
  if (!canRequestCancellation.value) return;
  await execute(async () => {
    await issueParentCancellationCode(requireToken());
    toast('验证码已发送');
  });
}

async function submitCancellation(): Promise<void> {
  if (!validForm()) return;
  await execute(async () => {
    await requestParentCancellation(requireToken(), code.value, confirmation.value, reason.value, note.value);
    toast('注销申请已进入冷静期');
    await uni.redirectTo({ url: '/pages/parent-account-lifecycle/parent-account-lifecycle' });
  });
}

function confirmRevoke(): void {
  uni.showModal({
    title: '撤销注销申请',
    content: '账号将继续正常使用。',
    success: (result) => { if (result.confirm) void revokeRequest(); }
  });
}

async function revokeRequest(): Promise<void> {
  await execute(async () => {
    await revokeParentCancellation(requireToken());
    await loadState();
    toast('注销申请已撤销');
  });
}

function goBack(): void {
  uni.navigateBack({ delta: 1 });
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
  if (!session.value || getParentSession()?.accessToken !== session.value.accessToken) throw new Error('家长会话已失效');
  return session.value.accessToken;
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
.page-shell { min-height: calc(100vh - var(--window-top,0px)); background: #f4f7f5; color: #1c2b28; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.state-view { padding: 120rpx 40rpx; text-align: center; color: #708078; font-size: 28rpx; }
.error-state { color: #9a4b38; }
/* 8.4 对照 07-10：警示条 */
.alert-band { display: flex; gap: 20rpx; align-items: flex-start; margin-top: 24rpx; padding: 28rpx; background: #fdf3e4; border: 1rpx solid #f3ddba; border-radius: 20rpx; }
.alert-copy { flex: 1; display: flex; flex-direction: column; gap: 8rpx; }
.alert-title { color: #b26a2b; font-size: 29rpx; font-weight: 700; }
.alert-text { color: #9a6b3f; font-size: 25rpx; line-height: 1.7; }
.form-band { margin-top: 24rpx; padding: 28rpx; background: #ffffff; border: 1rpx solid #e2eee7; border-radius: 24rpx; display: flex; flex-direction: column; gap: 20rpx; }
.band-title { font-size: 29rpx; font-weight: 650; color: #162b24; }
.band-sub { font-size: 24rpx; color: #78847e; }
.band-sub-line { font-size: 24rpx; color: #78847e; line-height: 1.6; }
.block-note { color: #708078; font-size: 26rpx; line-height: 1.7; }
/* 原因单选 */
.reason-row { display: flex; align-items: center; gap: 18rpx; padding: 18rpx 0; }
.radio-dot { width: 32rpx; height: 32rpx; border-radius: 50%; border: 3rpx solid #cbd7d1; box-sizing: border-box; background: #ffffff; }
.radio-dot.active { border-width: 9rpx; border-color: #176f50; }
.reason-label { font-size: 28rpx; color: #1c2b28; }
/* 补充说明 */
.note-head { display: flex; align-items: baseline; gap: 8rpx; }
.note-area { width: 100%; min-height: 160rpx; padding: 20rpx; box-sizing: border-box; background: #fbfcfb; border: 1rpx solid #dce5df; border-radius: 14rpx; font-size: 26rpx; }
.note-count { align-self: flex-end; color: #a3ada8; font-size: 23rpx; }
.field-input { min-height: 88rpx; padding: 0 24rpx; border: 1rpx solid #dfe7e1; border-radius: 16rpx; box-sizing: border-box; background: #fafcfb; font-size: 26rpx; }
/* 条款 */
.agree-row { display: flex; align-items: flex-start; gap: 16rpx; margin-top: 28rpx; }
.checkbox-box { flex: none; width: 34rpx; height: 34rpx; margin-top: 4rpx; border-radius: 8rpx; border: 2rpx solid #cbd7d1; box-sizing: border-box; background: #ffffff; }
.checkbox-box.active { background: #176f50; border-color: #176f50; }
.agree-text { font-size: 25rpx; color: #4c5a54; line-height: 1.7; }
.agree-link { color: #176f50; }
/* 按钮 */
.primary-command, .secondary-command, .danger-command, .text-command { width: 100%; min-height: 84rpx; margin: 0; border-radius: 18rpx; font-size: 28rpx; }
.secondary-command { background: #edf5f1; color: #135f47; }
.danger-command { margin-top: 28rpx; background: #c15148; color: #ffffff; }
.text-command { background: transparent; color: #708078; }
button::after { border: 0; }
button[disabled] { opacity: 0.45; }

@import "@/styles/workspace-prototype.scss";

.prototype-workspace{background:#f5f9f5!important;padding:0 28rpx 70rpx!important;box-sizing:border-box;color:#1a382a;}
.prototype-header{margin:0 -28rpx 28rpx;padding:32rpx 32rpx 48rpx;display:flex;align-items:center;gap:18rpx;background:linear-gradient(115deg,#226d5c,#07573f);color:#fff;border-radius:0 0 30rpx 30rpx;}
.prototype-header-title{font-size:34rpx;font-weight:700;letter-spacing:1rpx;}
button{border-radius:18rpx!important;font-size:26rpx!important;}button::after{border:0;}
</style>
