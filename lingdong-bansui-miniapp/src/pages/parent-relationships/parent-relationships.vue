<template>
  <view class="prototype-workspace page-shell"><PixelPageHeader title="亲子关系" back fallback="/pages/parent-me/parent-me"/>
    <button v-if="canApplyTraining" class="primary-command" @tap="openTrainingApplication">课程与活动申请</button>
    <view class="relationship-toolbar"><button v-if="enabled" class="manage-link" :disabled="loading" @tap="toggleManage">{{ manageMode ? '完成' : '管理' }}</button></view>
    <button v-if="enabled" @tap="scannerVisible=!scannerVisible">扫一扫或填写邀请编号</button><view v-if="enabled&&scannerVisible" class="command-band"><button :disabled="submitting" @tap="scanInvitation">扫一扫陪伴邀请</button><input v-model="manualInvitationId" class="field-input" placeholder="或填写邀请编号" maxlength="19"/><button :disabled="submitting" @tap="openManualInvitation">打开邀请</button></view><view v-if="issuedInvitation" class="dialog-surface invite-dialog"><view class="invite-tabs"><button :class="['invite-tab',inviteTab==='COMPANION'?'active':'']" @tap="inviteTab='COMPANION'">邀请成长伙伴</button><button :class="['invite-tab',inviteTab==='GUARDIAN'?'active':'']" @tap="inviteTab='GUARDIAN'">邀请其他监护人</button></view><template v-if="inviteTab==='GUARDIAN'"><InvitationQrCode ref="qrRef" :value="encodeParentInvitation(issuedInvitation.invitationId)"/><text class="invite-title">请使用「灵动伴随」小程序扫码加入</text><text class="invite-sub">有效期至 {{issuedInvitation.expiresAt.replace('T',' ').slice(0,16)}}</text><text class="invite-sub">仅限加入 {{selectedStudent?.studentName || '该学生'}} 的其他监护人</text><text class="invite-sub">受邀手机号：{{issuedInvitation.maskedMobile}}</text><text selectable class="member-name">{{issuedInvitation.invitationId}}</text><view class="invite-actions"><button class="invite-save" @tap="saveQr">保存图片</button><button class="invite-share" open-type="share">分享邀请</button></view><button class="plain-command" @tap="copyInvitation">复制邀请编号</button><text class="member-mobile">请在灵动伴随内扫一扫，受邀人仍需验证手机号。</text></template><template v-else><text class="invite-title">邀请成长伙伴</text><text class="invite-sub">成长伙伴邀请即将开放，当前可先邀请其他监护人共同陪伴孩子成长。</text><button class="invite-share" @tap="inviteTab='GUARDIAN'">去邀请其他监护人</button></template></view><view v-if="loading" class="state-view">正在加载</view>
    <view v-else-if="!enabled" class="state-view disabled-state">
      <text>家长关系功能未启用</text>
      <button class="return-button" @tap="returnHome">返回家长端</button>
    </view>
    <template v-else>
      <!-- 主视图：我发起的关系 / 我收到的邀请 双区（对照 07-03） -->
      <template v-if="!manageMode">
        <view class="section-band">
          <text class="section-title">我发起的关系 ({{ activeList.total.value }})</text>
          <scroll-view scroll-y class="relationship-scroll" :style="{height:sectionHeight(activeList.rows.value.length)}" @scrolltolower="activeList.more()">
          <view v-for="s in activeList.rows.value" :key="s.studentId" class="child-card">
            <view class="child-avatar"><PrototypeIcon name="user" :size="44"/></view>
            <view class="child-main">
              <text class="child-name">{{ s.studentName }}<text class="role-chip">{{ roleLabel(s.relationshipRole) }}</text></text>
              <text class="child-sub">关系：{{ roleLabel(s.relationshipRole) }}</text>
              <text v-if="s.boundAt" class="child-date">{{ dotDate(s.boundAt) }} 建立</text>
            </view>
            <text class="status-chip pass">已通过</text>
          </view>
          <view v-if="activeList.loaded.value&&!activeList.rows.value.length" class="empty-line">暂无已建立的孩子关系</view>
          </scroll-view>
          <view class="relationship-paging"><text v-if="activeList.busy.value">正在加载</text><button v-else-if="activeList.error.value" @tap="activeList.loaded.value?activeList.more(true):activeList.first()">加载失败，点击重试</button><text v-else-if="activeList.hasMore.value">上拉加载更多</text><text v-else-if="activeList.rows.value.length">已显示全部关系</text></view>
        </view>
        <view class="section-band">
          <text class="section-title">我收到的邀请 ({{ receivedList.total.value }})</text>
          <scroll-view scroll-y class="relationship-scroll" :style="{height:sectionHeight(receivedList.rows.value.length)}" @scrolltolower="receivedList.more()">
          <view v-for="inv in receivedList.rows.value" :key="inv.invitationId" class="child-card">
            <view class="child-avatar"><PrototypeIcon name="user" :size="44"/></view>
            <view class="child-main">
              <text class="child-name">{{ inv.studentName }}</text>
              <text class="child-sub">来自：{{ inv.inviterName || '家长' }}（{{ inv.invitationType === 'PRIMARY_TRANSFER' ? '监护权转移' : '副家长邀请' }}）</text>
              <text v-if="inv.createdAt" class="child-date">{{ dotDate(inv.createdAt) }}</text>
            </view>
            <button class="status-chip pending" @tap="openReceivedInvitation(inv.invitationId)">查看邀请</button>
          </view>
          <view v-if="receivedList.loaded.value&&!receivedList.rows.value.length" class="empty-line">暂无待处理的邀请</view>
          </scroll-view>
          <view class="relationship-paging"><text v-if="receivedList.busy.value">正在加载</text><button v-else-if="receivedList.error.value" @tap="receivedList.loaded.value?receivedList.more(true):receivedList.first()">加载失败，点击重试</button><text v-else-if="receivedList.hasMore.value">上拉加载更多</text></view>
        </view>
        <button class="inactive-entry" @tap="toggleInactive">{{showInactive?'收起已失效关系':'查看已失效的关系'}} ›</button>
        <view v-if="showInactive" class="section-band">
          <scroll-view scroll-y class="relationship-scroll" :style="{height:sectionHeight(inactiveList.rows.value.length)}" @scrolltolower="inactiveList.more()">
          <view v-for="row in inactiveList.rows.value" :key="row.studentId + (row.unboundAt || '')" class="child-card">
            <view class="child-main">
              <text class="child-name">{{ row.studentName }}</text>
              <text class="child-sub">原关系：{{ roleLabel(row.relationshipRole) }}</text>
              <text v-if="row.unboundAt" class="child-date">{{ dotDate(row.unboundAt) }} 解除</text>
            </view>
            <text class="status-chip invalid">已失效</text>
          </view>
          <view v-if="inactiveList.loaded.value&&!inactiveList.rows.value.length" class="empty-line">暂无已失效关系</view>
          </scroll-view>
          <view class="relationship-paging"><text v-if="inactiveList.busy.value">正在加载</text><button v-else-if="inactiveList.error.value" @tap="inactiveList.loaded.value?inactiveList.more(true):inactiveList.first()">加载失败，点击重试</button><text v-else-if="inactiveList.hasMore.value">上拉加载更多</text></view>
        </view>
      </template>

      <!-- 管理模式：保留既有成员管理与邀请能力 -->
      <template v-else>
      <view v-if="!invitationMode" class="heading-band">
        <text class="heading-title">家长关系</text>
        <picker v-if="students.length" :range="studentNames" :value="selectedIndex" @change="changeStudent">
          <view class="student-picker">{{ selectedStudent?.studentName || '选择学生' }}<text>⌄</text></view>
        </picker>
      </view>
      </template>

      <view v-if="errorMessage" class="error-band">{{ errorMessage }}</view>
      <view v-if="selectedStudent?.relationshipRole === 'SECONDARY_GUARDIAN'" class="readonly-band">
        副家长只读
      </view>

      <view v-if="manageMode && relationship && !invitationMode" class="relationship-list">
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
      <view v-else-if="manageMode && !errorMessage && !relationship" class="state-view">暂无家长关系</view>

      <view v-if="manageMode && canManage && !invitationMode" class="command-band">
        <button class="primary-command" :disabled="Boolean(relationship?.secondaryParent)"
                @tap="openInvitation('SECONDARY')">发起陪伴邀请</button>
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
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import InvitationQrCode from '@/components/InvitationQrCode.vue';
import {encodeParentInvitation,parseParentInvitation,invitationRoute} from '@/models/parent-invitation-qr';
import type {ParentRelationshipInvitation} from '@/api/parent-relationship';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {useRelationshipSection} from './paged-section';
import { computed, ref } from 'vue';
import { onHide, onShow, onShareAppMessage } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { request as apiRequest } from '@/api/http';
import {
  createPrimaryParentTransferInvitation,
  createSecondaryParentInvitation,
  getParentRelationships,
  pageInactiveRelationships,
  pageParentRelationshipStudents,
  pageReceivedInvitations,
  listParentRelationshipStudents,
  unbindPrimaryParent,
  unbindSecondaryParent,
  type ParentInactiveRelationship,
  type ParentReceivedInvitation,
  type ParentRelationship,
  type ParentRelationshipMember,
  type ParentRelationshipRole,
  type ParentRelationshipStudent
} from '@/api/parent-relationship';
import { getParentSession } from '@/session/parent-session';

const scannerVisible=ref(false);
const manageMode=ref(false);
const showInactive=ref(false);
function roleLabel(role: ParentRelationshipRole): string {
  return role === 'PRIMARY_GUARDIAN' ? '主家长' : '副家长';
}
/** 2026-10-13 → 2026.10.13（原型建立/解除日期格式）。 */
function dotDate(value: string): string {
  const parts = value.split('T')[0].split('-');
  return `${parts[0]}.${parts[1]}.${parts[2]}`;
}
const issuedInvitation=ref<ParentRelationshipInvitation|null>(null),manualInvitationId=ref('');
/** 8.2 对照 07-04：邀请弹层双 tab（成长伙伴占位/其他监护人二维码）。 */
const inviteTab=ref<'COMPANION'|'GUARDIAN'>('GUARDIAN');
const qrRef=ref<InstanceType<typeof InvitationQrCode>|null>(null);
async function saveQr(): Promise<void>{
  try{
    if (!qrRef.value) throw new Error('二维码尚未准备好');
    await qrRef.value.saveToAlbum();
    uni.showToast({title:'二维码已保存到相册',icon:'success'});
  }catch(e){
    uni.showToast({title:toMessage(e),icon:'none'});
  }
}
function copyInvitation(){if(issuedInvitation.value)uni.setClipboardData({data:issuedInvitation.value.invitationId});}
function openManualInvitation(){try{uni.navigateTo({url:invitationRoute(manualInvitationId.value.trim())});}catch(e){errorMessage.value=toMessage(e);}}
function scanInvitation(){uni.scanCode({onlyFromCamera:false,scanType:['qrCode'],success:r=>{try{uni.navigateTo({url:invitationRoute(parseParentInvitation(r.result))});}catch(e){errorMessage.value=toMessage(e);}},fail:()=>{errorMessage.value='扫描未完成，可填写邀请编号';}});}
function openReceivedInvitation(id: string): void {
  if (!enabled.value || loading.value) return;
  uni.navigateTo({ url: invitationRoute(id) });
}
onShareAppMessage(() => issuedInvitation.value && enabled.value
  ? { title: '邀请您共同陪伴孩子成长', path: invitationRoute(issuedInvitation.value.invitationId) }
  : { title: '灵动伴随', path: '/pages/index/index' });
const enabled = ref(false);
const canApplyTraining = ref(false);
function openTrainingApplication(){
  const selected=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string;typeCode?:string}|undefined;
  if(canApplyTraining.value&&selected?.typeCode==='FAMILY'&&selected.accessToken===getParentSession()?.accessToken)
    uni.navigateTo({url:'/pages/training-application/training-application'});
}
const loading = ref(true);
const errorMessage = ref('');
const students = ref<ParentRelationshipStudent[]>([]);
function relationshipScope(){const session=getParentSession();if(!session)return null;const selected=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string}|undefined;return {token:session.accessToken,organizationId:selected?.accessToken===session.accessToken?selected.organizationId||'':''};}
const activeList=useRelationshipSection<ParentRelationshipStudent>((scope,page)=>pageParentRelationshipStudents(scope.token,page),row=>row.studentId,relationshipScope);
const receivedList=useRelationshipSection<ParentReceivedInvitation>((scope,page)=>pageReceivedInvitations(scope.token,page),row=>row.invitationId,relationshipScope);
const inactiveList=useRelationshipSection<ParentInactiveRelationship>((scope,page)=>pageInactiveRelationships(scope.token,page),row=>row.studentId+'|'+(row.unboundAt||''),relationshipScope);
function sectionHeight(count:number){return (count?Math.min(520,count*170):76)+'rpx';}
async function toggleInactive(){showInactive.value=!showInactive.value;if(showInactive.value&&!inactiveList.loaded.value)await inactiveList.first();}
async function toggleManage(){if(loading.value)return;manageMode.value=!manageMode.value;await initialize();}
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

let pageSerial = 0;
onHide(()=>{pageSerial++;enabled.value=false;canApplyTraining.value=false;activeList.clear();receivedList.clear();inactiveList.clear();students.value=[];relationship.value=null;issuedInvitation.value=null;manualInvitationId.value='';inviteTab.value='GUARDIAN';});
onShow(() => {issuedInvitation.value=null;inviteTab.value='GUARDIAN';void initialize();});

async function initialize(): Promise<void> {
  const request = ++pageSerial;
  enabled.value = false;
  canApplyTraining.value = false;
  loading.value = true;
  errorMessage.value = '';
  const session = getParentSession();
  if (!session) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    if (request !== pageSerial || getParentSession()?.accessToken !== session.accessToken) return;
    enabled.value = capabilities.parentRelationshipManagementEnabled;
    if(capabilities.studentOrganizationRelationshipEnabled){
      const me=await apiRequest<{permissionCodes:string[]}>('/auth/me',{header:{Authorization:`Bearer ${session.accessToken}`}});
      if(request!==pageSerial||getParentSession()?.accessToken!==session.accessToken)return;
      const selected=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;typeCode?:string}|undefined;
      canApplyTraining.value=selected?.typeCode==='FAMILY'&&selected.accessToken===session.accessToken&&me.permissionCodes.includes('STUDENT_TRAINING_REQUEST_CREATE');
    }
    if (!enabled.value) return;
    if(!manageMode.value){students.value=[];relationship.value=null;await Promise.all([activeList.first(),receivedList.first(),showInactive.value?inactiveList.first():Promise.resolve()]);return;}
    const result = await listParentRelationshipStudents(session.accessToken);
    if (request !== pageSerial || getParentSession()?.accessToken !== session.accessToken) return;
    students.value = result;
    selectedIndex.value = Math.min(selectedIndex.value, Math.max(0, students.value.length - 1));
    await loadSelectedRelationship();
  } catch (error) {
    if(request===pageSerial&&getParentSession()?.accessToken===session.accessToken)errorMessage.value = toMessage(error);
  } finally {
    if (request === pageSerial) loading.value = false;
  }
}

async function loadSelectedRelationship(): Promise<void> {
  const session = getParentSession();
  if (!session || !selectedStudent.value) {
    relationship.value = null;
    return;
  }
  const studentId = selectedStudent.value.studentId;
  const request = pageSerial;
  const result = await getParentRelationships(studentId, session.accessToken);
  if (request === pageSerial && getParentSession()?.accessToken === session.accessToken && selectedStudent.value?.studentId === studentId) relationship.value = result;
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
  if (!enabled.value || loading.value || !canManage.value || submitting.value) return;
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
  if (!enabled.value || loading.value || submitting.value || !canManage.value || !session || !selectedStudent.value || !invitationMode.value) return;
  if (!/^1[3-9]\d{9}$/.test(targetMobile.value)) {
    dialogError.value = '手机号格式不正确';
    return;
  }
  const request = pageSerial;
  submitting.value = true;
  dialogError.value = '';
  try {
    const result = invitationMode.value === 'SECONDARY'
      ? await createSecondaryParentInvitation(
        selectedStudent.value.studentId, targetMobile.value, session.accessToken)
      : await createPrimaryParentTransferInvitation(
        selectedStudent.value.studentId, targetMobile.value, session.accessToken);
    if(request !== pageSerial || !enabled.value || getParentSession()?.accessToken!==session.accessToken)return;
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
.prototype-header{position:relative}
.manage-link{position:absolute;right:24rpx;top:50%;transform:translateY(-50%);margin:0;background:rgba(255,255,255,.16);color:#fff;font-size:26rpx;padding:10rpx 24rpx;border-radius:999rpx}
.section-band{padding:26rpx;background:#fff;border-radius:24rpx;margin-bottom:22rpx}
.section-title{display:block;font-size:30rpx;font-weight:650;margin-bottom:20rpx}
.child-card{display:flex;align-items:center;gap:16rpx;padding:20rpx 16rpx;margin:12rpx 0;border:1rpx solid #e5ede6;border-radius:20rpx;background:#fcfefc;box-sizing:border-box;min-height:142rpx}
.child-avatar{width:80rpx;height:80rpx;flex-shrink:0;border-radius:50%;background:linear-gradient(135deg,#e2f0e8,#fff0d9);display:flex;align-items:center;justify-content:center;}.relationship-scroll{width:100%;margin-top:12rpx;}.relationship-paging{font-size:22rpx;color:#819488;text-align:center;line-height:1.5;padding-top:12rpx;}.relationship-paging button{font-size:23rpx;background:#eff6f1;color:#24684c;padding:12rpx;margin:0;}
.child-main{flex:1;min-width:0}
.child-name{font-size:30rpx;font-weight:650;margin-right:12rpx;word-break:break-all}
.role-chip{display:inline-block;font-size:22rpx;color:#fff;background:#e2a33c;border-radius:10rpx;padding:2rpx 10rpx;margin-left:10rpx;vertical-align:middle}
.child-sub,.child-date{display:block;font-size:24rpx;color:#7c847e;margin-top:8rpx}
.status-chip{font-size:24rpx;border-radius:999rpx;padding:8rpx 20rpx;flex-shrink:0}
.status-chip.pass{background:#e6f4e4;color:#147c51}
.status-chip.pending{background:#fdf0e0;color:#b97708}
.status-chip.invalid{background:#f0f0ee;color:#8a8f88}
.empty-line{padding:18rpx 0;font-size:26rpx;color:#7b847d}
.inactive-entry,.scanner-toggle{width:100%;background:#fff;color:#225a40;font-size:28rpx;text-align:left;padding:26rpx;border-radius:24rpx;margin-bottom:22rpx} 
/* 8.2 对照 07-04：邀请弹层双 tab + 二维码 + 保存/分享 */
.invite-dialog{display:flex;flex-direction:column;align-items:center}
.invite-tabs{display:flex;width:100%;background:#f2f6f0;border-radius:18rpx;padding:8rpx;margin-bottom:28rpx}
.invite-tab{flex:1;margin:0;background:transparent;color:#5f6b62;font-size:26rpx;padding:14rpx 0;border-radius:14rpx;text-align:center}
.invite-tab.active{background:#176f50;color:#fff;font-weight:650}
.invite-title{display:block;font-size:30rpx;font-weight:650;margin:10rpx 0 12rpx;text-align:center}
.invite-sub{display:block;font-size:24rpx;color:#7c847e;margin:6rpx 0;text-align:center}
.invite-actions{display:flex;gap:16rpx;width:100%;margin-top:28rpx}
.invite-actions button{flex:1;margin:0}
.invite-save{background:#176f50;color:#fff}
.invite-share{background:#e2a33c;color:#fff}
.invite-dialog .plain-command{width:100%;background:#e9f2e6;color:#225a40;margin-top:16rpx}
.relationship-toolbar{display:flex;justify-content:flex-end;margin:0 0 12rpx;}.relationship-toolbar .manage-link{margin:0;padding:8rpx 18rpx;line-height:1.5;background:#eaf2eb;color:#17664c;border:0;border-radius:18rpx;font-size:24rpx;}
</style>
