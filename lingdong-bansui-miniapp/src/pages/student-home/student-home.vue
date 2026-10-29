<template>
  <view class="ld-page has-tabbar prototype-workspace"><NativeStatusSpacer/>
    <view class="profile-hero"><image class="child-avatar" src="/static/pixel/default-child-avatar.png" mode="aspectFill"/><view class="profile-copy"><text class="ld-heading-title">{{ displayName || session?.studentAccount || '小主人' }}</text><text class="growth-badge">⭐ 成长小主人</text><text class="ld-heading-sub">每天进步一点点！</text></view><button class="profile-settings" aria-label="账号设置" @tap="openSettings">›</button></view>
    <view class="ld-group">
      <button v-if="dailyGrowthReviewEnabled" class="ld-cell" @tap="openPage('/pages/growth-reviews/growth-reviews')"><view class="ld-cell-icon teal"><PrototypeIcon name="growth" :size="23"/></view><view class="ld-cell-body"><text class="ld-cell-title">我的成长</text></view><text class="ld-cell-arrow">›</text></button>
      <button v-if="growthPointEnabled" class="ld-cell" @tap="openPage('/pages/growth-points/growth-points')"><view class="ld-cell-icon"><PrototypeIcon name="star" tone="amber" :size="46"/></view><view class="ld-cell-body"><text class="ld-cell-title">积分记录</text></view><text class="ld-cell-arrow">›</text></button>
      <button v-if="rewardExchangeEnabled" class="ld-cell" @tap="openPage('/pages/rewards/rewards?tab=exchanges')"><view class="ld-cell-icon"><PrototypeIcon name="gift" tone="amber" :size="46"/></view><view class="ld-cell-body"><text class="ld-cell-title">兑换记录</text></view><text class="ld-cell-arrow">›</text></button>

    </view>
    <view class="ld-group security-group">
      <button v-if="accountSecurityEnabled" class="ld-cell" @tap="openAccountSecurity()">
        <view class="ld-cell-icon soft"><PrototypeIcon name="shield" :size="23" /></view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">账号与安全</text>
          <text class="ld-cell-sub">查看设备和登录提醒</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
    </view>
  <AppTabBar :items="STUDENT_TABS" :active="3" />
  </view>
</template>

<script setup lang="ts">
import NativeStatusSpacer from "@/components/NativeStatusSpacer.vue";
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref } from 'vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { STUDENT_TABS } from '@/config/tabbar';
import { onShow, onHide } from '@dcloudio/uni-app';
import { request } from '@/api/http';
import { getMiniappCapabilities } from '@/api/capability';
import { getStudentSession, type StoredStudentSession } from '@/session/student-session';

const session = ref<StoredStudentSession | null>(null);
const displayName = ref('');
let generation = 0;
onHide(() => { generation++; });
const learningTaskEnabled = ref(false);
const growthPointEnabled = ref(false);
const rewardExchangeEnabled = ref(false);
const dailyGrowthReviewEnabled = ref(false);
const accountSecurityEnabled = ref(false);

onShow(async () => {
  const currentGeneration = ++generation;
  displayName.value = '';
  session.value = getStudentSession();
  if (!session.value) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  const token = session.value.accessToken;
  try {
    const [capabilities, me] = await Promise.all([getMiniappCapabilities(), request<{displayName?:string}>('/auth/me', {header:{Authorization:`Bearer ${token}`}})]);
    if (currentGeneration !== generation || getStudentSession()?.accessToken !== token) return;
    displayName.value = me.displayName || '';
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
    growthPointEnabled.value = capabilities.growthPointQueryEnabled;
    rewardExchangeEnabled.value = capabilities.rewardExchangeEnabled;
    dailyGrowthReviewEnabled.value = capabilities.dailyGrowthReviewEnabled;
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
  } catch {
    if (currentGeneration !== generation || getStudentSession()?.accessToken !== token) return;
    learningTaskEnabled.value = false;
    growthPointEnabled.value = false;
    rewardExchangeEnabled.value = false;
    dailyGrowthReviewEnabled.value = false;
    accountSecurityEnabled.value = false;
  }
});

function openPage(url: string): void { uni.navigateTo({ url }); }
function openAccountSecurity(): void {
  uni.navigateTo({ url: '/pages/account-security/account-security?identity=student' });
}

function openSettings(): void { uni.navigateTo({url:'/pages/account-settings/account-settings?identity=student'}); }
</script>

<style lang="scss" scoped>@import "@/styles/workspace-prototype.scss";
.prototype-workspace{background:#f5f8f3;color:#162c22;padding:28rpx 30rpx 150rpx;}.ld-topbar{justify-content:flex-end;}.ld-topbar-brand{display:none;}.profile-hero{display:flex;align-items:center;gap:30rpx;padding:38rpx 8rpx 64rpx;}.profile-hero>svg{border-radius:50%;background:#cbe8d5;padding:28rpx;}.profile-hero .ld-heading-title{font-size:40rpx;}.profile-hero .ld-heading-sub{font-size:24rpx;margin-top:20rpx;}.ld-cell-sub{display:none;}.ld-group{border-radius:28rpx;overflow:hidden;background:#fff;}.ld-cell{min-height:120rpx;padding:24rpx 28rpx;}.ld-cell-title{font-size:30rpx;}.ld-cell-icon{background:transparent;}.ld-cell-arrow{color:#29372e;}
</style>


<style lang="scss" scoped>
.profile-hero{padding:42rpx 6rpx 58rpx;gap:24rpx;}.child-avatar{width:140rpx;height:140rpx;border-radius:50%;background:#c8e8d5;flex-shrink:0;}.profile-copy{flex:1;min-width:0;}.ld-heading-title{display:block;white-space:normal;overflow-wrap:anywhere;word-break:break-all;line-height:1.35;}.growth-badge{display:inline-block;background:#127548;color:white;border-radius:24rpx;font-size:22rpx;padding:5rpx 14rpx;margin-top:9rpx;}.profile-hero .ld-heading-sub{margin-top:12rpx;color:#667169;font-size:21rpx;}.profile-settings{flex-shrink:0;margin:0;background:transparent;padding:0 4rpx;font-size:36rpx;color:#29372e;}.profile-settings::after{border:0;}.ld-cell{min-height:80rpx;padding:14rpx 22rpx;}.ld-cell+.ld-cell{border-top:1rpx solid #eef2ed;}.ld-cell-title{font-size:26rpx;}.security-group{margin-top:12rpx;}
</style>

<style scoped>.prototype-workspace{min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box}</style>
