<template><view v-if="height" :style="{height:height+'px',flexShrink:0}" /></template>
<script setup lang="ts">
// 自绘主页面保留微信状态栏；H5 无额外导航占位。
let height = 0;
// #ifdef MP-WEIXIN
try { height = Math.max(0, uni.getSystemInfoSync().statusBarHeight || 0); } catch { height = 0; }
// #endif
</script>
