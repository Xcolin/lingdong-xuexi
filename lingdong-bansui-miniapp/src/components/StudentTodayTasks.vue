<template>
  <view class="today">

    <view v-if="busy" class="state">正在加载今日任务</view>
    <view v-else-if="error" class="state error">{{ error }}</view>
    <template v-else-if="allowed">
      <view class="progress-card">
        <text class="progress-title">今日任务进度</text>
        <view class="progress-layout"><view class="progress-ring" :style="{ background: `conic-gradient(#23815d ${completionPercent}%, #e4eee2 0)` }"><view class="progress-center"><text>{{ completed }}/{{ total }}</text><text>已完成</text></view></view><view class="progress-counts"><view v-for="task in tasks.slice(0,4)" :key="task.id" class="progress-task" @tap="open(task.id)"><view :class="['progress-dot',{done:task.effectiveStatus==='COMPLETED'}]"><text v-if="task.effectiveStatus==='COMPLETED'">✓</text></view><text class="progress-task-name">{{task.title}}</text></view><text v-if="!tasks.length" class="meta">今日暂无任务</text><text v-if="total>4" class="meta" @tap="viewAll">共 {{total}} 项，查看全部 ›</text></view></view>
      </view>
      <slot name="summary"/><view class="head-row"><text class="heading">今日任务</text><text class="view-all" @tap="viewAll">查看全部 ›</text></view>
      <view class="summary-row">
        <text class="summary-count">今日共 {{ total }} 项任务</text>
        <text v-if="total === 0" class="summary-hint">今日暂无任务</text>
      </view>
      <view v-for="task in tasks.slice(0, 3)" :key="task.id" class="task-row" @tap="open(task.id)">
        <view class="task-icon"><PrototypeIcon name="book" :size="24" /></view>
        <view class="task-main">
          <text class="task-title">{{ task.title }}</text>
          <text class="meta">{{ sourceName(task.sourceType) }} · {{ statusName(task.effectiveStatus) }}</text>
        </view>
        <text class="task-state" :class="{finished:task.effectiveStatus==='COMPLETED'}">{{['PENDING_CLAIM','IN_PROGRESS'].includes(task.effectiveStatus)?'去完成':statusName(task.effectiveStatus)}}</text>
      </view>
      
    </template>
    <view v-else class="state">今日任务暂不可用</view><slot v-if="!allowed&&!busy" name="summary"/>
  </view>
</template>
<script setup lang="ts">
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { request } from '@/api/http';
import { getMiniappCapabilities } from '@/api/capability';
import { listStudentTaskAssignments, type StudentTaskAssignment } from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';
import { studentTodayTaskUrl } from '@/pages/task-detail/task-detail-presentation';
const tasks = ref<StudentTaskAssignment[]>([]), total = ref(0), busy = ref(false), allowed = ref(false), error = ref('');
const completed = computed(() => tasks.value.filter(t => t.effectiveStatus === 'COMPLETED').length);
const underway = computed(() => tasks.value.filter(t => ['IN_PROGRESS', 'NEEDS_IMPROVEMENT'].includes(t.effectiveStatus)).length);
const pending = computed(() => tasks.value.filter(t => t.effectiveStatus === 'PENDING_CLAIM').length);
const other = computed(() => tasks.value.length - completed.value - underway.value - pending.value);
const completionPercent = computed(() => total.value ? completed.value / total.value * 100 : 0);
let revision = 0;
function clear() { tasks.value = []; total.value = 0; allowed.value = false; }
async function load() {
  const current = ++revision, token = getStudentSession()?.accessToken;
  clear(); error.value = ''; busy.value = true;
  const valid = () => current === revision && getStudentSession()?.accessToken === token;
  try {
    if (!token) return;
    const [capability, user] = await Promise.all([getMiniappCapabilities(), request<{ clientType: string; roleCodes: string[]; permissionCodes: string[] }>('/auth/me', { header: { Authorization: `Bearer ${token}` } })]);
    if (!valid() || !capability.learningTaskManagementEnabled || user.clientType !== 'MINIAPP'
      || !user.roleCodes.includes('STUDENT') || user.roleCodes.includes('SYS_AUDITOR')
      || !user.permissionCodes.includes('TASK_ASSIGNMENT_READ_SELF')) return;
    // 与业务时区保持一致，仅复用日期筛选，不在客户端计算待执行数量。
    const scheduledDate = new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10);
    const result = await listStudentTaskAssignments({ scheduledDate, page: 1, pageSize: 50 });
    if (!valid()) return;
    const all = [...result.items];
    let page = 2;
    while (all.length < result.total) {
      const next = await listStudentTaskAssignments({ scheduledDate, page: page++, pageSize: 50 });
      if (!valid()) return;
      if (!next.items.length) throw new Error('今日任务进度暂未加载完整，请稍后重试');
      all.push(...next.items);
    }
    const distinct = Array.from(new Map(all.map(item => [item.id, item])).values());
    if(distinct.length !== result.total) throw new Error('任务发生变化，请重新进入首页加载进度');
    tasks.value = distinct;
    total.value = tasks.value.length; allowed.value = true;
  } catch (cause) {
    if (valid()) { clear(); error.value = cause instanceof Error ? cause.message : '今日任务查询失败'; }
  } finally { if (current === revision) busy.value = false; }
}
function viewAll(){if(allowed.value&&!busy.value)void uni.navigateTo({url:'/pages/task-list/task-list'});}
function open(id: string) {
  if (allowed.value && !busy.value) void uni.navigateTo({ url: studentTodayTaskUrl(id) });
}
function sourceName(source: string) { return ({ FAMILY: '家庭', TEACHER: '教师', ORGANIZATION: '机构' } as Record<string, string>)[source] || '学习任务'; }
function statusName(status: string) { return ({ PENDING_CLAIM: '待认领', IN_PROGRESS: '进行中', PENDING_REVIEW: '待审核', NEEDS_IMPROVEMENT: '待优化', PAUSED: '暂停中', EXEMPT: '免执行', COMPLETED: '已完成' } as Record<string, string>)[status] || status; }
function hide() { revision++; clear(); busy.value = false; }
onShow(load); onHide(hide); onUnload(hide);
</script>
<style scoped>
.today{padding:0;color:#182c21}.progress-card{background:#fff;border:1rpx solid #edf0e8;border-radius:24rpx;padding:30rpx;}.progress-title{font-size:26rpx;font-weight:600}.progress-layout{display:flex;align-items:center;gap:30rpx;margin-top:20rpx}.progress-ring{width:206rpx;height:206rpx;box-sizing:border-box;border-radius:50%;padding:16rpx;flex-shrink:0}.progress-center{background:white;border-radius:50%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center}.progress-center text:first-child{font-size:40rpx;font-weight:700}.progress-center text:last-child{font-size:22rpx;color:#818b84}.progress-counts{flex:1;min-width:0;display:flex;flex-direction:column;gap:16rpx;border-left:1rpx solid #edf0e8;padding-left:24rpx}.progress-task{display:flex;align-items:center;gap:14rpx}.progress-dot{width:28rpx;height:28rpx;border:2rpx solid #aab6ad;border-radius:50%;flex-shrink:0;display:flex;align-items:center;justify-content:center}.progress-dot.done{background:#58a644;border-color:#58a644;color:white;font-size:20rpx}.progress-task-name{font-size:24rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.summary-row{display:none}.head-row{display:flex;justify-content:space-between;align-items:center;margin:24rpx 0 18rpx}.heading{font-size:28rpx;font-weight:600}.view-all{font-size:22rpx;color:#818b84}.task-row{display:flex;align-items:center;gap:18rpx;background:white;padding:18rpx;border-bottom:1rpx solid #edf0e8}.task-icon{display:flex;align-items:center;justify-content:center;width:76rpx;height:76rpx;background:#fff3dc;border-radius:14rpx;flex-shrink:0}.task-main{min-width:0;flex:1}.task-title{display:block;font-size:30rpx;font-weight:600;overflow-wrap:anywhere}.meta{display:block;font-size:22rpx;color:#818b84;margin-top:6rpx}.task-state{background:#075737;color:white;padding:10rpx 24rpx;border-radius:32rpx;white-space:nowrap;font-size:24rpx}.task-state.finished{background:#edf4ee;color:#365344}.state,.empty-tip{padding:24rpx;color:#818b84;font-size:24rpx}.state.error{color:#b34f3b}
</style>
