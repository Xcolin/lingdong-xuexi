<template>
  <view class="prototype-workspace page">
    <PixelPageHeader :title="stage==='reject'?'退回修改':stage==='approve'?'通过确认':stage==='success'?'审核结果':detail?'打卡审核':'待审核任务'" back intercept-back @back="back" />
    <view v-if="stage==='success'" class="card"><PrototypeIcon name="check" :size="48"/><text class="heading">{{resultApproved?'审核已通过':'已退回修改'}}</text><text>审核结果已提交</text><button @tap="load(1)">返回待审核列表</button></view>
    
    <text v-if="busy" class="muted">正在处理…</text>
    <text v-if="error" class="error">{{ error }}</text>
    <template v-if="allowed && stage!=='success'">
      <template v-if="detail">
        <view class="card review-summary"><view class="review-symbol"><PrototypeIcon name="book" :size="28" /></view><view><text class="heading">{{ detail.title }}</text><text>{{ detail.studentName }} · {{ sourceName(detail.sourceType) }}</text><text>基础积分：{{ detail.basePoints }}</text><text class="muted">提交时间：{{ detail.latestCheckIn?.submittedAt }}</text></view></view>
        <view class="card"><text class="heading">小主人的打卡内容</text><text class="content">{{ detail.latestCheckIn?.content || '未填写文字说明' }}</text><view class="review-attachments"><button v-for="file in detail.latestCheckIn?.attachments || []" :key="file.id" :disabled="busy" @tap="preview(file.contentUrl)"><PrototypeIcon name="book" :size="18" /> {{ file.originalName }} ›</button></view></view>
        <view v-if="stage==='reject'" class="card return-card"><text class="heading">退回意见</text><text class="muted">说明需要改进的地方，帮助成长小主人完成任务</text><textarea v-model="comment" :disabled="busy || stale" maxlength="500" placeholder="退回原因（退回时必填，最多500字）" /><text class="counter">{{ comment.length }}/500</text></view>
        <view v-if="stage==='approve'" class="card"><text class="heading">确认通过本次打卡？</text><text>提交前将核对最新打卡，避免审核已经更新的内容。</text></view>
        <button class="review-back" :disabled="busy" @tap="load(page)">返回列表</button>
        <view class="review-fixed-actions"><button class="review-reject" :disabled="busy || stale || (stage==='reject'&&!comment.trim())" @tap="stage==='reject'?submit(false):stage='reject'">退回修改</button><button class="review-approve" :disabled="busy || stale" @tap="stage==='approve'?submit(true):stage='approve'">{{stage==='approve'?'确认通过':'审核通过'}}</button></view>
      </template>
      <template v-else-if="!busy && loaded">
        <text class="muted">共 {{ total }} 项待审核任务</text>
        <view v-if="total === 0" class="review-empty"><PrototypeIcon name="check" tone="muted" :size="58"/><text>暂无待审核任务</text></view>
        <view v-for="item in items" :key="item.assignmentId" class="card review-list-card"><view class="review-symbol"><PrototypeIcon name="book" :size="26" /></view>
          <text class="review-status">待审核</text><text class="heading">{{ item.title }}</text><text>{{ item.studentName }} · {{ sourceName(item.sourceType) }}</text>
          <button @tap="open(item.assignmentId)">查看并审核</button>
        </view>
        <view v-if="total > 0" class="paging"><button :disabled="page <= 1" @tap="load(page - 1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page * 20 >= total" @tap="load(page + 1)">下一页</button></view>
      </template>
    </template>
  </view>
</template>
<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
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
const stage=ref<'detail'|'reject'|'approve'|'success'>('detail'),resultApproved=ref(false);
const error = ref(''), comment = ref(''); const epoch = requestEpoch();
function clear() { stage.value='detail'; loaded.value = false; items.value = []; detail.value = null; total.value = 0; allowed.value = false; }
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
function back(){if(busy.value)return;if(stage.value==='reject'||stage.value==='approve'){stage.value='detail';return;}if(detail.value||stage.value==='success'){void load(page.value);return;}uni.navigateBack({fail:()=>uni.reLaunch({url:'/pages/family-tasks/family-tasks'})});}
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
  stage.value='detail';
  const n = epoch.next(), token = getParentSession()?.accessToken; if (!token) { clear(); return; }
  busy.value = true; error.value = ''; items.value = []; detail.value = null; comment.value = ''; stale.value = false;
  try { if (!await access(n, token)) return; const value = await parentReviewApi.detail(token, id); if (valid(n, token)) detail.value = value; }
  catch (e) { if (valid(n, token)) failed(e); }
  finally { if (epoch.current(n)) busy.value = false; }
}
async function submit(approve: boolean) {
  if (busy.value || stale.value || !detail.value?.latestCheckIn || (!approve && !comment.value.trim())) return;
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
    if (valid(n, token)) {resultApproved.value=approve;stage.value='success';comment.value='';}
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
function hide() { epoch.invalidate(); clear(); comment.value=''; busy.value = false; }
onShow(() => load(1)); onHide(hide); onUnload(hide);
</script>
<style scoped>
.page { padding: 32rpx; background: #f4f7f5; min-height: calc(100vh - var(--window-top,0px)); box-sizing: border-box; color: #1c2b28; }
.title { display:block; font-size:40rpx; font-weight:700; margin-bottom:24rpx; }
.card { background:white; border-radius:16rpx; padding:28rpx; margin-top:24rpx; }
.card text { display:block; margin-bottom:16rpx; overflow-wrap:anywhere; }
.heading { font-size:32rpx; font-weight:600; }.content { white-space:pre-wrap; }
button { margin:16rpx 0; font-size:28rpx; }.muted,.error { display:block; margin:20rpx 0; }.error { color:#a33c2e; }
textarea { width:100%; box-sizing:border-box; border:1px solid #ccc; padding:16rpx; height:160rpx; }
.paging { display:flex; align-items:center; justify-content:space-between; gap:12rpx; }
</style>

<style lang="scss" scoped>@import "@/styles/workspace-prototype.scss";</style>

<style scoped>
.prototype-workspace{background:#f4faf7!important;padding:0 28rpx 48rpx!important;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;color:#172d27;}
.prototype-workspace.has-tabbar{padding-bottom:160rpx!important;}
.prototype-header{margin:0 -28rpx 28rpx;padding:32rpx 32rpx 48rpx;display:flex;align-items:center;gap:18rpx;background:linear-gradient(115deg,#226d5c,#07573f);color:#fff;border-radius:0 0 30rpx 30rpx;}
.prototype-header-title{font-size:34rpx;font-weight:700;letter-spacing:1rpx;}
.card,.section-band,.summary-band,.action-band,.heading-band{background:#fff!important;border:1rpx solid #e2eee7!important;border-radius:24rpx!important;box-shadow:none!important;margin-bottom:22rpx;padding:24rpx!important;}
input,textarea,.field{background:#fbfcfb!important;border:1rpx solid #dce5df!important;border-radius:14rpx!important;padding:18rpx!important;box-sizing:border-box;font-size:26rpx!important;}
input{min-height:76rpx;}textarea{width:100%;}
button{border-radius:18rpx!important;font-size:26rpx!important;}button::after{border:0;}
.heading,.section-title,.row-title,.task-title{color:#162b24;font-weight:650;font-size:28rpx;}
.row-meta,.task-meta,.hint{color:#78847e;font-size:24rpx;line-height:1.65;}
.list-row,.relationship-row,.task-row{border-bottom:1rpx solid #edf1ee;padding:24rpx 0;}
.current-tag,.read-tag,.source-badge{background:#e7f7e9;color:#168450;border-radius:10rpx;padding:6rpx 12rpx;font-size:22rpx;}
</style>
<style scoped>.review-summary{display:flex;gap:20rpx;}.review-symbol{width:80rpx;height:80rpx;flex-shrink:0;background:#e9f8ed;color:#066444;border-radius:26rpx;display:flex;align-items:center;justify-content:center;}.review-summary text{display:block;font-size:24rpx;line-height:1.8;}.review-list-card{position:relative;padding-left:124rpx!important;}.review-list-card>.review-symbol{position:absolute;left:24rpx;top:28rpx;}.review-status{float:right;background:#fff3d5;color:#dd8e12;padding:5rpx 12rpx;border-radius:10rpx;font-size:22rpx;}.review-attachments button{background:#f5faf6;color:#126247;text-align:left;margin-top:12rpx;}.return-card textarea{margin-top:18rpx;min-height:210rpx;}.counter{display:block;text-align:right;color:#7b8881;font-size:22rpx;margin-top:10rpx;}.review-fixed-actions{position:fixed;bottom:0;left:0;right:0;padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));background:white;display:flex;gap:18rpx;z-index:10;border-top:1rpx solid #e1eae4;}.review-fixed-actions button{flex:1;margin:0;}.review-reject{background:white!important;color:#df3e3e!important;border:1rpx solid #df3e3e;}.review-approve{background:#086344!important;color:white!important;}.review-back{margin-bottom:150rpx;background:#edf5ef!important;color:#35624e!important;}</style><style scoped>.review-empty{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:20rpx;min-height:260rpx;background:#fffefa;border:1rpx solid #e1e9e2;border-radius:24rpx;color:#809486;font-size:26rpx;}.review-list-card{min-height:164rpx;box-sizing:border-box;}.review-list-card .heading{display:block;padding-right:110rpx;overflow-wrap:anywhere;}.review-list-card button{background:#edf4ee!important;color:#184f40!important;margin-top:16rpx;}.review-summary>view:last-child{flex:1;min-width:0;overflow-wrap:anywhere;}.review-fixed-actions{box-sizing:border-box;}.review-fixed-actions button{min-width:0;}</style>
