<template>
 <view class="companion-home ld-page has-tabbar"><NativeStatusSpacer/>
  <view class="home-toolbar"><button class="organization-choice" @tap="chooseOrganization"><text>{{selectedFamilyName||'灵动伴随'}}</text><text class="choice-chevron">⌄</text></button><button v-if="createTaskEnabled" class="toolbar-action" @tap="openFamilyTasks"><PrototypeIcon name="plus" :size="40"/></button></view>
  <view class="home-greeting"><text class="greeting-title">今天，一起陪伴成长</text><text class="greeting-caption">点滴行动，汇聚孩子的美好未来。</text><image class="botanical" src="/static/growth-botanical.svg" mode="aspectFit"/></view>
  <view v-if="reviewEnabled||exchangeReviewEnabled" class="pending-card"><view class="section-heading"><text>待处理</text><button v-if="reviewEnabled" @tap="openReviews">查看全部<PrototypeIcon name="arrow" :size="22"/></button></view><button v-if="reviewEnabled" class="pending-content" @tap="openReviews"><view class="pending-icon"><PrototypeIcon name="task" tone="amber" :size="44"/></view><view><text class="pending-number">{{reviewLoading?'—':reviewError?'—':reviewTotal}}</text><text class="pending-label">任务待审核</text></view><text class="pending-error" v-if="reviewError">暂未加载，请重试</text><PrototypeIcon name="arrow" :size="28"/></button><button v-if="exchangeReviewEnabled" class="pending-content" @tap="openRewards"><view class="pending-icon"><PrototypeIcon name="gift" tone="amber" :size="44"/></view><view><text class="pending-number">{{exchangeLoading||exchangeError?'—':exchangePending}}</text><text class="pending-label">兑换待处理</text></view></button></view>
  <view v-if="createTaskEnabled||weeklyEnabled||rewardEnabled||reviewEnabled" class="shortcut-card">
   <button v-if="createTaskEnabled" @tap="openFamilyTasks"><view class="shortcut-icon"><PrototypeIcon name="task" :size="38"/></view><text>发布任务</text></button>
   <button v-if="reviewEnabled" @tap="openReviews"><view class="shortcut-icon"><PrototypeIcon name="people" :size="38"/></view><text>审核任务</text></button>
   <button v-if="weeklyEnabled" @tap="openWeekly"><view class="shortcut-icon"><PrototypeIcon name="chart" :size="38"/></view><text>成长周报</text></button>
   <button v-if="rewardEnabled" @tap="openRewards"><view class="shortcut-icon"><PrototypeIcon name="gift" :size="38"/></view><text>奖励管理</text></button>
  </view>
  <view v-if="activityEnabled" class="recent-section"><view class="section-heading"><text>最新动态</text><button @tap="openReviews">查看全部<PrototypeIcon name="arrow" :size="22"/></button></view><view class="recent-group"><view v-if="recentLoading" class="empty-state">正在加载动态…</view><view v-else-if="recentError" class="empty-state">{{recentError}}</view><view v-else-if="!recentTasks.length" class="empty-state"><text>暂无待审核提交动态</text></view><button v-for="task in recentTasks" :key="task.id" class="recent-row" @tap="openReviews"><view class="row-icon"><PrototypeIcon name="book" :size="36"/></view><view class="recent-body"><text class="recent-title">{{task.studentName}}提交了任务</text><text class="recent-meta">{{task.title}} · {{task.occurredAt.replace('T',' ').slice(0,19)}}</text></view><text class="status-pill">待审核</text></button></view></view>
  <AppTabBar :items="PARENT_TABS" :active="0"/>
 </view>
</template>

<script setup lang="ts">
import NativeStatusSpacer from "@/components/NativeStatusSpacer.vue";
import {parentRewardApi,REVIEW_EXCHANGES} from '@/api/parent-reward';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {listPartnerActivities,type PartnerActivity} from '@/api/partner-activity';
import {parentChildrenApi} from '@/api/parent-children';
import {isParentIdentity} from "@/models/parent-identity";
import { ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { getParentAuthContext, getParentState, logoutParent } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { rankApi } from '@/api/anonymous-rank';
import { parentReviewApi } from '@/api/parent-task-review';
import { canReview } from '@/models/parent-task-reviews';
import { canReadWeekly } from '@/api/parent-weekly-review';
import { canManageFamily } from '@/models/family-task-draft';
import { canUseParentRewards } from '@/api/parent-reward';
import { useAttendanceEntry } from '@/composables/use-attendance-entry';
import AppTabBar from '@/components/AppTabBar.vue';
import { PARENT_TABS } from '@/config/tabbar';
const { attendanceEnabled, openAttendance } = useAttendanceEntry('parent');
import {
  clearParentSession,
  getParentSession,
  type StoredParentSession
} from '@/session/parent-session';

const recentTasks=ref<PartnerActivity[]>([]),recentLoading=ref(false),recentError=ref(''),childrenEnabled=ref(false),activityEnabled=ref(false);
function chooseOrganization(){return uni.navigateTo({url:'/pages/my-organizations/my-organizations?identity=parent&choose=1'});}
function openChildren(){return uni.navigateTo({url:'/pages/parent-children/parent-children'});}
const session = ref<StoredParentSession | null>(null);
const loggingOut = ref(false);
const relationshipEnabled = ref(false);
const accountSecurityEnabled = ref(false);
const accountLifecycleEnabled = ref(false);
const studentWechatAuthEnabled = ref(false);
const weeklyEnabled = ref(false);
const familyTaskEnabled = ref(false),createTaskEnabled=ref(false);
const rewardEnabled = ref(false);
function openRewards() { return uni.navigateTo({ url: '/pages/parent-rewards/parent-rewards' }); }
function openFamilyTasks() { return uni.navigateTo({ url: '/pages/family-tasks/family-tasks' }); }
function openWeekly() { return uni.navigateTo({ url: '/pages/parent-weekly-reviews/parent-weekly-reviews' }); }
const reviewEnabled = ref(false), reviewLoading = ref(false), reviewTotal = ref(0), reviewError = ref('');
let homeRequest = 0;
function openReviews() { return uni.navigateTo({ url: '/pages/parent-task-reviews/parent-task-reviews' }); }
onHide(() => { homeRequest++; childrenEnabled.value=false; activityEnabled.value=false; recentTasks.value=[]; reviewEnabled.value = false; weeklyEnabled.value = false; familyTaskEnabled.value = false; createTaskEnabled.value=false; rewardEnabled.value = false; });
const rankEnabled = ref(false), rankWithdrawalEnabled = ref(false);
function openRank(withdraw: boolean) {
  return uni.navigateTo({ url: `/pages/anonymous-ranks/anonymous-ranks${withdraw ? '?withdraw=true' : ''}` });
}

const exchangeReviewEnabled=ref(false),exchangeLoading=ref(false),exchangeError=ref(false),exchangePending=ref(0);
const selectedFamilyName = ref('');
onShow(async () => {
  const selection = uni.getStorageSync('lingdong.selected-organization') as {accessToken?: string; name?: string; typeCode?: string; organizationId?: string} | undefined;
  selectedFamilyName.value = selection?.typeCode === 'FAMILY' && selection?.accessToken === getParentSession()?.accessToken ? selection?.name || '' : '';
  activityEnabled.value=false;
  exchangeReviewEnabled.value=false;exchangePending.value=0;exchangeError.value=false;
  const request = ++homeRequest; const token=getParentSession()?.accessToken; recentTasks.value=[];recentError.value='';childrenEnabled.value=false; reviewEnabled.value = false; reviewError.value = ''; reviewTotal.value = 0;
  weeklyEnabled.value = false;
  familyTaskEnabled.value = false; createTaskEnabled.value=false;
  rewardEnabled.value = false;
  rankEnabled.value = false; rankWithdrawalEnabled.value = false;
  session.value = getParentSession();
  if (!session.value) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const [context, state, capabilities, user] = await Promise.all([
      getParentAuthContext(), getParentState(session.value.accessToken), getMiniappCapabilities(), rankApi.me(session.value.accessToken)
    ]);
    if (request !== homeRequest || getParentSession()?.accessToken!==token) return;
    if (!context.enabled) {
      clearParentSession();
      await uni.reLaunch({ url: '/pages/index/index' });
      return;
    }
    if (state.onboardingRequired || state.agreementAcceptanceRequired) {
      await uni.redirectTo({ url: '/pages/parent-onboarding/parent-onboarding' });
      return;
    }
    relationshipEnabled.value = capabilities.parentRelationshipManagementEnabled;
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
    accountLifecycleEnabled.value = capabilities.parentAccountLifecycleEnabled;
    studentWechatAuthEnabled.value = capabilities.studentWechatAuthEnabled === true;
    rankWithdrawalEnabled.value = isParentIdentity(user);
    rankEnabled.value = rankWithdrawalEnabled.value && capabilities.anonymousClassRankEnabled === true
      && user.permissionCodes.includes('MINIAPP_ANONYMOUS_CLASS_RANK_READ');
    reviewEnabled.value = canReview(user, capabilities.learningTaskManagementEnabled);
    weeklyEnabled.value = canReadWeekly(user);
    familyTaskEnabled.value = capabilities.learningTaskManagementEnabled && canManageFamily(user, 'LEARNING_TASK_READ_MANAGED');
    createTaskEnabled.value=familyTaskEnabled.value&&canManageFamily(user,'LEARNING_TASK_CREATE');
    rewardEnabled.value = capabilities.rewardExchangeEnabled && canUseParentRewards(user);
    exchangeReviewEnabled.value=capabilities.rewardExchangeEnabled&&canUseParentRewards(user,REVIEW_EXCHANGES);
    if(exchangeReviewEnabled.value){exchangeLoading.value=true;try{const result=await parentRewardApi.summary(token!);if(request===homeRequest&&getParentSession()?.accessToken===token)exchangePending.value=result.pendingApprovalCount+result.pendingVerificationCount;}catch{if(request===homeRequest)exchangeError.value=true;}finally{if(request===homeRequest)exchangeLoading.value=false;}}
    childrenEnabled.value=isParentIdentity(user)&&['STUDENT_READ','STUDENT_CREATE'].some(code=>user.permissionCodes.includes(code));
    activityEnabled.value=reviewEnabled.value&&user.permissionCodes.includes('STUDENT_READ')&&selection?.typeCode==='FAMILY'&&selection?.accessToken===token&&!!selection.organizationId;
    if(activityEnabled.value){
      const familyId=selection!.organizationId!;
      const current=()=>{const selected=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string;typeCode?:string}|undefined;return request===homeRequest&&getParentSession()?.accessToken===token&&selected?.accessToken===token&&selected?.organizationId===familyId&&selected?.typeCode==='FAMILY';};
      recentLoading.value=true;
      try{
        const studentIds:string[]=[];let next=1,seen=0;
        while(current()){
          const result=await parentChildrenApi.list(token!,next);
          if(!current())return;
          studentIds.push(...result.items.map(child=>child.id));seen+=result.items.length;
          if(!result.items.length||seen>=result.total)break;next++;
        }
        if(!current())return;
        const events=await listPartnerActivities(token!,{studentIds,source:'FAMILY'},user.permissionCodes,capabilities.learningTaskManagementEnabled,3,current);
        if(current())recentTasks.value=events;
      }catch{if(current())recentError.value='最新动态暂未加载，请进入审核列表重试';}
      finally{if(current())recentLoading.value=false;}
    }

    if(request!==homeRequest||getParentSession()?.accessToken!==token)return;
    if (reviewEnabled.value) {
      reviewLoading.value = true;
      try { const result = await parentReviewApi.list(session.value.accessToken); if (request === homeRequest&&getParentSession()?.accessToken===token) reviewTotal.value = result.total; }
      catch { if (request === homeRequest&&getParentSession()?.accessToken===token) reviewError.value = '待审核任务加载失败，请进入列表重试'; }
      finally { if (request === homeRequest) reviewLoading.value = false; }
    }
  } catch {
    if (request !== homeRequest || getParentSession()?.accessToken!==token) return;
    clearParentSession();
    await uni.reLaunch({ url: '/pages/index/index' });
  }
});

async function logout(): Promise<void> {
  if (!session.value || loggingOut.value) return;
  const logoutToken=session.value.accessToken;
  loggingOut.value = true;
  try {
    await logoutParent(session.value.accessToken);
  } catch {
    // 服务端不可用时仍清除本地家长会话，不影响独立的学生会话。
  } finally {
    if(getParentSession()?.accessToken!==logoutToken){loggingOut.value=false;return;}
    clearParentSession();
    session.value = null;
    loggingOut.value = false;
    await uni.reLaunch({ url: '/pages/index/index' });
  }
}

</script>

<style scoped>
.companion-home{background:#f5f8f4;min-height:calc(100vh - var(--window-top, 0px));box-sizing:border-box;padding:18rpx 32rpx calc(134rpx + env(safe-area-inset-bottom));color:#193e32;}
button{margin:0;padding:0;line-height:1.4;background:transparent;}button::after{border:0;}
.home-toolbar{display:flex;align-items:center;justify-content:space-between;min-height:58rpx;}.organization-choice{display:flex;align-items:center;gap:12rpx;color:#183f33;font-size:27rpx;font-weight:650;max-width:88%;text-align:left;}.organization-choice>text:first-of-type{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}.choice-chevron{color:#607c68;font-size:30rpx;}.toolbar-action{padding:8rpx;}
.home-greeting{position:relative;min-height:125rpx;display:flex;flex-direction:column;justify-content:center;margin:18rpx 0 10rpx;overflow:hidden;}.greeting-title{position:relative;z-index:1;font-size:36rpx;font-weight:700;letter-spacing:1rpx;}.greeting-caption{position:relative;z-index:1;color:#7a8c80;font-size:23rpx;margin-top:12rpx;}.botanical{position:absolute;right:-8rpx;bottom:-48rpx;width:165rpx;height:200rpx;opacity:.7;}
.pending-card{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:20rpx;background:linear-gradient(115deg,#edf4e9,#f8fbf5);border:1rpx solid #e0eadc;border-radius:24rpx;padding:22rpx 24rpx;margin-bottom:14rpx;box-shadow:0 6rpx 20rpx #184f4005;}.section-heading{display:flex;justify-content:space-between;align-items:center;margin:0 2rpx 16rpx;}.section-heading>text{font-size:27rpx;font-weight:650;}.section-heading button{display:flex;align-items:center;gap:6rpx;font-size:21rpx;color:#77867b;}.pending-content{display:flex;align-items:center;gap:18rpx;width:100%;text-align:left;color:#193e32;}.pending-card>.section-heading{grid-column:1/-1;}.pending-content>.prototype-icon{display:none;}.pending-icon{padding:14rpx;border-radius:18rpx;background:#fff6df;border:1rpx solid #f4e8c4;}.pending-number{font-size:43rpx;font-weight:700;line-height:1.1;display:block;}.pending-label{display:block;font-size:23rpx;margin-top:6rpx;}.pending-error{font-size:21rpx;margin-left:auto;color:#a78049;}
.shortcut-card{display:flex;justify-content:space-around;gap:10rpx;margin:26rpx 0 28rpx;}.shortcut-card button{flex:1;min-width:0;display:flex;align-items:center;flex-direction:column;gap:12rpx;color:#294a3a;font-size:23rpx;}.shortcut-icon{display:flex;align-items:center;justify-content:center;width:78rpx;height:78rpx;border-radius:50%;background:white;border:1rpx solid #e7eee5;box-shadow:0 6rpx 14rpx #193e3206;}
.recent-group{border-radius:24rpx;background:#ffffff;border:1rpx solid #e8eee5;overflow:hidden;}.recent-row,.service-row{display:flex;align-items:center;gap:18rpx;width:100%;padding:22rpx;box-sizing:border-box;text-align:left;color:#244633;}.recent-row+.recent-row,.service-row+.service-row{border-top:1rpx solid #eff2eb;}.row-icon{display:flex;align-items:center;justify-content:center;width:66rpx;height:66rpx;flex-shrink:0;border-radius:18rpx;background:#eaf3e8;}.recent-body{flex:1;min-width:0;}.recent-title{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:27rpx;font-weight:600;}.recent-meta{display:block;font-size:21rpx;color:#8a978a;margin-top:8rpx;}.status-pill{border-radius:10rpx;padding:5rpx 10rpx;font-size:20rpx;color:#967832;background:#fff6df;white-space:nowrap;}.status-pill.published{background:#e8f4e8;color:#397653;}.empty-state{padding:34rpx 24rpx;text-align:center;color:#879486;font-size:24rpx;line-height:1.6;}.empty-icon{display:flex;justify-content:center;margin-bottom:14rpx;}.empty-state>text{display:block;}.empty-state button{margin:18rpx auto 0;display:inline-flex;padding:12rpx 24rpx;border:1rpx solid #d9e6d5;border-radius:30rpx;color:#1d5c46;font-size:24rpx;background:#f3f8ef;}.services{margin-top:28rpx;}.service-row>text{flex:1;font-size:27rpx;}
</style>

