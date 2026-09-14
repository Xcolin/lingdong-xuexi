<template>
  <view class="today">
    <text class="heading">今日任务</text>
    <text v-if="busy">正在加载今日任务</text>
    <text v-else-if="error" class="error">{{ error }}</text>
    <template v-else-if="allowed">
      <text>今日共 {{ total }} 项任务</text>
      <text v-if="total === 0">今日暂无任务</text>
      <button v-for="task in tasks" :key="task.id" @tap="open(task.id)">
        <text>{{ task.title }}</text>
        <text class="meta">{{ sourceName(task.sourceType) }} · {{ statusName(task.effectiveStatus) }}</text>
      </button>
    </template>
    <text v-else>今日任务暂不可用</text>
    <button :disabled="busy" @tap="load">刷新今日任务</button>
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
.today { margin-top:32rpx; padding:24rpx; background:#fff; border:1px solid #dce4e1; border-radius:12rpx; }
.today text { display:block; overflow-wrap:anywhere; }.heading { font-weight:700; margin-bottom:16rpx; }
.today button { margin-top:16rpx; font-size:28rpx; text-align:left; }.meta { color:#708078; font-size:24rpx; }.error { color:#a33c2e; }
</style>
