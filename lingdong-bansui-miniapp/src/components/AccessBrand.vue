<template><view class="access-brand" :class="{'access-brand-compact':compact}"><view class="access-brand-leaf"/><text class="access-brand-name">灵动伴随</text></view></template>
<script setup lang="ts">defineProps<{compact?:boolean}>();</script>
<style scoped>
.access-brand{display:flex;align-items:center;justify-content:center;gap:20rpx;box-sizing:border-box;padding:48rpx 0 58rpx;margin:0 -32rpx 46rpx;border-bottom:1rpx solid #aabeb0;border-radius:0 0 50% 20%;background:linear-gradient(160deg,#f5faf7,#f7f7f1);color:#125747;}
.access-brand-name{font-size:36rpx;font-weight:700;line-height:1.5;}
.access-brand-leaf{flex-shrink:0;width:38rpx;height:52rpx;border-radius:85% 0 85% 0;transform:rotate(15deg);background:linear-gradient(135deg,#1a6656 0%,#105145 46%,#f5faf7 47%,#f5faf7 53%,#105145 54%,#236b58 100%);}
.access-brand-compact{padding:36rpx 0 40rpx;margin:0 -28rpx;}
.access-brand-compact .access-brand-name{font-size:38rpx;}
@media(max-height:650px){.access-brand-compact{padding-top:24rpx;padding-bottom:28rpx;}}
</style>
