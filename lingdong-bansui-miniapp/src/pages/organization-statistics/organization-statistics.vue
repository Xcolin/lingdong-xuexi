<template><view class="stats-page"><NativeStatusSpacer/>
  <view class="header"><button @tap="back">‹</button><text>数据概览</text></view>
  <text v-if="error" class="error">{{ error }}</text>
  <view v-if="loading" class="loading">正在加载</view>
  <template v-else-if="allowed">
    <view class="summary-card">
      <view class="summary-icon"><PrototypeIcon name="chart" tone="white" :size="44"/></view>
      <view class="summary-copy">
        <text class="summary-title">教学组织与成员统计</text>
        <text class="summary-caption">当前管理范围内的组织与成员计数</text>
      </view>
    </view>
    <view class="stat-card" v-for="row in rows" :key="row.label">
      <view class="stat-icon"><PrototypeIcon :name="row.icon" :size="40"/></view>
      <view class="stat-copy">
        <text class="stat-label">{{ row.label }}</text>
        <text class="stat-caption">{{ row.caption }}</text>
      </view>
      <text class="stat-value">{{ row.value }}</text>
    </view>
    <text v-if="!hasAnyValue" class="empty">暂无可显示的统计数据</text>
  </template>
</view></template>

<script setup lang="ts">
import NativeStatusSpacer from '@/components/NativeStatusSpacer.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref, computed } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import {
  getOrganizationWorkbenchStatistics,
  type OrganizationWorkbenchStatistics
} from '@/api/organization-workbench';
import { clearOrganizationSession, getOrganizationSession } from '@/session/organization-session';
import { ApiError } from '@/api/http';

const statistics = ref<OrganizationWorkbenchStatistics | null>(null);
const loading = ref(true);
const allowed = ref(false);
const error = ref('');
let revision = 0;

function formatCount(value: number | null | undefined): string {
  return value == null ? '—' : value.toLocaleString('en-US');
}

const rows = computed(() => [
  { label: '校区', caption: '管理范围内的校区数量', icon: 'school', value: formatCount(statistics.value?.campusCount) },
  { label: '年级', caption: '管理范围内的年级数量', icon: 'book', value: formatCount(statistics.value?.gradeCount) },
  { label: '班级', caption: '管理范围内的班级数量', icon: 'people', value: formatCount(statistics.value?.classCount) },
  { label: '成长伙伴', caption: '范围内启用中的教师账号', icon: 'user', value: formatCount(statistics.value?.teacherCount) },
  { label: '成长小主人', caption: '范围内有效入学的学生数', icon: 'star', value: formatCount(statistics.value?.studentCount) }
]);

const hasAnyValue = computed(() => rows.value.some(row => row.value !== '—'));

function clear() {
  revision++;
  statistics.value = null;
  allowed.value = false;
  error.value = '';
  loading.value = true;
}

onShow(async () => {
  const n = ++revision;
  const session = getOrganizationSession();
  if (!session) {
    clearOrganizationSession();
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const data = await getOrganizationWorkbenchStatistics(session.accessToken);
    if (n !== revision || getOrganizationSession()?.accessToken !== session.accessToken) return;
    statistics.value = data;
    allowed.value = true;
  } catch (e) {
    if (n !== revision || getOrganizationSession()?.accessToken !== session.accessToken) return;
    if (e instanceof ApiError && (e.statusCode === 401 || e.statusCode === 403)) {
      clearOrganizationSession();
      await uni.reLaunch({ url: '/pages/index/index' });
      return;
    }
    error.value = e instanceof Error ? e.message : '加载失败，请稍后重试';
  } finally {
    if (n === revision) loading.value = false;
  }
});
onHide(clear);
onUnload(clear);

function back() {
  uni.navigateBack();
}
</script>

<style scoped>
.stats-page{min-height:100vh;box-sizing:border-box;background:#f5f8f4;color:#182d26;padding:0 24rpx 48rpx}
.header{height:88rpx;margin:0 -24rpx 12rpx;padding:0 112px;position:relative;display:flex;align-items:center;justify-content:center;background:#fff;font-size:30rpx;font-weight:650}
.header>button{position:absolute;left:14rpx;top:0;margin:0;padding:0;width:56rpx;height:88rpx;line-height:80rpx;background:transparent;font-size:50rpx}
.summary-card{display:flex;align-items:center;gap:18rpx;background:linear-gradient(120deg,#21664f,#0b4b3d);border-radius:24rpx;padding:28rpx 26rpx;margin-bottom:20rpx;color:#fff}
.summary-title{display:block;font-size:32rpx;font-weight:700}
.summary-caption{display:block;font-size:23rpx;opacity:.85;margin-top:8rpx}
.summary-icon{width:76rpx;height:76rpx;border-radius:16rpx;background:#ffffff25;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.stat-card{display:flex;align-items:center;gap:18rpx;background:#fff;border:1rpx solid #e5ece5;border-radius:24rpx;padding:26rpx 24rpx;margin-bottom:16rpx}
.stat-icon{width:64rpx;height:64rpx;border-radius:14rpx;background:#e7f1e4;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.stat-copy{flex:1;min-width:0}
.stat-label{display:block;font-size:29rpx;font-weight:650}
.stat-caption{display:block;font-size:23rpx;color:#7b837d;margin-top:6rpx}
.stat-value{font-size:40rpx;font-weight:750;color:#138655}
.error{display:block;color:#b05242;margin:24rpx 4rpx}
.loading{display:block;padding:32rpx 4rpx;color:#7b837d}
.empty{display:block;padding:28rpx 4rpx;color:#7d867e;text-align:center}
</style>
