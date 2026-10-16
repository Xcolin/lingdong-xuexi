<template>
  <view class="tabbar">
    <view class="tabbar-inner">
      <view
        v-for="(item, index) in items"
        :key="item.url"
        :class="['tab-item', { active: index === active }]"
        @tap="switchTab(index)"
      >
        <view class="tab-icon"><PrototypeIcon :name="item.label==='首页'?'home':item.label==='我的'?'user':item.label==='成长'?'growth':item.label==='考勤'?'calendar':'task'" :tone="index===active?'green':'muted'" :size="38"/></view>
        <text class="tab-label">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import type { TabItem } from '@/config/tabbar';

const props = defineProps<{
  items: TabItem[];
  /** 当前激活页签下标。 */
  active: number;
}>();

/** 页签间用 redirectTo 互切,避免页面栈随切换增长。 */
function switchTab(index: number): void {
  const target = props.items[index];
  if (!target || index === props.active) return;
  uni.redirectTo({ url: target.url });
}
</script>

<style scoped>
.tabbar{position:fixed;left:0;right:0;bottom:0;z-index:90;background:#fff;border-top:1rpx solid #e9eee8;padding-bottom:env(safe-area-inset-bottom);box-sizing:border-box;}.tabbar-inner{display:flex;height:104rpx;}.tab-item{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6rpx;}.tab-icon{display:flex;align-items:center;justify-content:center;width:42rpx;height:42rpx;background:transparent;}.tab-label{font-size:22rpx;line-height:1.25;color:#7b857e;}.tab-item.active .tab-label{color:#145940;font-weight:650;}
</style>
