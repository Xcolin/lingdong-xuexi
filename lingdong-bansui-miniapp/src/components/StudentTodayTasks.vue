<template>
  <view class="today">
    <view class="head-row">
      <text class="heading">今日任务</text>
      <button class="refresh-button" :disabled="busy" @tap="load">{{ busy ? '加载中' : '刷新' }}</button>
    </view>
    <view v-if="busy" class="state">正在加载今日任务</view>
    <view v-else-if="error" class="state error">{{ error }}</view>
    <template v-else-if="allowed">
      <view class="summary-row">
        <text class="summary-count">今日共 {{ total }} 项任务</text>
        <text v-if="total === 0" class="summary-hint">今日暂无任务</text>
      </view>
      <view v-for="task in tasks" :key="task.id" class="task-row" @tap="open(task.id)">
        <view class="task-main">
          <text class="task-title">{{ task.title }}</text>
          <text class="meta">{{ sourceName(task.sourceType) }} · {{ statusName(task.effectiveStatus) }}</text>
        </view>
        <text class="task-arrow">›</text>
      </view>
      <view v-if="total === 0" class="empty-tip">完成的任务将转为待审核，由家长或教师确认</view>
    </template>
    <view v-else class="state">今日任务暂不可用</view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { request } from '@/api/http';
import { getMiniappCapabilities } from '@/api/capability';
import { listStudentTaskAssignments, type StudentTaskAssignment } from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';
const tasks = ref<StudentTaskAssignment[]>([]), total = ref(0), busy = ref(false), allowed = ref(false), error = ref('');
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
    const result = await listStudentTaskAssignments({ scheduledDate, page: 1, pageSize: 3 });
    if (!valid()) return;
    tasks.value = result.items; total.value = result.total; allowed.value = true;
  } catch (cause) {
    if (valid()) { clear(); error.value = cause instanceof Error ? cause.message : '今日任务查询失败'; }
  } finally { if (current === revision) busy.value = false; }
}
function open(id: string) {
  if (allowed.value && !busy.value) void uni.navigateTo({ url: `/pages/task-detail/task-detail?id=${encodeURIComponent(id)}` });
}
function sourceName(source: string) { return ({ FAMILY: '家庭', TEACHER: '教师', ORGANIZATION: '机构' } as Record<string, string>)[source] || '学习任务'; }
function statusName(status: string) { return ({ PENDING_CLAIM: '待认领', IN_PROGRESS: '进行中', PENDING_REVIEW: '待审核', NEEDS_IMPROVEMENT: '待优化', PAUSED: '暂停中', EXEMPT: '免执行', COMPLETED: '已完成' } as Record<string, string>)[status] || status; }
function hide() { revision++; clear(); busy.value = false; }
onShow(load); onHide(hide); onUnload(hide);
</script>
<style scoped>
.today {
  margin-top: 32rpx;
  padding: 30rpx;
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(21, 54, 43, 0.06);
}
.head-row { display: flex; align-items: center; justify-content: space-between; }
.heading { font-size: 34rpx; font-weight: 700; color: #1c2b28; }
.refresh-button {
  height: 56rpx;
  display: flex;
  align-items: center;
  margin: 0;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #e7f2ed;
  color: #167c5a;
  font-size: 24rpx;
  font-weight: 600;
}
.refresh-button::after { border: 0; }
.summary-row { display: flex; align-items: baseline; gap: 16rpx; margin-top: 20rpx; }
.summary-count { color: #4b5c55; font-size: 26rpx; }
.summary-hint { color: #8a9992; font-size: 24rpx; }
.state { padding: 40rpx 0 16rpx; color: #8a9992; font-size: 26rpx; }
.state.error { color: #b34f3b; }
.task-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 20rpx;
  padding: 24rpx 26rpx;
  border-radius: 16rpx;
  background: #f7faf8;
}
.task-main { min-width: 0; flex: 1; }
.task-title { display: block; color: #1c2b28; font-size: 28rpx; font-weight: 600; overflow-wrap: anywhere; }
.meta { display: block; margin-top: 8rpx; color: #8a9992; font-size: 24rpx; }
.task-arrow { color: #bcd8cd; font-size: 40rpx; line-height: 1; }
.empty-tip { margin-top: 20rpx; color: #8a9992; font-size: 24rpx; }
</style>
