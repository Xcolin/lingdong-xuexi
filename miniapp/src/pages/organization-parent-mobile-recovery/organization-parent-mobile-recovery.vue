<template>
  <view class="page-shell">
    <view class="warning-band">
      <text class="warning-title">家长换号核验</text>
      <text class="warning-text">仅用于原手机号不可用且已完成线下身份核验的家长。提交后将撤销该家长全部活动会话。</text>
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <view v-else class="form-band">
      <text class="field-label">学生与家长</text>
      <picker :range="candidateLabels" :value="candidateIndex" @change="selectCandidate">
        <view class="picker-value">{{ selectedCandidateLabel || '请选择活动关系' }}</view>
      </picker>

      <text class="field-label">新手机号</text>
      <view class="mobile-row">
        <input
          v-model="newMobile"
          class="text-input mobile-input"
          type="number"
          maxlength="11"
          placeholder="请输入新手机号"
        />
        <button class="code-button" :disabled="sending" @tap="sendCode">
          {{ sending ? '发送中' : '发送验证码' }}
        </button>
      </view>

      <text class="field-label">验证码</text>
      <input
        v-model="smsCode"
        class="text-input"
        type="number"
        maxlength="6"
        placeholder="请输入 6 位验证码"
      />

      <text class="field-label">核验原因</text>
      <textarea
        v-model="reason"
        class="reason-input"
        maxlength="200"
        placeholder="请输入线下核验原因"
      />

      <text class="field-label">确认语句</text>
      <input
        v-model="confirmation"
        class="text-input"
        maxlength="20"
        placeholder="请输入：已完成线下身份核验"
      />

      <button class="submit-button" :disabled="submitting" @tap="submit">
        {{ submitting ? '正在提交' : '确认换绑' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  issueParentMobileManualRecoveryCode,
  listParentMobileManualRecoveryCandidates,
  recoverParentMobile,
  type ParentMobileManualRecoveryCandidate
} from '@/api/parent-mobile-manual-recovery';
import { getOrganizationSession } from '@/session/organization-session';

const CONFIRMATION = '已完成线下身份核验';
const MOBILE_PATTERN = /^1[3-9]\d{9}$/;
const SMS_CODE_PATTERN = /^\d{6}$/;

const candidates = ref<ParentMobileManualRecoveryCandidate[]>([]);
const candidateIndex = ref(-1);
const newMobile = ref('');
const smsCode = ref('');
const reason = ref('');
const confirmation = ref('');
const loading = ref(true);
const sending = ref(false);
const submitting = ref(false);

const candidateLabels = computed(() => candidates.value.map((candidate) => (
  `${candidate.studentName} · ${candidate.parentDisplayName} · ${roleLabel(candidate.relationshipRole)} · ${candidate.maskedMobile}`
)));
const selectedCandidate = computed(() => candidates.value[candidateIndex.value] || null);
const selectedCandidateLabel = computed(() => candidateLabels.value[candidateIndex.value] || '');

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    if (capabilities.parentMobileManualRecoveryEnabled !== true) return leavePage();
    candidates.value = await listParentMobileManualRecoveryCandidates(session.accessToken);
  } catch (error) {
    showError(error instanceof Error ? error.message : '换号核验加载失败');
  } finally {
    loading.value = false;
  }
});

function selectCandidate(event: { detail: { value: string } }): void {
  candidateIndex.value = Number(event.detail.value);
}

async function sendCode(): Promise<void> {
  const session = getOrganizationSession();
  const candidate = selectedCandidate.value;
  if (!session || !candidate) return showError('请选择学生与家长');
  if (!MOBILE_PATTERN.test(newMobile.value)) return showError('手机号格式不正确');

  sending.value = true;
  try {
    await issueParentMobileManualRecoveryCode(
      session.accessToken,
      candidate.studentId,
      candidate.parentUserId,
      newMobile.value
    );
    uni.showToast({ title: '验证码已发送', icon: 'success' });
  } catch (error) {
    showError(error instanceof Error ? error.message : '验证码发送失败');
  } finally {
    sending.value = false;
  }
}

async function submit(): Promise<void> {
  const session = getOrganizationSession();
  const candidate = selectedCandidate.value;
  const normalizedReason = reason.value.trim();
  if (!session || !candidate) return showError('请选择学生与家长');
  if (!MOBILE_PATTERN.test(newMobile.value)) return showError('手机号格式不正确');
  if (!SMS_CODE_PATTERN.test(smsCode.value)) return showError('验证码必须为 6 位数字');
  if (!normalizedReason) return showError('请输入核验原因');
  if (confirmation.value !== CONFIRMATION) return showError('确认语句不正确');

  const confirmed = await confirmRecovery();
  if (!confirmed) return;
  submitting.value = true;
  try {
    await recoverParentMobile(session.accessToken, {
      studentId: candidate.studentId,
      parentUserId: candidate.parentUserId,
      newMobile: newMobile.value,
      smsCode: smsCode.value,
      reason: normalizedReason,
      confirmation: confirmation.value
    });
    uni.showToast({ title: '手机号已换绑', icon: 'success' });
    await uni.navigateBack();
  } catch (error) {
    showError(error instanceof Error ? error.message : '换绑提交失败');
  } finally {
    submitting.value = false;
  }
}

function confirmRecovery(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '确认完成手机号换绑？',
      content: '操作后家长原有活动会话将全部撤销，且操作事实会被审计记录。',
      confirmText: '确认换绑',
      confirmColor: '#b54732',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
}

function roleLabel(role: ParentMobileManualRecoveryCandidate['relationshipRole']): string {
  return role === 'PRIMARY_GUARDIAN' ? '主家长' : '副家长';
}

function showError(title: string): void {
  uni.showToast({ title, icon: 'none' });
}

function leavePage(): Promise<unknown> {
  return uni.reLaunch({ url: '/pages/index/index' });
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 28rpx 0 48rpx; box-sizing: border-box; background: #f4f7f5; }
.warning-band { padding: 28rpx 40rpx; border-top: 2rpx solid #edcf9c; border-bottom: 2rpx solid #edcf9c; background: #fff8e8; }
.warning-title, .warning-text, .field-label { display: block; }
.warning-title { color: #6f4b16; font-size: 30rpx; font-weight: 700; }
.warning-text { margin-top: 12rpx; color: #7b6036; font-size: 24rpx; line-height: 1.6; }
.state-text { min-height: 360rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 28rpx; }
.form-band { margin-top: 28rpx; padding: 32rpx 40rpx 40rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.field-label { margin: 28rpx 0 12rpx; color: #31413b; font-size: 26rpx; font-weight: 600; }
.field-label:first-child { margin-top: 0; }
.picker-value, .text-input { min-height: 84rpx; display: flex; align-items: center; padding: 0 24rpx; box-sizing: border-box; border: 2rpx solid #cfd9d4; border-radius: 8rpx; color: #1c2b28; font-size: 27rpx; }
.mobile-row { display: flex; align-items: stretch; gap: 16rpx; }
.mobile-input { min-width: 0; flex: 1; }
.code-button { width: 210rpx; height: 84rpx; margin: 0; padding: 0 12rpx; border-radius: 8rpx; background: #ffffff; color: #167c5a; font-size: 25rpx; line-height: 84rpx; }
.code-button::after { border-color: #167c5a; }
.reason-input { width: 100%; min-height: 180rpx; padding: 20rpx 24rpx; box-sizing: border-box; border: 2rpx solid #cfd9d4; border-radius: 8rpx; color: #1c2b28; font-size: 27rpx; }
.submit-button { height: 88rpx; margin-top: 36rpx; border-radius: 10rpx; background: #b54732; color: #ffffff; font-size: 29rpx; }
.submit-button::after { border: 0; }
</style>
