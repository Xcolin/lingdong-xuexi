<template>
  <view class="prototype-workspace page-shell">
    <view class="prototype-header"><PrototypeIcon name="people" tone="white" :size="26" /><text class="prototype-header-title">亲子关系列表</text></view>
    <button v-if="enabled" @tap="scannerVisible=!scannerVisible">扫一扫或填写邀请编号</button><view v-if="enabled&&scannerVisible" class="command-band"><button :disabled="submitting" @tap="scanInvitation">扫一扫陪伴邀请</button><input v-model="manualInvitationId" class="field-input" placeholder="或填写邀请编号" maxlength="19"/><button :disabled="submitting" @tap="openManualInvitation">打开邀请</button></view><view v-if="issuedInvitation" class="dialog-surface"><text class="dialog-title">陪伴关系邀请</text><InvitationQrCode :value="encodeParentInvitation(issuedInvitation.invitationId)"/><text class="member-mobile">受邀手机号：{{issuedInvitation.maskedMobile}}</text><text class="member-mobile">有效期至 {{issuedInvitation.expiresAt.replace('T',' ')}}</text><text selectable class="member-name">{{issuedInvitation.invitationId}}</text><button @tap="copyInvitation">复制邀请编号</button><text class="member-mobile">请在灵动伴随内扫一扫，受邀人仍需验证手机号。</text></view><view v-if="loading" class="state-view">正在加载</view>
    <view v-else-if="!enabled" class="state-view disabled-state">
      <text>家长关系功能未启用</text>
      <button class="return-button" @tap="returnHome">返回家长端</button>
    </view>
    <template v-else>
      <view v-if="!invitationMode" class="heading-band">
        <text class="heading-title">家长关系</text>
        <picker v-if="students.length" :range="studentNames" :value="selectedIndex" @change="changeStudent">
          <view class="student-picker">{{ selectedStudent?.studentName || '选择学生' }}<text>⌄</text></view>
        </picker>
      </view>

      <view v-if="errorMessage" class="error-band">{{ errorMessage }}</view>
      <view v-if="selectedStudent?.relationshipRole === 'SECONDARY_GUARDIAN'" class="readonly-band">
        副家长只读
      </view>

      <view v-if="relationship && !invitationMode" class="relationship-list">
        <view v-for="member in members" :key="member.userId" class="relationship-row">
          <!-- 通用默认陪伴者插画，不代表真实头像 --><image class="member-avatar" src="/static/pixel/default-companion-avatar.png" mode="aspectFill"/><view class="member-main">
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

      <view v-if="canManage && !invitationMode" class="command-band">
        <button class="primary-command" :disabled="Boolean(relationship?.secondaryParent)"
                @tap="openInvitation('SECONDARY')">邀请副家长</button>
        <button class="plain-command" @tap="openInvitation('TRANSFER')">转移监护权</button>
        <button class="danger-command" @tap="confirmUnbindPrimary">解除我的主家长关系</button>
      </view>

      <view v-if="invitationMode" class="invitation-step">
        <view class="dialog-surface">
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
import InvitationQrCode from '@/components/InvitationQrCode.vue';
import {encodeParentInvitation,parseParentInvitation,invitationRoute} from '@/models/parent-invitation-qr';
import type {ParentRelationshipInvitation} from '@/api/parent-relationship';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
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

const scannerVisible=ref(false);
const issuedInvitation=ref<ParentRelationshipInvitation|null>(null),manualInvitationId=ref('');
function copyInvitation(){if(issuedInvitation.value)uni.setClipboardData({data:issuedInvitation.value.invitationId});}
function openManualInvitation(){try{uni.navigateTo({url:invitationRoute(manualInvitationId.value.trim())});}catch(e){errorMessage.value=toMessage(e);}}
function scanInvitation(){uni.scanCode({onlyFromCamera:false,scanType:['qrCode'],success:r=>{try{uni.navigateTo({url:invitationRoute(parseParentInvitation(r.result))});}catch(e){errorMessage.value=toMessage(e);}},fail:()=>{errorMessage.value='扫描未完成，可填写邀请编号';}});}
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

onHide(()=>{issuedInvitation.value=null;manualInvitationId.value='';});
onShow(() => {issuedInvitation.value=null;void initialize();});

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
    if(getParentSession()?.accessToken!==session.accessToken)return;
    closeInvitationAfterSubmit();
    issuedInvitation.value=result;
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

<style scoped>
.page-shell{padding:24rpx 28rpx 60rpx;background:#f6f9f5;min-height:100vh;box-sizing:border-box;color:#18372a}.prototype-header{display:flex;gap:14rpx;align-items:center;justify-content:center;background:#175e47;color:#fff;padding:28rpx;margin:-24rpx -28rpx 26rpx}.prototype-header-title{font-size:32rpx;font-weight:650}.heading-band,.relationship-list,.command-band,.dialog-surface{padding:26rpx;background:#fff;border-radius:24rpx;margin-bottom:22rpx}.heading-title,.dialog-title{display:block;font-size:32rpx;font-weight:650;margin-bottom:22rpx}.student-picker{display:flex;justify-content:space-between;background:#f6f9f3;padding:20rpx;border-radius:16rpx}.relationship-row{display:flex;align-items:center;gap:18rpx;padding:24rpx 0;border-bottom:1rpx solid #e6eee3}.member-avatar{width:80rpx;height:80rpx;border-radius:50%;flex-shrink:0}.member-main{flex:1}.member-name{display:block;font-size:30rpx;font-weight:650}.member-mobile{display:block;font-size:24rpx;color:#7c847e;margin-top:8rpx}.role-label{font-size:23rpx;background:#e6f4e4;color:#147c51;border-radius:14rpx;padding:8rpx}.field-label{display:block;font-size:26rpx;margin:24rpx 0 12rpx}.field-input{background:#f7faf5;border:1rpx solid #dfe8dc;border-radius:16rpx;padding:20rpx;font-size:28rpx}.dialog-actions{display:flex;gap:16rpx;margin-top:30rpx}.dialog-actions button{flex:1}button{font-size:26rpx;padding:18rpx;line-height:1.5;background:#e9f2e6;color:#225a40;border-radius:18rpx;margin-top:16rpx}button::after{border:0}.submit-command,.primary-command{background:#176f50;color:white}.danger-command,.danger,.dialog-error,.error-band{color:#a44d40}.state-view,.readonly-band{padding:30rpx;color:#7b847d;font-size:26rpx}
</style>
