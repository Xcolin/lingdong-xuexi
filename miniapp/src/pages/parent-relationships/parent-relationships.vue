<template>
  <view class="page-shell">
    <view v-if="loading" class="state-view">正在加载</view>
    <view v-else-if="!enabled" class="state-view disabled-state">
      <text>家长关系功能未启用</text>
      <button class="return-button" @tap="returnHome">返回家长端</button>
    </view>
    <template v-else>
      <view class="heading-band">
        <text class="heading-title">家长关系</text>
        <picker v-if="students.length" :range="studentNames" :value="selectedIndex" @change="changeStudent">
          <view class="student-picker">{{ selectedStudent?.studentName || '选择学生' }}<text>⌄</text></view>
        </picker>
      </view>

      <view v-if="errorMessage" class="error-band">{{ errorMessage }}</view>
      <view v-if="selectedStudent?.relationshipRole === 'SECONDARY_GUARDIAN'" class="readonly-band">
        副家长只读
      </view>

      <view v-if="relationship" class="relationship-list">
        <view v-for="member in members" :key="member.userId" class="relationship-row">
          <view class="member-main">
            <text class="member-name">{{ member.displayName || '家长' }}</text>
            <text class="member-mobile">{{ member.mobileMasked || '-' }}</text>
          </view>
          <text :class="['role-label', member.relationshipRole === 'PRIMARY_GUARDIAN' ? 'primary' : 'secondary']">
            {{ member.relationshipRole === 'PRIMARY_GUARDIAN' ? '主家长' : '副家长' }}
          </text>
          <button v-if="canManage && member.relationshipRole === 'SECONDARY_GUARDIAN'"
                  class="row-command danger" @tap="confirmUnbindSecondary(member)">解除</button>
        </view>
      </view>
      <view v-else-if="!errorMessage" class="state-view">暂无家长关系</view>

      <view v-if="canManage" class="command-band">
        <button class="primary-command" :disabled="Boolean(relationship?.secondaryParent)"
                @tap="openInvitation('SECONDARY')">邀请副家长</button>
        <button class="plain-command" @tap="openInvitation('TRANSFER')">转移监护权</button>
        <button class="danger-command" @tap="confirmUnbindPrimary">解除我的主家长关系</button>
      </view>

      <view v-if="invitationMode" class="dialog-mask" @tap="closeInvitation">
        <view class="dialog-surface" @tap.stop>
          <text class="dialog-title">{{ invitationMode === 'SECONDARY' ? '邀请副家长' : '转移监护权' }}</text>
          <text class="field-label">目标家长手机号</text>
          <input v-model="targetMobile" class="field-input" type="number" maxlength="11"
                 placeholder="请输入手机号" :disabled="submitting" />
          <text v-if="dialogError" class="dialog-error">{{ dialogError }}</text>
          <view class="dialog-actions">
            <button class="cancel-command" :disabled="submitting" @tap="closeInvitation">取消</button>
            <button class="submit-command" :loading="submitting" :disabled="submitting" @tap="submitInvitation">
              发送邀请
            </button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  createPrimaryParentTransferInvitation,
  createSecondaryParentInvitation,
  getParentRelationships,
  listParentRelationshipStudents,
  unbindPrimaryParent,
  unbindSecondaryParent,
  type ParentRelationship,
  type ParentRelationshipMember,
  type ParentRelationshipStudent
} from '@/api/parent-relationship';
import { getParentSession } from '@/session/parent-session';

const enabled = ref(false);
const loading = ref(true);
const errorMessage = ref('');
const students = ref<ParentRelationshipStudent[]>([]);
const selectedIndex = ref(0);
const relationship = ref<ParentRelationship | null>(null);
const invitationMode = ref<'SECONDARY' | 'TRANSFER' | null>(null);
const targetMobile = ref('');
const dialogError = ref('');
const submitting = ref(false);

const studentNames = computed(() => students.value.map((student) => student.studentName));
const selectedStudent = computed(() => students.value[selectedIndex.value] || null);
const members = computed(() => relationship.value
  ? [relationship.value.primaryParent, relationship.value.secondaryParent]
    .filter((member): member is ParentRelationshipMember => Boolean(member))
  : []);
const canManage = computed(() => selectedStudent.value?.relationshipRole === 'PRIMARY_GUARDIAN');

onShow(() => void initialize());

async function initialize(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  const session = getParentSession();
  if (!session) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    enabled.value = capabilities.parentRelationshipManagementEnabled;
    if (!enabled.value) return;
    students.value = await listParentRelationshipStudents(session.accessToken);
    selectedIndex.value = Math.min(selectedIndex.value, Math.max(0, students.value.length - 1));
    await loadSelectedRelationship();
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    loading.value = false;
  }
}

async function loadSelectedRelationship(): Promise<void> {
  const session = getParentSession();
  if (!session || !selectedStudent.value) {
    relationship.value = null;
    return;
  }
  relationship.value = await getParentRelationships(
    selectedStudent.value.studentId, session.accessToken
  );
}

async function changeStudent(event: { detail: { value: string | number } }): Promise<void> {
  selectedIndex.value = Number(event.detail.value);
  errorMessage.value = '';
  try {
    await loadSelectedRelationship();
  } catch (error) {
    errorMessage.value = toMessage(error);
  }
}

function openInvitation(mode: 'SECONDARY' | 'TRANSFER'): void {
  invitationMode.value = mode;
  targetMobile.value = '';
  dialogError.value = '';
}

function closeInvitation(): void {
  if (submitting.value) return;
  invitationMode.value = null;
  targetMobile.value = '';
  dialogError.value = '';
}

async function submitInvitation(): Promise<void> {
  const session = getParentSession();
  if (!session || !selectedStudent.value || !invitationMode.value) return;
  if (!/^1[3-9]\d{9}$/.test(targetMobile.value)) {
    dialogError.value = '手机号格式不正确';
    return;
  }
  submitting.value = true;
  dialogError.value = '';
  try {
    const result = invitationMode.value === 'SECONDARY'
      ? await createSecondaryParentInvitation(
        selectedStudent.value.studentId, targetMobile.value, session.accessToken)
      : await createPrimaryParentTransferInvitation(
        selectedStudent.value.studentId, targetMobile.value, session.accessToken);
    closeInvitationAfterSubmit();
    await showMessage('邀请已发送', `目标手机号 ${result.maskedMobile}，请在5分钟内完成确认。`);
  } catch (error) {
    dialogError.value = toMessage(error);
  } finally {
    submitting.value = false;
  }
}

function confirmUnbindSecondary(member: ParentRelationshipMember): void {
  uni.showModal({
    title: '确认解除副家长',
    content: `解除后，${member.displayName || '该副家长'}将立即失去该学生的数据访问权限。`,
    confirmText: '确认解除',
    confirmColor: '#b54734',
    success: (result) => {
      if (result.confirm) void executeUnbindSecondary(member.userId);
    }
  });
}

async function executeUnbindSecondary(parentUserId: string): Promise<void> {
  const session = getParentSession();
  if (!session || !selectedStudent.value) return;
  try {
    await unbindSecondaryParent(
      selectedStudent.value.studentId, parentUserId, session.accessToken
    );
    await loadSelectedRelationship();
    uni.showToast({ title: '关系已解除', icon: 'success' });
  } catch (error) {
    errorMessage.value = toMessage(error);
  }
}

function confirmUnbindPrimary(): void {
  uni.showModal({
    title: '确认解除主家长关系',
    content: relationship.value?.secondaryParent
      ? '解除后，当前副家长将自动晋升为主家长。'
      : '解除后，该学生将暂时没有主家长。',
    confirmText: '确认解除',
    confirmColor: '#b54734',
    success: (result) => {
      if (result.confirm) void executeUnbindPrimary();
    }
  });
}

async function executeUnbindPrimary(): Promise<void> {
  const session = getParentSession();
  if (!session || !selectedStudent.value) return;
  try {
    await unbindPrimaryParent(selectedStudent.value.studentId, session.accessToken);
    await initialize();
    uni.showToast({ title: '关系已解除', icon: 'success' });
  } catch (error) {
    errorMessage.value = toMessage(error);
  }
}

function closeInvitationAfterSubmit(): void {
  invitationMode.value = null;
  targetMobile.value = '';
  dialogError.value = '';
}

function showMessage(title: string, content: string): Promise<UniApp.ShowModalRes> {
  return new Promise((resolve) => uni.showModal({ title, content, showCancel: false, success: resolve }));
}

function returnHome(): Promise<unknown> {
  return uni.reLaunch({ url: '/pages/parent-home/parent-home' });
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 36rpx 32rpx 64rpx; box-sizing: border-box; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.heading-band { display: flex; align-items: center; justify-content: space-between; gap: 24rpx; margin-bottom: 28rpx; }
.heading-title { color: #1c2b28; font-size: 40rpx; font-weight: 700; }
.student-picker { min-width: 240rpx; height: 72rpx; display: flex; align-items: center; justify-content: space-between; gap: 18rpx; padding: 0 22rpx; border: 2rpx solid #cdd8d3; border-radius: 10rpx; box-sizing: border-box; background: #ffffff; color: #253730; font-size: 27rpx; }
.state-view { min-height: 360rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 28rpx; color: #71817a; font-size: 28rpx; }
.disabled-state { color: #9a4b38; }
.return-button { height: 72rpx; margin: 0; padding: 0 28rpx; background: #ffffff; color: #1d6f55; font-size: 27rpx; }
.error-band, .readonly-band { margin-bottom: 20rpx; padding: 20rpx 24rpx; border-left: 6rpx solid #b54734; background: #fff3ef; color: #8d3c2d; font-size: 26rpx; }
.readonly-band { border-left-color: #367ca5; background: #eef7fb; color: #2d688a; }
.relationship-list { border-top: 2rpx solid #d9e2de; background: #ffffff; }
.relationship-row { min-height: 128rpx; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 18rpx; padding: 20rpx 24rpx; border-bottom: 2rpx solid #e2e8e5; box-sizing: border-box; }
.member-main { min-width: 0; }
.member-name, .member-mobile { display: block; }
.member-name { color: #1c2b28; font-size: 29rpx; font-weight: 600; }
.member-mobile { margin-top: 8rpx; color: #71817a; font-size: 24rpx; }
.role-label { padding: 8rpx 14rpx; border-radius: 8rpx; font-size: 23rpx; }
.role-label.primary { background: #e6f4ee; color: #146c4e; }
.role-label.secondary { background: #eaf2f7; color: #326d90; }
.row-command { min-width: 92rpx; height: 60rpx; margin: 0; padding: 0 16rpx; background: transparent; font-size: 24rpx; }
.row-command::after { border: 0; }
.row-command.danger { color: #a34332; }
.command-band { display: grid; gap: 18rpx; margin-top: 32rpx; }
.primary-command, .plain-command, .danger-command { width: 100%; height: 84rpx; margin: 0; border-radius: 10rpx; font-size: 28rpx; }
.primary-command { background: #167c5a; color: #ffffff; }
.plain-command { background: #ffffff; color: #1d6f55; }
.danger-command { background: #ffffff; color: #a34332; }
.primary-command::after, .plain-command::after, .danger-command::after { border: 0; }
.primary-command[disabled] { background: #aebdb7; color: #ffffff; }
.dialog-mask { position: fixed; inset: 0; z-index: 20; display: flex; align-items: flex-end; background: rgba(21, 34, 29, 0.48); }
.dialog-surface { width: 100%; padding: 36rpx 32rpx calc(36rpx + env(safe-area-inset-bottom)); border-radius: 16rpx 16rpx 0 0; box-sizing: border-box; background: #ffffff; }
.dialog-title { display: block; margin-bottom: 32rpx; color: #1c2b28; font-size: 34rpx; font-weight: 700; }
.field-label { display: block; margin-bottom: 12rpx; color: #455a52; font-size: 25rpx; }
.field-input { width: 100%; height: 84rpx; padding: 0 22rpx; border: 2rpx solid #cdd8d3; border-radius: 10rpx; box-sizing: border-box; font-size: 29rpx; }
.dialog-error { display: block; margin-top: 16rpx; color: #a34332; font-size: 24rpx; }
.dialog-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; margin-top: 32rpx; }
.cancel-command, .submit-command { height: 80rpx; margin: 0; font-size: 27rpx; }
.cancel-command { background: #eef2f0; color: #52675f; }
.submit-command { background: #167c5a; color: #ffffff; }
.cancel-command::after, .submit-command::after { border: 0; }
</style>
