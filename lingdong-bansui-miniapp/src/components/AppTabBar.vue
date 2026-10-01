<template>
  <view class="tabbar">
    <view class="tabbar-inner">
      <view
        v-for="(item, index) in items"
        :key="item.url"
        :class="['tab-item', { active: index === active }]"
        @tap="switchTab(index)"
      >
        <text class="tab-icon">{{ item.icon }}</text>
        <text class="tab-label">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
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

<style lang="scss" scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 90;
  background: rgba(255, 255, 255, 0.97);
  border-top: 2rpx solid $ld-line;
  padding-bottom: env(safe-area-inset-bottom);
  backdrop-filter: blur(10px);
}
.tabbar-inner {
  display: flex;
  height: 108rpx;
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}
.tab-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: $ld-text-muted;
  background: transparent;
}
.tab-label {
  color: $ld-text-muted;
  font-size: $ld-font-mini;
}
.tab-item.active .tab-icon {
  background: $ld-primary-soft;
  color: $ld-primary;
}
.tab-item.active .tab-label {
  color: $ld-primary;
  font-weight: 650;
}
</style>
