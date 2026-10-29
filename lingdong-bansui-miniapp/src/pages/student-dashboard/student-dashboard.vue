<template><view class="child-home ld-page has-tabbar"><NativeStatusSpacer/><!-- #ifdef MP-WEIXIN --><view class="child-native-nav"/><!-- #endif --><view class="layout-welcome"><view class="layout-avatar"><image class="default-avatar" src="/static/pixel/default-child-avatar.png" mode="aspectFill"/></view><view class="layout-greeting"><text class="layout-name">{{name||'小主人'}}，今天也要加油！</text><text class="layout-caption">小小的坚持，会让你变得更棒！</text></view><button v-if="visibleQuickEntries.length" class="layout-more" :aria-expanded="showQuickEntries" @tap="toggleQuickEntries">{{showQuickEntries?'收起':'更多'}}</button></view>
 <StudentTodayTasks><template #summary><view v-if="showQuickEntries" class="quick-entries"><view v-for="entry in visibleQuickEntries" :key="entry.label" class="quick-entry" @tap="go(entry)"><view class="quick-icon"><PrototypeIcon :name="entry.icon" tone="amber" :size="34"/></view><text class="quick-label">{{entry.label}}</text></view></view><view class="child-summary"><button v-if="pointsEnabled" class="point-card" @tap="open('/pages/growth-points/growth-points')"><view class="summary-icon"><PrototypeIcon name="star" tone="amber" :size="42"/></view><view><text class="summary-number">{{points===null?'—':points}}<text> 积分</text></text><text class="summary-caption">{{pointsError||'我的成长积分'}}</text></view></button><button v-if="rewardEnabled" class="reward-card" @tap="open('/pages/rewards/rewards?layout=list')"><view class="summary-icon gift"><PrototypeIcon name="gift" tone="amber" :size="44"/></view><view><text class="summary-title">成长奖励</text><text class="summary-caption">让努力有惊喜</text></view><PrototypeIcon name="arrow" :size="22"/></button></view></template></StudentTodayTasks>
 <AppTabBar :items="STUDENT_TABS" :active="0"/></view></template>
<script setup lang="ts">
import NativeStatusSpacer from '@/components/NativeStatusSpacer.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {ref,computed} from 'vue';
import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import StudentTodayTasks from '@/components/StudentTodayTasks.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import {STUDENT_TABS} from '@/config/tabbar';
import {getStudentSession} from '@/session/student-session';
import {getMiniappCapabilities} from '@/api/capability';
import {getMyGrowthPointAccount} from '@/api/growth-point';
import {request} from '@/api/http';
import {dashboardEntryAllowed,dashboardSessionCurrent,dashboardNavigationAllowed} from './dashboard-access';
import {canReadReviewPeriod} from '@/pages/growth-reviews/review-period';
const showQuickEntries=ref(false);
const name=ref(''),points=ref<number|null>(null),pointsError=ref(''),pointsEnabled=ref(false),rewardEnabled=ref(false),taskEnabled=ref(false),reviewEnabled=ref(false);
interface QuickEntry{label:string;icon:string;url:string;allowed:()=>boolean}
const quickEntries:QuickEntry[]=[
 {label:'任务列表',icon:'task',url:'/pages/task-list/task-list',allowed:()=>taskEnabled.value},
 {label:'我的打卡',icon:'check',url:'/pages/growth-points/growth-points',allowed:()=>pointsEnabled.value},
 {label:'成长日历',icon:'calendar',url:'/pages/growth-reviews/growth-reviews',allowed:()=>reviewEnabled.value},
 {label:'我的徽章',icon:'star',url:'/pages/rewards/rewards',allowed:()=>rewardEnabled.value}
];
const visibleQuickEntries=computed(()=>quickEntries.filter(entry=>entry.allowed()));
let revision=0,authorizedToken='';
function clear(){showQuickEntries.value=false;revision++;authorizedToken='';name.value='';points.value=null;pointsError.value='';pointsEnabled.value=rewardEnabled.value=taskEnabled.value=reviewEnabled.value=false;}
onHide(clear);onUnload(clear);
onShow(async()=>{
 clear();const current=revision,session=getStudentSession();
 if(!session){await uni.reLaunch({url:'/pages/unified-login/unified-login'});return;}
 const valid=()=>dashboardSessionCurrent(current,revision,session.accessToken,getStudentSession()?.accessToken);
 name.value=session.studentAccount;
 try{
  const [c,identity]=await Promise.all([getMiniappCapabilities(),request<{clientType:string;roleCodes:string[];permissionCodes:string[]}>('/auth/me',{header:{Authorization:`Bearer ${session.accessToken}`}})]);
  if(!valid())return;
  authorizedToken=session.accessToken;
  pointsEnabled.value=dashboardEntryAllowed(c.growthPointQueryEnabled,identity.permissionCodes,'GROWTH_POINT_READ_SELF');
  rewardEnabled.value=dashboardEntryAllowed(c.rewardExchangeEnabled,identity.permissionCodes,'REWARD_EXCHANGE_SELF');
  taskEnabled.value=dashboardEntryAllowed(c.learningTaskManagementEnabled,identity.permissionCodes,'TASK_ASSIGNMENT_READ_SELF');
  reviewEnabled.value=canReadReviewPeriod(identity,c.dailyGrowthReviewEnabled);
  if(pointsEnabled.value){try{const a=await getMyGrowthPointAccount();if(!valid())return;points.value=a.availablePoints;name.value=a.studentName;}catch{if(valid())pointsError.value='积分暂未加载，查看记录';}}
 }catch{if(valid())pointsError.value='暂未获取功能状态';}
});
function open(url:string){
 const enabled=url==='/pages/growth-points/growth-points'?pointsEnabled.value:url.split('?')[0]==='/pages/rewards/rewards'?rewardEnabled.value:false;
 if(!dashboardNavigationAllowed(enabled,authorizedToken,getStudentSession()?.accessToken)){clear();return;}
 uni.navigateTo({url});
}
function toggleQuickEntries(){
 if(!dashboardNavigationAllowed(visibleQuickEntries.value.length>0,authorizedToken,getStudentSession()?.accessToken)){clear();return;}
 showQuickEntries.value=!showQuickEntries.value;
}
function go(entry:QuickEntry){
 if(!dashboardNavigationAllowed(entry.allowed(),authorizedToken,getStudentSession()?.accessToken)){clear();return;}
 uni.navigateTo({url:entry.url});
}
</script>
<style lang="scss" scoped>
@import "@/styles/pixel-tokens.scss";
.child-home{background:$pixel-background;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;padding:24rpx 33rpx calc(140rpx + env(safe-area-inset-bottom));color:$pixel-text}
.layout-welcome{display:flex;align-items:center;gap:20rpx;margin:0 0 24rpx}.layout-avatar{display:flex;align-items:center;justify-content:center;background:#e4f0e6;border-radius:50%;width:127rpx;height:127rpx;flex-shrink:0}.default-avatar{width:100%;height:100%;border-radius:50%}.layout-greeting{flex:1;min-width:0}.layout-name{display:block;font-size:42rpx;font-weight:700;line-height:1.5;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.layout-caption{display:block;font-size:22rpx;color:$pixel-muted;line-height:1.6;margin-top:6rpx}
.child-native-nav{height:44px;}
.layout-more{flex-shrink:0;align-self:flex-start;margin:12rpx 0 0;padding:8rpx 12rpx;font-size:22rpx;line-height:1.5;color:#47715d;background:#eaf1e9;border-radius:20rpx}.layout-more::after{border:0}
.child-summary{display:flex;gap:12rpx;margin:18rpx 0 22rpx}.child-summary button{flex:1;min-width:0;display:flex;align-items:center;gap:12rpx;margin:0;padding:14rpx 16rpx;background:#fff;border-radius:18rpx;text-align:left;color:#214b37;line-height:1.5;border:1rpx solid #e8ecdf}.child-summary button::after{border:0}.summary-number{display:block;font-size:32rpx;font-weight:700}.summary-number>text{display:none}.summary-caption{display:block;color:#899980;font-size:21rpx;margin-top:5rpx}.summary-title{font-size:25rpx;font-weight:600;white-space:nowrap}.summary-icon{display:flex;align-items:center;justify-content:center;background:#fff3db;border-radius:14rpx;padding:9rpx}.summary-icon.gift{background:#fff0db}
.quick-entries{display:flex;gap:12rpx;margin:18rpx 0 4rpx}.quick-entry{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;gap:10rpx;background:#fff;border:1rpx solid #e8ecdf;border-radius:18rpx;padding:20rpx 6rpx;color:#214b37}.quick-icon{display:flex;align-items:center;justify-content:center;background:#fdf3df;border-radius:14rpx;padding:10rpx}.quick-label{font-size:21rpx;font-weight:600}
</style>
