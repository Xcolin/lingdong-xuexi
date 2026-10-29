<template>
  <view class="shop-header" :class="{'growth-header':showBalance===false}" :style="{paddingTop: `${statusHeight}px`}">
    <view class="shop-header-row" :style="{minHeight: `${rowHeight}px`, paddingRight: `${capsuleSpace}px`}">
      <button class="shop-menu" :aria-label="showBalance===false?'返回':'奖励功能'" @tap="showBalance===false?goBack():emit('menu')"><text v-if="showBalance===false">‹</text><PrototypeIcon v-else name="gift" tone="white" :size="26"/></button>
      <text class="shop-title">{{title || '奖品商城'}}</text>
      <view v-if="showBalance!==false" class="shop-balance" aria-label="可用积分"><text class="coin">★</text><text>{{points ?? '—'}}</text></view>
      <button v-else class="record-button" @tap="emit('records')">兑换记录</button>
    </view>
  </view>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
defineProps<{points?: number | null; title?: string; showBalance?: boolean}>();
const emit=defineEmits<{(event:'menu'):void;(event:'records'):void}>();
function goBack(){uni.navigateBack({delta:1,fail:()=>uni.reLaunch({url:'/pages/student-dashboard/student-dashboard'})});}
const statusHeight=ref(0),rowHeight=ref(44),capsuleSpace=ref(16);
// #ifdef MP-WEIXIN
try {
  const system=uni.getSystemInfoSync();
  statusHeight.value=system.statusBarHeight||0;
  const capsule=uni.getMenuButtonBoundingClientRect();
  rowHeight.value=Math.max(44,capsule.height+(capsule.top-statusHeight.value)*2);
  capsuleSpace.value=Math.max(16,system.windowWidth-capsule.left+12);
} catch { /* Menu metrics can be unavailable in preview. */ }
// #endif
</script>
<style scoped>
.shop-header{margin:-28rpx -28rpx 24rpx;background:#17684f;color:#fff;box-sizing:border-box;}
.shop-header-row{display:flex;align-items:center;justify-content:space-between;gap:20rpx;padding-left:28rpx;box-sizing:border-box;}
.shop-title{flex:1;text-align:center;font-size:34rpx;font-weight:650;white-space:nowrap;}
.shop-balance{display:flex;align-items:center;gap:8rpx;font-size:28rpx;font-weight:650;white-space:nowrap;}
.coin{color:#ffd16b;font-size:30rpx;}
.shop-menu{display:flex;align-items:center;justify-content:center;margin:0;padding:0;background:transparent;color:inherit;line-height:44px;min-width:32rpx;font-size:32rpx;}.shop-menu::after,.record-button::after{border:0;}
.growth-header{background:#fff;color:#193e32;}.growth-header .shop-menu{font-size:48rpx;}
.record-button{margin:0;padding:0;background:transparent;font-size:23rpx;color:#193e32;line-height:44px;white-space:nowrap;}
</style>
