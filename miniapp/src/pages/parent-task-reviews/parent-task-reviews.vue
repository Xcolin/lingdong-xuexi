<template>
  <view class="page">
    <text class="title">待审核任务</text>
    <button :disabled="busy" @tap="load(1)">刷新待办</button>
    <text v-if="busy" class="muted">正在处理…</text>
    <text v-if="error" class="error">{{ error }}</text>
    <template v-if="allowed">
      <template v-if="detail">
        <view class="card">
          <text class="heading">{{ detail.title }}</text>
          <text>{{ detail.studentName }} · {{ sourceName(detail.sourceType) }}</text>
          <text>基础积分：{{ detail.basePoints }}</text>
          <text>提交时间：{{ detail.latestCheckIn?.submittedAt }}</text>
          <text class="content">{{ detail.latestCheckIn?.content || '未填写文字说明' }}</text>
          <button v-for="file in detail.latestCheckIn?.attachments || []" :key="file.id" :disabled="busy" @tap="preview(file.contentUrl)">查看附件：{{ file.originalName }}</button>
          <textarea v-model="comment" :disabled="busy || stale" maxlength="500" placeholder="退回原因（必填，最多500字）" />
          <button :disabled="busy || stale" @tap="submit(true)">审核通过</button>
          <button :disabled="busy || stale || !comment.trim()" @tap="submit(false)">退回修改</button>
          <button :disabled="busy" @tap="load(page)">返回列表</button>
        </view>
      </template>
      <template v-else-if="!busy && loaded">
        <text class="muted">共 {{ total }} 项待审核任务</text>
        <text v-if="total === 0">暂无待审核任务</text>
        <view v-for="item in items" :key="item.assignmentId" class="card">
          <text class="heading">{{ item.title }}</text><text>{{ item.studentName }} · {{ sourceName(item.sourceType) }}</text>
          <button @tap="open(item.assignmentId)">查看并审核</button>
        </view>
        <view v-if="total > 0" class="paging"><button :disabled="page <= 1" @tap="load(page - 1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page * 20 >= total" @tap="load(page + 1)">下一页</button></view>
      </template>
    </template>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { parentReviewApi, type ParentTaskReview } from '@/api/parent-task-review';
import { getMiniappCapabilities } from '@/api/capability';
import { getParentSession } from '@/session/parent-session';
import { canReview, requestEpoch } from '@/models/parent-task-reviews';
import { ApiError } from '@/api/http';
const items = ref<ParentTaskReview[]>([]), detail = ref<ParentTaskReview | null>(null);
const page = ref(1), total = ref(0), busy = ref(false), allowed = ref(false), stale = ref(false);
const loaded = ref(false);
const error = ref(''), comment = ref(''); const epoch = requestEpoch();
function clear() { loaded.value = false; items.value = []; detail.value = null; total.value = 0; allowed.value = false; }
function valid(n: number, token: string) { return epoch.current(n) && getParentSession()?.accessToken === token; }
async function access(n: number, token: string) {
  const [user, capabilities] = await Promise.all([parentReviewApi.me(token), getMiniappCapabilities()]);
  if (!valid(n, token)) return false;
  if (!canReview(user, capabilities.learningTaskManagementEnabled)) { clear(); throw new Error('任务审核未开启或无审核权限'); }
  allowed.value = true; return true;
}
function failed(e: unknown) {
  if (e instanceof ApiError && ([401, 403, 404].includes(e.statusCode) || e.code === 'FEATURE_DISABLED')) clear();
  error.value = e instanceof Error ? e.message : '操作失败，请刷新重试';
}
async function load(target = 1) {
  const n = epoch.next(), token = getParentSession()?.accessToken; clear(); error.value = ''; busy.value = true;
  if (!token) { busy.value = false; error.value = '请先登录家长账号'; return; }
  try {
    if (!await access(n, token)) return;
    const result = await parentReviewApi.list(token, target);
    if (!valid(n, token)) return;
    loaded.value = true; items.value = result.items; total.value = result.total; page.value = result.page; stale.value = false;
  } catch (e) { if (valid(n, token)) failed(e); }
  finally { if (epoch.current(n)) busy.value = false; }
}
async function open(id: string) {
  const n = epoch.next(), token = getParentSession()?.accessToken; if (!token) { clear(); return; }
  busy.value = true; error.value = ''; items.value = []; detail.value = null; comment.value = ''; stale.value = false;
  try { if (!await access(n, token)) return; const value = await parentReviewApi.detail(token, id); if (valid(n, token)) detail.value = value; }
  catch (e) { if (valid(n, token)) failed(e); }
  finally { if (epoch.current(n)) busy.value = false; }
}
async function submit(approve: boolean) {
  if (busy.value || stale.value || !detail.value?.latestCheckIn) return;
  const current = detail.value, expectedCheckInId = detail.value.latestCheckIn.id, token = getParentSession()?.accessToken; if (!token) { clear(); return; }
  const n = epoch.next(); busy.value = true; error.value = '';
  try {
    if (!await access(n, token)) return;
    // 重新读取最新打卡，避免对已经重新提交的内容沿用旧审核决定。
    const latest = await parentReviewApi.detail(token, current.assignmentId);
    if (!valid(n, token)) return;
    if (latest.latestCheckIn?.id !== current.latestCheckIn?.id) throw new Error('任务已更新，请刷新后重新审核');
    if (approve) await parentReviewApi.approve(token, current.assignmentId, expectedCheckInId);
    else await parentReviewApi.reject(token, current.assignmentId, comment.value.trim(), expectedCheckInId);
    if (valid(n, token)) await load(1);
  } catch (e) { if (valid(n, token)) { stale.value = true; failed(e); error.value += '；请刷新后重试'; } }
  finally { if (epoch.current(n)) busy.value = false; }
}
async function preview(url: string) {
  const token = getParentSession()?.accessToken; if (!token || busy.value) return;
  const n = epoch.next();
  busy.value = true;
  try { if (!await access(n, token)) return; const file = await parentReviewApi.download(token, url); if (valid(n, token)) await uni.previewImage({ urls: [file] }); }
  catch (e) { if (valid(n, token)) failed(e); }
  finally { if (epoch.current(n)) busy.value = false; }
}
function sourceName(source: string) { return ({ FAMILY: '家庭任务', TEACHER: '教师任务', ORGANIZATION: '机构任务' } as Record<string, string>)[source] || '学习任务'; }
function hide() { epoch.invalidate(); clear(); busy.value = false; }
onShow(() => load(1)); onHide(hide); onUnload(hide);
</script>
<style scoped>
.page { padding: 32rpx; background: #f4f7f5; min-height: 100vh; box-sizing: border-box; color: #1c2b28; }
.title { display:block; font-size:40rpx; font-weight:700; margin-bottom:24rpx; }
.card { background:white; border-radius:16rpx; padding:28rpx; margin-top:24rpx; }
.card text { display:block; margin-bottom:16rpx; overflow-wrap:anywhere; }
.heading { font-size:32rpx; font-weight:600; }.content { white-space:pre-wrap; }
button { margin:16rpx 0; font-size:28rpx; }.muted,.error { display:block; margin:20rpx 0; }.error { color:#a33c2e; }
textarea { width:100%; box-sizing:border-box; border:1px solid #ccc; padding:16rpx; height:160rpx; }
.paging { display:flex; align-items:center; justify-content:space-between; gap:12rpx; }
</style>
