<template>
  <view class="page-shell">
    <view class="warning-band">
      <text class="warning-title">学生账号注销</text>
      <text class="warning-text">仅可注销已退学且已解除全部家长关系的学生。账号身份将永久匿名化，所有登录凭据立即失效。</text>
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <view v-else-if="candidates.length === 0" class="state-text">暂无可注销学生</view>
    <view v-else class="form-band">
      <text class="field-label">学生账号</text>
      <picker :range="candidateLabels" :value="candidateIndex" @change="selectCandidate">
        <view class="picker-value">{{ selectedCandidateLabel || '请选择待注销学生' }}</view>
      </picker>

      <text class="field-label">注销原因</text>
      <textarea
        v-model="reason"
        class="reason-input"
        maxlength="200"
        placeholder="请输入退学及关系解除情况"
      />

      <text class="field-label">确认语句</text>
      <input
        v-model="confirmation"
        class="text-input"
        maxlength="20"
        placeholder="请输入：确认注销学生账号"
      />

      <button class="submit-button" :disabled="submitting" @tap="submit">
        {{ submitting ? '正在注销' : '注销学生账号' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  cancelStudentAccount,
  listStudentAccountCancellationCandidates,
  type StudentAccountCancellationCandidate
} from '@/api/student-account-cancellation';
import { getOrganizationSession } from '@/session/organization-session';

const CONFIRMATION = '确认注销学生账号';

const candidates = ref<StudentAccountCancellationCandidate[]>([]);
const candidateIndex = ref(-1);
const reason = ref('');
const confirmation = ref('');
const loading = ref(true);
const submitting = ref(false);

const candidateLabels = computed(() => candidates.value.map((candidate) => (
  `${candidate.studentName} · ${candidate.studentAccount} · ${candidate.organizationName}`
)));
const selectedCandidate = computed(() => candidates.value[candidateIndex.value] || null);
const selectedCandidateLabel = computed(() => candidateLabels.value[candidateIndex.value] || '');

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) return leavePage(true);
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    if (capabilities.studentAccountCancellationEnabled !== true) return leavePage(false);
    candidates.value = await listStudentAccountCancellationCandidates(session.accessToken);
    if (candidateIndex.value >= candidates.value.length) candidateIndex.value = -1;
  } catch (error) {
    showError(error instanceof Error ? error.message : '学生账号注销页面加载失败');
  } finally {
    loading.value = false;
  }
});

function selectCandidate(event: { detail: { value: string } }): void {
  candidateIndex.value = Number(event.detail.value);
}

async function submit(): Promise<void> {
  const session = getOrganizationSession();
  const candidate = selectedCandidate.value;
  const normalizedReason = reason.value.trim();
  if (!session || !candidate) return showError('请选择学生账号');
  if (!normalizedReason) return showError('请输入注销原因');
  if (normalizedReason.length > 200) return showError('注销原因不能超过 200 个字符');
  if (confirmation.value !== CONFIRMATION) return showError('确认语句不正确');
  if (!await confirmCancellation(candidate)) return;

  submitting.value = true;
  try {
    await cancelStudentAccount(session.accessToken, candidate.studentId, {
      reason: normalizedReason,
      confirmation: confirmation.value
    });
    uni.showToast({ title: '学生账号已注销', icon: 'success' });
    await uni.navigateBack();
  } catch (error) {
    showError(error instanceof Error ? error.message : '学生账号注销失败');
  } finally {
    submitting.value = false;
  }
}

function confirmCancellation(candidate: StudentAccountCancellationCandidate): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: `确认注销 ${candidate.studentName}？`,
      content: '账号、登录码、二维码和全部会话将立即失效，此操作不可恢复。',
      confirmText: '确认注销',
      confirmColor: '#b42318',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
}

function showError(title: string): void {
  uni.showToast({ title, icon: 'none' });
}

function leavePage(missingSession: boolean): Promise<unknown> {
  return uni.reLaunch({
    url: missingSession ? '/pages/index/index' : '/pages/organization-home/organization-home'
  });
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 28rpx 0 48rpx; box-sizing: border-box; background: #f4f7f5; }
.warning-band { padding: 28rpx 40rpx; border-top: 2rpx solid #e9b4ad; border-bottom: 2rpx solid #e9b4ad; background: #fff1f0; }
.warning-title, .warning-text, .field-label { display: block; }
.warning-title { color: #8f2119; font-size: 30rpx; font-weight: 700; }
.warning-text { margin-top: 12rpx; color: #7e3a35; font-size: 24rpx; line-height: 1.6; }
.state-text { min-height: 360rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 28rpx; }
.form-band { margin-top: 28rpx; padding: 32rpx 40rpx 40rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.field-label { margin: 28rpx 0 12rpx; color: #31413b; font-size: 26rpx; font-weight: 600; }
.field-label:first-child { margin-top: 0; }
.picker-value, .text-input { min-height: 84rpx; display: flex; align-items: center; padding: 0 24rpx; box-sizing: border-box; border: 2rpx solid #cfd9d4; border-radius: 8rpx; color: #1c2b28; font-size: 27rpx; }
.reason-input { width: 100%; min-height: 180rpx; padding: 20rpx 24rpx; box-sizing: border-box; border: 2rpx solid #cfd9d4; border-radius: 8rpx; color: #1c2b28; font-size: 27rpx; }
.submit-button { height: 88rpx; margin-top: 36rpx; border-radius: 10rpx; background: #b42318; color: #ffffff; font-size: 29rpx; }
.submit-button::after { border: 0; }
</style>
