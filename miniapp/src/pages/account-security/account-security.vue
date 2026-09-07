<template>
  <view class="page-shell">
    <view v-if="warningCount > 0" class="warning-band">
      <text class="warning-title">发现 {{ warningCount }} 条新设备登录提醒</text>
      <text class="warning-copy">请核对设备，发现异常时及时下线。</text>
    </view>

    <view class="section-band">
      <view class="section-heading">
        <text class="section-title">登录设备</text>
        <button class="text-button danger" @tap="confirmSignOutAll">全部下线</button>
      </view>
      <view v-if="devices.length === 0" class="empty-text">暂无活动设备</view>
      <view v-for="device in devices" :key="device.id" class="list-row">
        <view class="row-main">
          <text class="row-title">{{ device.deviceName }}</text>
          <text class="row-meta">{{ clientName(device.clientType) }} · {{ formatTime(device.lastActiveAt) }}</text>
        </view>
        <text v-if="device.current" class="current-tag">当前设备</text>
        <button v-else class="text-button danger" @tap="confirmSignOutDevice(device)">下线</button>
      </view>
    </view>

    <view class="section-band">
      <view class="section-heading">
        <text class="section-title">安全事件</text>
        <button class="text-button" @tap="markAllRead">全部已读</button>
      </view>
      <view v-if="events.length === 0" class="empty-text">暂无安全事件</view>
      <view v-for="event in events" :key="event.id" class="list-row">
        <view class="row-main">
          <text class="row-title">{{ eventName(event.eventType) }}</text>
          <text class="row-meta">{{ event.deviceName }} · {{ formatTime(event.occurredAt) }}</text>
        </view>
        <button v-if="event.status === 'UNREAD'" class="text-button" @tap="markRead(event.id)">标记已读</button>
        <text v-else class="read-tag">已读</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  listAccountDevices,
  listAccountSecurityEvents,
  markAccountSecurityEventRead,
  markAllAccountSecurityEventsRead,
  signOutAccountDevice,
  signOutAllAccountDevices,
  type AccountDeviceSession,
  type AccountSecurityEvent,
  type MiniappAuthIdentity
} from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { clearParentSession, getParentSession } from '@/session/parent-session';
import { clearStudentSession, getStudentSession } from '@/session/student-session';
import { clearOrganizationSession, getOrganizationSession } from '@/session/organization-session';

const identity = ref<MiniappAuthIdentity>('student');
const accessToken = ref('');
const devices = ref<AccountDeviceSession[]>([]);
const events = ref<AccountSecurityEvent[]>([]);
const warningCount = computed(() => events.value.filter((event) => event.riskLevel === 'WARNING' && event.status === 'UNREAD').length);

onLoad(async (query) => {
  if (query?.identity !== 'parent'
      && query?.identity !== 'student'
      && query?.identity !== 'organization') return leavePage();
  identity.value = query.identity;
  const session = identity.value === 'parent'
    ? getParentSession()
    : identity.value === 'student'
      ? getStudentSession()
      : getOrganizationSession();
  if (!session) return leavePage();
  accessToken.value = session.accessToken;
  try {
    const capabilities = await getMiniappCapabilities();
    if (!capabilities.accountSecurityManagementEnabled) return leavePage();
    await loadData();
  } catch {
    uni.showToast({ title: '账号安全信息加载失败', icon: 'none' });
  }
});

async function loadData(): Promise<void> {
  [devices.value, events.value] = await Promise.all([
    listAccountDevices(identity.value, accessToken.value),
    listAccountSecurityEvents(identity.value, accessToken.value)
  ]);
}

async function confirmSignOutDevice(device: AccountDeviceSession): Promise<void> {
  const result = await uni.showModal({ title: '下线设备', content: `确认下线“${device.deviceName}”？` });
  if (!result.confirm) return;
  try {
    await signOutAccountDevice(identity.value, accessToken.value, device.id);
    await loadData();
  } catch {
    uni.showToast({ title: '设备下线失败，请稍后重试', icon: 'none' });
  }
}

async function confirmSignOutAll(): Promise<void> {
  const result = await uni.showModal({ title: '下线全部设备', content: '当前设备也会退出，是否继续？' });
  if (!result.confirm) return;
  try {
    await signOutAllAccountDevices(identity.value, accessToken.value);
    clearIdentitySession();
    await uni.reLaunch({ url: '/pages/index/index' });
  } catch {
    uni.showToast({ title: '全部下线失败，当前会话已保留', icon: 'none' });
  }
}

async function markRead(eventId: string): Promise<void> {
  try {
    await markAccountSecurityEventRead(identity.value, accessToken.value, eventId);
    const event = events.value.find((item) => item.id === eventId);
    if (event) event.status = 'READ';
  } catch {
    uni.showToast({ title: '标记已读失败', icon: 'none' });
  }
}

async function markAllRead(): Promise<void> {
  try {
    await markAllAccountSecurityEventsRead(identity.value, accessToken.value);
    events.value.forEach((event) => { event.status = 'READ'; });
  } catch {
    uni.showToast({ title: '全部标记已读失败', icon: 'none' });
  }
}

function clearIdentitySession(): void {
  if (identity.value === 'parent') clearParentSession();
  else if (identity.value === 'student') clearStudentSession();
  else clearOrganizationSession();
}

function leavePage(): void {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/index/index' }) });
}

function eventName(type: AccountSecurityEvent['eventType']): string {
  if (type === 'NEW_DEVICE_LOGIN') return '新设备登录';
  if (type === 'DEVICE_REVOKED') return '设备已下线';
  return '全部设备已下线';
}

function clientName(client: AccountDeviceSession['clientType']): string {
  return client === 'WEB' ? 'Web' : '小程序';
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 32rpx; box-sizing: border-box; background: #f4f7f5; }
.warning-band { padding: 28rpx; border-left: 8rpx solid #d97706; background: #fff7e6; }
.warning-title, .warning-copy, .section-title, .row-title, .row-meta { display: block; }
.warning-title { color: #7a3f0d; font-size: 30rpx; font-weight: 700; }
.warning-copy { margin-top: 10rpx; color: #8c5b2c; font-size: 24rpx; }
.section-band { margin-top: 28rpx; background: #ffffff; border: 2rpx solid #dbe3df; border-radius: 10rpx; }
.section-heading, .list-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 26rpx 28rpx; }
.section-heading { border-bottom: 2rpx solid #e7ece9; }
.section-title { color: #1c2b28; font-size: 32rpx; font-weight: 700; }
.list-row + .list-row { border-top: 2rpx solid #edf1ef; }
.row-main { min-width: 0; flex: 1; }
.row-title { color: #1c2b28; font-size: 28rpx; }
.row-meta { margin-top: 8rpx; color: #708078; font-size: 22rpx; }
.text-button { min-width: 112rpx; margin: 0; padding: 0 16rpx; background: transparent; color: #167c5a; font-size: 24rpx; }
.text-button::after { border: 0; }
.danger { color: #a44835; }
.current-tag, .read-tag { color: #167c5a; font-size: 23rpx; }
.read-tag { color: #708078; }
.empty-text { padding: 42rpx 28rpx; color: #708078; font-size: 26rpx; text-align: center; }
</style>
