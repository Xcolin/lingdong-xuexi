<template><view class="child-home ld-page has-tabbar"><view class="layout-welcome"><view class="layout-avatar"><image class="default-avatar" src="/static/pixel/default-child-avatar.png" mode="aspectFill"/></view><view class="layout-greeting"><text class="layout-name">{{name||'小主人'}}，今天也要加油！</text><text class="layout-caption">小小的坚持，会让你变得更棒！</text></view></view>
 <StudentTodayTasks><template #summary><view class="child-summary"><button v-if="pointsEnabled" class="point-card" @tap="open('/pages/growth-points/growth-points')"><view class="summary-icon"><PrototypeIcon name="star" tone="amber" :size="42"/></view><view><text class="summary-number">{{points===null?'—':points}}<text> 积分</text></text><text class="summary-caption">{{pointsError||'我的成长积分'}}</text></view></button><button v-if="rewardEnabled" class="reward-card" @tap="open('/pages/rewards/rewards')"><view class="summary-icon gift"><PrototypeIcon name="gift" tone="amber" :size="44"/></view><view><text class="summary-title">成长奖励</text><text class="summary-caption">让努力有惊喜</text></view><PrototypeIcon name="arrow" :size="22"/></button></view></template></StudentTodayTasks>
 <AppTabBar :items="STUDENT_TABS" :active="0"/></view></template>
<script setup lang="ts">
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {ref} from 'vue';
import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import StudentTodayTasks from '@/components/StudentTodayTasks.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import {STUDENT_TABS} from '@/config/tabbar';
import {getStudentSession} from '@/session/student-session';
import {getMiniappCapabilities} from '@/api/capability';
import {getMyGrowthPointAccount} from '@/api/growth-point';
import {request} from '@/api/http';
import {dashboardEntryAllowed,dashboardSessionCurrent,dashboardNavigationAllowed} from './dashboard-access';
const name=ref(''),points=ref<number|null>(null),pointsError=ref(''),pointsEnabled=ref(false),rewardEnabled=ref(false);
let revision=0,authorizedToken='';
function clear(){revision++;authorizedToken='';name.value='';points.value=null;pointsError.value='';pointsEnabled.value=rewardEnabled.value=false;}
onHide(clear);onUnload(clear);
onShow(async()=>{
 clear();const current=revision,session=getStudentSession();
 if(!session){await uni.reLaunch({url:'/pages/unified-login/unified-login'});return;}
 const valid=()=>dashboardSessionCurrent(current,revision,session.accessToken,getStudentSession()?.accessToken);
 name.value=session.studentAccount;
 try{
  const [c,identity]=await Promise.all([getMiniappCapabilities(),request<{permissionCodes:string[]}>('/auth/me',{header:{Authorization:`Bearer ${session.accessToken}`}})]);
  if(!valid())return;
  authorizedToken=session.accessToken;
  pointsEnabled.value=dashboardEntryAllowed(c.growthPointQueryEnabled,identity.permissionCodes,'GROWTH_POINT_READ_SELF');
  rewardEnabled.value=dashboardEntryAllowed(c.rewardExchangeEnabled,identity.permissionCodes,'REWARD_EXCHANGE_SELF');
  if(pointsEnabled.value){try{const a=await getMyGrowthPointAccount();if(!valid())return;points.value=a.availablePoints;name.value=a.studentName;}catch{if(valid())pointsError.value='积分暂未加载，查看记录';}}
 }catch{if(valid())pointsError.value='暂未获取功能状态';}
});
function open(url:string){
 const enabled=url==='/pages/growth-points/growth-points'?pointsEnabled.value:url==='/pages/rewards/rewards'?rewardEnabled.value:false;
 if(!dashboardNavigationAllowed(enabled,authorizedToken,getStudentSession()?.accessToken)){clear();return;}
 uni.navigateTo({url});
}
</script>
<style lang="scss" scoped>
@import "@/styles/pixel-tokens.scss";
.child-home{background:$pixel-background;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;padding:24rpx 33rpx calc(140rpx + env(safe-area-inset-bottom));color:$pixel-text}
.layout-welcome{display:flex;align-items:center;gap:20rpx;margin:0 0 24rpx}.layout-avatar{display:flex;align-items:center;justify-content:center;background:#e4f0e6;border-radius:50%;width:127rpx;height:127rpx;flex-shrink:0}.default-avatar{width:100%;height:100%;border-radius:50%}.layout-greeting{flex:1;min-width:0}.layout-name{display:block;font-size:42rpx;font-weight:700;line-height:1.5;overflow-wrap:anywhere}.layout-caption{display:block;font-size:22rpx;color:$pixel-muted;line-height:1.6;margin-top:6rpx}
.child-summary{display:flex;gap:12rpx;margin:18rpx 0 22rpx}.child-summary button{flex:1;min-width:0;display:flex;align-items:center;gap:12rpx;margin:0;padding:14rpx 16rpx;background:#fff;border-radius:18rpx;text-align:left;color:#214b37;line-height:1.5;border:1rpx solid #e8ecdf}.child-summary button::after{border:0}.summary-number{display:block;font-size:32rpx;font-weight:700}.summary-number>text{display:none}.summary-caption{display:block;color:#899980;font-size:21rpx;margin-top:5rpx}.summary-title{font-size:25rpx;font-weight:600;white-space:nowrap}.summary-icon{display:flex;align-items:center;justify-content:center;background:#fff3db;border-radius:14rpx;padding:9rpx}.summary-icon.gift{background:#fff0db}
</style>
