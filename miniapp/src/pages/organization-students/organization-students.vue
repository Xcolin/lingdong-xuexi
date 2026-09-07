<template>
  <view class="page-shell">
    <view class="mode-control">
      <button :class="{ active: mode === 'TRANSFER' }" @tap="setMode('TRANSFER')">校内转班</button>
      <button :class="{ active: mode === 'DEACTIVATE' }" @tap="setMode('DEACTIVATE')">离校或转学转出</button>
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <view v-else class="form-band">
      <text class="field-label">学生</text>
      <picker :range="studentLabels" :value="studentIndex" @change="selectStudent">
        <view class="picker-value">{{ selectedStudentLabel || '请选择学生' }}</view>
      </picker>

      <template v-if="mode === 'TRANSFER'">
        <text class="field-label">目标班级</text>
        <picker :range="classLabels" :value="classIndex" @change="selectClass">
          <view class="picker-value">{{ selectedClassLabel || '请选择目标班级' }}</view>
        </picker>
      </template>

      <text class="field-label">变更原因</text>
      <textarea
        v-model="reason"
        class="reason-input"
        maxlength="200"
        placeholder="请输入变更原因"
      />

      <button
        class="submit-button"
        :class="{ danger: mode === 'DEACTIVATE' }"
        :disabled="submitting"
        @tap="submit"
      >
        {{ submitting ? '正在提交' : mode === 'TRANSFER' ? '确认转班' : '确认转出' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  deactivateOrganizationStudent,
  listOrganizationClasses,
  listOrganizationStudents,
  transferOrganizationStudent,
  type OrganizationClassOption,
  type OrganizationStudentRelationship
} from '@/api/organization-students';
import { getOrganizationSession } from '@/session/organization-session';

type OperationMode = 'TRANSFER' | 'DEACTIVATE';

const mode = ref<OperationMode>('TRANSFER');
const students = ref<OrganizationStudentRelationship[]>([]);
const classes = ref<OrganizationClassOption[]>([]);
const studentIndex = ref(-1);
const classIndex = ref(-1);
const reason = ref('');
const loading = ref(true);
const submitting = ref(false);

const studentLabels = computed(() => students.value.map((student) => (
  `${student.studentName}${student.currentClassOrganizationName
    ? ` · 当前 ${student.currentClassOrganizationName}` : ' · 未分班'}`
)));
const classLabels = computed(() => classes.value.map((item) => item.name));
const selectedStudent = computed(() => students.value[studentIndex.value] || null);
const selectedStudentLabel = computed(() => studentLabels.value[studentIndex.value] || '');
const selectedClass = computed(() => classes.value[classIndex.value] || null);
const selectedClassLabel = computed(() => classLabels.value[classIndex.value] || '');

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    if (!capabilities.studentOrganizationRelationshipEnabled) return leavePage();
    await loadData(session.accessToken);
  } catch {
    uni.showToast({ title: '学员关系加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
});

function setMode(nextMode: OperationMode): void {
  mode.value = nextMode;
  classIndex.value = -1;
}

function selectStudent(event: { detail: { value: string } }): void {
  studentIndex.value = Number(event.detail.value);
}

function selectClass(event: { detail: { value: string } }): void {
  classIndex.value = Number(event.detail.value);
}

async function submit(): Promise<void> {
  const session = getOrganizationSession();
  const student = selectedStudent.value;
  const normalizedReason = reason.value.trim();
  if (!session || !student) return showError('请选择学生');
  if (!normalizedReason) return showError('请输入变更原因');
  if (mode.value === 'TRANSFER' && !selectedClass.value) return showError('请选择目标班级');

  if (mode.value === 'DEACTIVATE') {
    const confirmed = await confirmDeactivation();
    if (!confirmed) return;
  }

  submitting.value = true;
  try {
    if (mode.value === 'TRANSFER') {
      await transferOrganizationStudent(
        session.accessToken,
        student.studentId,
        selectedClass.value!.id,
        normalizedReason
      );
      uni.showToast({ title: '班级关系已更新', icon: 'success' });
    } else {
      await deactivateOrganizationStudent(
        session.accessToken,
        student.studentId,
        student.enrollmentOrganizationId,
        normalizedReason
      );
      uni.showToast({ title: '机构关系已停用', icon: 'success' });
    }
    studentIndex.value = -1;
    classIndex.value = -1;
    reason.value = '';
    await loadData(session.accessToken);
  } catch (error) {
    showError(error instanceof Error ? error.message : '提交失败');
  } finally {
    submitting.value = false;
  }
}

async function loadData(accessToken: string): Promise<void> {
  [students.value, classes.value] = await Promise.all([
    listOrganizationStudents(accessToken),
    listOrganizationClasses(accessToken)
  ]);
}

function confirmDeactivation(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '确认转出该学生？',
      content: '转出后机构和班级活动关系立即停用，历史数据继续保留。',
      confirmText: '确认转出',
      confirmColor: '#b54732',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
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
.mode-control { display: grid; grid-template-columns: 1fr 1fr; gap: 0; margin: 0 40rpx 28rpx; border: 2rpx solid #b8c7c0; border-radius: 10rpx; overflow: hidden; background: #ffffff; }
.mode-control button { height: 72rpx; margin: 0; border-radius: 0; background: #ffffff; color: #52625b; font-size: 26rpx; line-height: 72rpx; }
.mode-control button::after { border: 0; }
.mode-control button.active { background: #167c5a; color: #ffffff; }
.state-text { min-height: 360rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 28rpx; }
.form-band { padding: 32rpx 40rpx 40rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.field-label { display: block; margin: 28rpx 0 12rpx; color: #31413b; font-size: 26rpx; font-weight: 600; }
.field-label:first-child { margin-top: 0; }
.picker-value { min-height: 84rpx; display: flex; align-items: center; padding: 0 24rpx; box-sizing: border-box; border: 2rpx solid #cfd9d4; border-radius: 8rpx; color: #1c2b28; font-size: 27rpx; }
.reason-input { width: 100%; min-height: 180rpx; padding: 20rpx 24rpx; box-sizing: border-box; border: 2rpx solid #cfd9d4; border-radius: 8rpx; color: #1c2b28; font-size: 27rpx; }
.submit-button { height: 88rpx; margin-top: 36rpx; border-radius: 10rpx; background: #167c5a; color: #ffffff; font-size: 29rpx; }
.submit-button.danger { background: #b54732; }
.submit-button::after { border: 0; }
</style>
