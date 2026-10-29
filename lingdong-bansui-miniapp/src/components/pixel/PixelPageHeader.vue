<template>
  <view :class="['pixel-header',{'pixel-header-start':align==='start','pixel-header-page':tone==='page','pixel-header-flush':flush}]" :style="{ paddingTop: `${statusHeight}px` }">
    <view class="pixel-header-row" :style="{ height: `${rowHeight}px`, paddingRight: `${capsuleSpace}px` }">
      <button v-if="back" class="pixel-back" aria-label="返回" @tap="goBack">‹</button>
      <text class="pixel-title">{{ title }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue';
const props = withDefaults(defineProps<{ title: string; back?: boolean; interceptBack?: boolean; fallback?: string; align?: 'center'|'start'; tone?: 'white'|'page'; flush?:boolean }>(), { back: false, interceptBack: false, fallback: '/pages/index/index',align:'center',tone:'white',flush:false });
const emit = defineEmits<{ (event: 'back'): void }>();
const statusHeight = ref(0), rowHeight = ref(44), capsuleSpace = ref(16);
// #ifdef MP-WEIXIN
try {
  const system = uni.getSystemInfoSync();
  statusHeight.value = system.statusBarHeight || 0;
  const capsule = uni.getMenuButtonBoundingClientRect();
  rowHeight.value = Math.max(44, capsule.height + (capsule.top - statusHeight.value) * 2);
  capsuleSpace.value = Math.max(16, system.windowWidth - capsule.left + 12);
} catch { /* Default header remains usable when menu metrics are unavailable. */ }
// #endif
function goBack() {
  if (props.interceptBack) { emit('back'); return; }
  uni.navigateBack({ delta: 1, fail: () => { uni.reLaunch({ url: props.fallback }); } });
}
</script>
<style scoped>
.pixel-header{background:#fff;color:#172b25;margin:0 -28rpx 24rpx;position:relative;z-index:30;box-sizing:border-box}.pixel-header-row{display:flex;align-items:center;justify-content:center;position:relative;box-sizing:border-box;padding-left:16px}.pixel-title{position:absolute;left:60px;right:60px;text-align:center;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:32rpx;font-weight:650;pointer-events:none}.pixel-back{position:absolute;left:10px;top:0;width:44px;height:100%;padding:0;margin:0;display:flex;align-items:center;justify-content:center;font-size:54rpx;line-height:1;background:transparent;color:#172b25;border-radius:0}.pixel-back::after{border:0}
.pixel-header-flush{margin:0;flex-shrink:0;}
</style>
<style scoped>
.pixel-header-start .pixel-header-row{justify-content:flex-start;padding-left:28rpx;}.pixel-header-start .pixel-title{position:static;flex:1;text-align:left;font-size:40rpx;line-height:1.35;}.pixel-header-start .pixel-back+.pixel-title{padding-left:48px;}.pixel-header-page{background:#f5f8f4;color:#173c2f;}
</style>
