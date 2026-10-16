<template><image class="prototype-icon" :src="source" mode="aspectFit" :style="{width:`${size}rpx`,height:`${size}rpx`}" /></template>
<script setup lang="ts">
import {computed} from 'vue';
const props=withDefaults(defineProps<{name:string;size?:number;tone?:'green'|'muted'|'white'|'amber'}>(),{size:40,tone:'green'});
const names=['home','task','growth','star','settings','user','book','gift','check','clock','calendar','people','school','leaf','plus','arrow','chart','shield','mail','phone','lock'];
const source=computed(()=>`/static/prototype-icons/${names.includes(props.name)?props.name:'leaf'}-${props.tone}.svg`);
</script>
<style scoped>.prototype-icon{display:block;flex-shrink:0;vertical-align:middle;}</style>
