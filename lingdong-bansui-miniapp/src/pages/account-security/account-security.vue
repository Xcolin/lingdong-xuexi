<template>
  <view class="prototype-workspace has-tabbar page-shell">
    <PrototypePageHeader title="登录设备管理" icon="shield"/>
    <view class="security-tabs"><button :class="{active:!showEvents}" :disabled="loading || !!loadError" @tap="showEvents=false">登录设备</button><button class="event-entry" :class="{active:showEvents}" :disabled="loading || !!loadError" @tap="showEvents=true">安全提醒</button></view>
    <view v-if="loading" class="empty-text">正在加载账号安全信息</view>
    <view v-if="loadError" class="warning-band"><text>{{ loadError }}</text><button class="text-button" @tap="loadData">重新加载</button></view>
    <view v-if="warningCount > 0" class="warning-band">
      <text class="warning-title">发现 {{ warningCount }} 条新设备登录提醒</text>
      <text class="warning-copy">请核对设备，发现异常时及时下线。</text>
    </view>

    <view v-if="!showEvents" class="section-band device-section">
      <view class="section-heading">
        <text class="section-title">已登录设备（{{ deviceTotal }}）</text>
        <button class="text-button danger" :disabled="loading || !!loadError" @tap="confirmSignOutAll">全部下线</button>
      </view>
      <view v-if="!loading && !loadError && devices.length === 0" class="empty-text">暂无活动设备</view>
      <view v-for="device in devices" :key="device.id" class="list-row">
        <view class="row-main">
          <text class="row-title">{{ device.deviceName }}</text>
          <text class="row-meta">{{ clientName(device.clientType) }} · {{ formatTime(device.lastActiveAt) }}</text>
        </view>
        <text v-if="device.current" class="current-tag">当前设备</text>
        <button v-else class="text-button danger" @tap="confirmSignOutDevice(device)">下线</button>
      </view>
    </view>

    <view class="load-more" v-if="!showEvents && devices.length"><text v-if="appending">正在加载更多设备</text><button v-else-if="appendError" @tap="loadMore">加载失败，点击重试</button><text v-else-if="hasMore">上拉加载更多设备</text><text v-else>已显示全部设备</text></view>
    <view v-if="showEvents" class="section-band event-section">
      <view class="section-heading">
        <text class="section-title">安全事件（{{eventTotal}}）</text>
        <button class="text-button event-toggle" :disabled="loading || !!loadError" @tap="toggleEvents">{{showEvents?'收起':'查看'}}</button>
      </view>
      <template v-if="showEvents">
      <button class="text-button" :disabled="loading || !!loadError" @tap="markAllRead">全部已读</button>
      <view v-if="!loading && !loadError && events.length === 0" class="empty-text">暂无安全事件</view>
      <view v-for="event in events" :key="event.id" class="list-row">
        <view class="row-main">
          <text class="row-title">{{ eventName(event.eventType) }}</text>
          <text class="row-meta">{{ event.deviceName }} · {{ formatTime(event.occurredAt) }}</text>
        </view>
        <button v-if="event.status === 'UNREAD'" class="text-button" @tap="markRead(event.id)">标记已读</button>
        <text v-else class="read-tag">已读</text>
      </view>
      <view class="load-more" v-if="events.length"><text v-if="eventAppending">正在加载更多提醒</text><button v-else-if="eventAppendError" @tap="loadMoreEvents">加载失败，点击重试</button><text v-else-if="eventHasMore">上拉加载更多提醒</text><text v-else>已显示全部提醒</text></view>
      </template>
    </view>
  <AppTabBar v-if="identity === 'organization'" :items="teacherWorkbench?TEACHER_TABS:ORG_TABS" :active="3" />
  </view>
</template>

<script setup lang="ts">
import PrototypePageHeader from '@/components/PrototypePageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { TEACHER_TABS, ORG_TABS } from '@/config/tabbar';
import { computed, ref } from 'vue';
import { onLoad, onShow, onHide, onUnload, onReachBottom } from '@dcloudio/uni-app';
import {
  listAccountDevicesPage,
  listAccountSecurityEventsPage,
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
const teacherWorkbench=ref(false);
const accessToken = ref('');
const devices = ref<AccountDeviceSession[]>([]);
const events = ref<AccountSecurityEvent[]>([]);
const loading = ref(false), loadError = ref('');
const devicePage=ref(1),deviceTotal=ref(0),hasMore=ref(false),appending=ref(false),appendError=ref('');
const showEvents=ref(false),eventPage=ref(1),eventTotal=ref(0),eventHasMore=ref(false),eventAppending=ref(false),eventAppendError=ref(''),warningCount=ref(0);
function toggleEvents(){showEvents.value=!showEvents.value;}
let loadRevision = 0;
function currentToken() {
  return (identity.value === 'parent' ? getParentSession() : identity.value === 'student' ? getStudentSession() : getOrganizationSession())?.accessToken;
}
function clearLoading() { loadRevision++; loading.value=false; devices.value=[]; events.value=[]; loadError.value='';devicePage.value=1;deviceTotal.value=0;hasMore.value=false;appending.value=false;appendError.value='';showEvents.value=false;eventPage.value=1;eventTotal.value=0;eventHasMore.value=false;eventAppending.value=false;eventAppendError.value='';warningCount.value=0; }
onHide(clearLoading);
onUnload(clearLoading);
onShow(() => { if(accessToken.value && !loading.value) void loadData(); });

onLoad(async (query) => {
  if (query?.identity !== 'parent'
      && query?.identity !== 'student'
      && query?.identity !== 'organization') return leavePage();
  identity.value = query.identity;
  teacherWorkbench.value=query.workbench==='teacher';
  const session = identity.value === 'parent'
    ? getParentSession()
    : identity.value === 'student'
      ? getStudentSession()
      : getOrganizationSession();
  if (!session) return leavePage();
  accessToken.value = session.accessToken;
  await loadData();
});

async function loadData(): Promise<void> {
  const token=currentToken();
  if(!token){clearLoading();leavePage();return;}
  accessToken.value=token;
  const revision=++loadRevision, sourceIdentity=identity.value;
  loading.value=true; loadError.value=''; devices.value=[]; events.value=[];
  devicePage.value=1;deviceTotal.value=0;hasMore.value=false;appending.value=false;appendError.value='';
  eventPage.value=1;eventTotal.value=0;eventHasMore.value=false;eventAppending.value=false;eventAppendError.value='';warningCount.value=0;
  const valid=()=>revision===loadRevision && identity.value===sourceIdentity && currentToken()===token;
  try {
    const capabilities=await getMiniappCapabilities();
    if(!valid()) return;
    if(!capabilities.accountSecurityManagementEnabled){leavePage();return;}
    const [deviceRows,eventRows]=await Promise.all([
      listAccountDevicesPage(sourceIdentity,token,1),listAccountSecurityEventsPage(sourceIdentity,token,1)
    ]);
    if(valid()){devices.value=deviceRows.items;devicePage.value=deviceRows.page;deviceTotal.value=deviceRows.total;hasMore.value=deviceRows.items.length>0&&deviceRows.page*deviceRows.pageSize<deviceRows.total;events.value=eventRows.items;eventPage.value=eventRows.page;eventTotal.value=eventRows.total;eventHasMore.value=eventRows.items.length>0&&eventRows.page*eventRows.pageSize<eventRows.total;warningCount.value=eventRows.unreadWarningTotal;}
  } catch(cause) {
    if(valid()) loadError.value=cause instanceof Error?cause.message:'账号安全信息加载失败，请重试';
  } finally { if(revision===loadRevision) loading.value=false; }
}

async function loadMore():Promise<void>{
  const token=accessToken.value,sourceIdentity=identity.value,revision=loadRevision;
  if(loading.value||appending.value||loadError.value||!hasMore.value||currentToken()!==token)return;
  appending.value=true;appendError.value='';
  const valid=()=>revision===loadRevision&&identity.value===sourceIdentity&&currentToken()===token;
  try{const result=await listAccountDevicesPage(sourceIdentity,token,devicePage.value+1);if(!valid())return;
    const ids=new Set(devices.value.map(item=>item.id));const added=result.items.filter(item=>{if(ids.has(item.id))return false;ids.add(item.id);return true;});
    devices.value=[...devices.value,...added];devicePage.value=result.page;deviceTotal.value=result.total;
    hasMore.value=added.length>0&&result.page*result.pageSize<result.total;
  }catch(cause){if(valid())appendError.value=cause instanceof Error?cause.message:'设备加载失败';}
  finally{if(revision===loadRevision)appending.value=false;}
}
async function loadMoreEvents():Promise<void>{
  const token=accessToken.value,sourceIdentity=identity.value,revision=loadRevision;
  if(!showEvents.value||loading.value||eventAppending.value||loadError.value||!eventHasMore.value||currentToken()!==token)return;
  eventAppending.value=true;eventAppendError.value='';
  const valid=()=>revision===loadRevision&&identity.value===sourceIdentity&&currentToken()===token;
  try{const result=await listAccountSecurityEventsPage(sourceIdentity,token,eventPage.value+1);if(!valid())return;
    const ids=new Set(events.value.map(item=>item.id));const added=result.items.filter(item=>{if(ids.has(item.id))return false;ids.add(item.id);return true;});
    events.value=[...events.value,...added];eventPage.value=result.page;eventTotal.value=result.total;warningCount.value=result.unreadWarningTotal;
    eventHasMore.value=added.length>0&&result.page*result.pageSize<result.total;
  }catch(cause){if(valid())eventAppendError.value=cause instanceof Error?cause.message:'提醒加载失败';}
  finally{if(revision===loadRevision)eventAppending.value=false;}
}
onReachBottom(()=>{if(showEvents.value){if(!eventAppendError.value)void loadMoreEvents();}else if(!appendError.value)void loadMore();});

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
    await loadData();
  } catch {
    uni.showToast({ title: '标记已读失败', icon: 'none' });
  }
}

async function markAllRead(): Promise<void> {
  try {
    await markAllAccountSecurityEventsRead(identity.value, accessToken.value);
    await loadData();
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
.security-tabs{display:flex;gap:8rpx;padding:8rpx;margin:20rpx 0;background:#eaf1ec;border-radius:22rpx;}.security-tabs button{flex:1;margin:0;background:transparent;color:#71877b;font-size:26rpx;padding:14rpx;line-height:1.5;}.security-tabs button.active{background:white;color:#155941;box-shadow:0 3rpx 10rpx #173c2910;font-weight:650;}

.page-shell { min-height: calc(100vh - var(--window-top,0px)); padding: 32rpx; box-sizing: border-box; background: #f4f7f5; }
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

@import "@/styles/workspace-prototype.scss";

.prototype-workspace{background:#f4faf7!important;padding:0 28rpx 48rpx!important;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;color:#172d27;}
.prototype-workspace.has-tabbar{padding-bottom:160rpx!important;}
.prototype-header{margin:0 -28rpx 28rpx;padding:32rpx 32rpx 48rpx;display:flex;align-items:center;gap:18rpx;background:linear-gradient(115deg,#226d5c,#07573f);color:#fff;border-radius:0 0 30rpx 30rpx;}
.prototype-header-title{font-size:34rpx;font-weight:700;letter-spacing:1rpx;}
.card,.section-band,.summary-band,.action-band,.heading-band{background:#fff!important;border:1rpx solid #e2eee7!important;border-radius:24rpx!important;box-shadow:none!important;margin-bottom:22rpx;padding:24rpx!important;}
input,textarea,.field{background:#fbfcfb!important;border:1rpx solid #dce5df!important;border-radius:14rpx!important;padding:18rpx!important;box-sizing:border-box;font-size:26rpx!important;}
input{min-height:76rpx;}textarea{width:100%;}
button{border-radius:18rpx!important;font-size:26rpx!important;}button::after{border:0;}
.heading,.section-title,.row-title,.task-title{color:#162b24;font-weight:650;font-size:28rpx;}
.row-meta,.task-meta,.hint{color:#78847e;font-size:24rpx;line-height:1.65;}
.list-row,.relationship-row,.task-row{border-bottom:1rpx solid #edf1ee;padding:24rpx 0;}
.current-tag,.read-tag,.source-badge{background:#e7f7e9;color:#168450;border-radius:10rpx;padding:6rpx 12rpx;font-size:22rpx;}

.secondary-page,.prototype-workspace{background:#f5f9f5!important;padding:24rpx 28rpx 70rpx!important;color:#1a382a;}
.section-band{background:white;border:0;border-radius:26rpx;padding:26rpx;margin-bottom:24rpx;}.list-row{background:#fafcfb;border:1rpx solid #e0e8e2;border-radius:20rpx;padding:24rpx;margin:20rpx 0;}.row-title{font-size:29rpx;color:#1d3228;}.row-meta{font-size:24rpx;line-height:1.6;color:#818b84;}.current-tag{background:#d9f9d8;color:#227b39;border-radius:10rpx;font-size:23rpx;padding:8rpx 12rpx;}.text-button.danger{background:#edf1ee;color:#466056;border-radius:14rpx;padding:8rpx 24rpx;}.warning-band{background:#fff2df;border-radius:20rpx;color:#a65c25;padding:24rpx;}.section-title{font-size:29rpx;}
</style>
