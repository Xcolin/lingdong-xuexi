<template>
  <view class="prototype-workspace page">
    <PixelPageHeader :title="detail ? '打卡审核详情' : stage==='success' ? '审核结果' : '待审核任务'" back intercept-back @back="back" />
    <view v-if="stage==='success'" class="card success-card">
      <PrototypeIcon name="check" :size="48" />
      <text class="heading">{{ resultApproved ? '审核已通过' : '已退回修改' }}</text>
      <text class="muted">审核结果已提交</text>
      <button @tap="backToList">返回列表</button>
    </view>

    <text v-if="busy" class="muted">正在处理…</text>
    <text v-if="error" class="error">{{ error }}</text>
    <template v-if="allowed && stage!=='success'">
      <template v-if="detail">
        <view class="card detail-hero">
          <view class="review-symbol"><PrototypeIcon :name="typeIcons[detail.taskType || ''] || 'book'" :size="28" /></view>
          <view class="detail-hero-main">
            <text class="heading">{{ detail.title }}</text>
          </view>
          <text class="status-badge" :class="statusClass(detail)">{{ statusLabel(detail) }}</text>
        </view>
        <view class="card detail-table">
          <view class="detail-row"><text class="detail-label">任务日期</text><text class="detail-value">{{ detailDate }}</text></view>
          <view class="detail-row"><text class="detail-label">提交时间</text><text class="detail-value">{{ formatTime(detail.latestCheckIn?.submittedAt) }}</text></view>
          <view class="detail-row"><text class="detail-label">提交成员</text><text class="detail-value">{{ memberLabel }}</text></view>
        </view>
        <view class="card checkin-block">
          <view class="required-label">小主人的打卡内容</view>
          <text class="checkin-content">{{ detail.latestCheckIn?.content || '未填写文字说明' }}</text>
          <view v-if="detailImages.length" class="photo-grid">
            <image v-for="(image, index) in detailImages" :key="image.id" class="photo-cell" :src="thumbs[image.id] || ''" mode="aspectFill" @tap="previewDetailImage(index)" />
          </view>
        </view>
        <view v-if="canAct || reviewCommentText" class="card comment-block">
          <view class="required-label">成长伙伴的评语<text class="label-hint">（提交时填写）</text></view>
          <textarea v-if="canAct" v-model="comment" :disabled="busy || stale" maxlength="500"
            placeholder="宝贝今天很认真，主动帮忙做家务，值得表扬！" />
          <text v-else class="review-echo">{{ reviewCommentText || '未填写评语' }}</text>
          <text v-if="canAct" class="counter">{{ comment.length }}/500</text>
        </view>
        <template v-if="canAct">
          <view class="review-question">是否通过这次打卡？</view>
          <view class="review-fixed-actions">
            <button class="review-reject" :disabled="busy || stale || !comment.trim()"
              @tap="confirmSubmit(false)">驳回</button>
            <button class="review-approve" :disabled="busy || stale" @tap="confirmSubmit(true)">通过</button>
          </view>
        </template>
        <button v-else class="review-back" :disabled="busy" @tap="backToList">返回列表</button>
      </template>
      <template v-else-if="!busy && loaded">
        <view class="status-tabs">
          <view v-for="tab in tabs" :key="tab.key" class="status-tab" :class="{ active: statusTab === tab.key }" @tap="switchTab(tab.key)">
            <text>{{ tab.label }}</text>
            <text v-if="tab.count !== null" class="tab-count">({{ tab.count }})</text>
          </view>
        </view>
        <view v-if="items.length === 0" class="review-empty">
          <PrototypeIcon name="check" tone="muted" :size="58" />
          <text>{{ statusTab === 'PENDING_REVIEW' ? '暂无待审核任务' : '暂无相关审核记录' }}</text>
        </view>
        <view v-for="item in items" :key="item.assignmentId" class="card review-list-card" @tap="open(item.assignmentId)">
          <view class="review-symbol"><PrototypeIcon :name="typeIcons[item.taskType || ''] || 'book'" :size="26" /></view>
          <view class="review-main">
            <view class="review-top">
              <text class="heading">{{ item.title }}</text>
              <text class="status-badge" :class="statusClass(item)">{{ statusLabel(item) }}</text>
            </view>
            <text class="review-meta">{{ item.studentName }}<template v-if="item.currentClassName"> · {{ item.currentClassName }}</template></text>
            <text class="review-meta">任务日期：{{ dotDate(item.scheduledDate) }}</text>
          </view>
          <image v-if="thumbOf(item)" class="review-thumb" :src="thumbOf(item)" mode="aspectFill" />
          <view v-else class="review-thumb placeholder"><PrototypeIcon name="task" :size="22" /></view>
        </view>
        <view v-if="items.length" class="paging">
          <button :disabled="page <= 1" @tap="load(page - 1)">上一页</button>
          <text>第 {{ page }} 页</text>
          <button :disabled="page * 20 >= total" @tap="load(page + 1)">下一页</button>
        </view>
      </template>
    </template>
  </view>
</template>
<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref, computed } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { parentReviewApi, type ParentTaskReview, type ReviewStatusTab } from '@/api/parent-task-review';
import { getMiniappCapabilities } from '@/api/capability';
import { getParentSession } from '@/session/parent-session';
import { canReview, requestEpoch } from '@/models/parent-task-reviews';
import { ApiError } from '@/api/http';
const items = ref<ParentTaskReview[]>([]), detail = ref<ParentTaskReview | null>(null);
const page = ref(1), total = ref(0), busy = ref(false), allowed = ref(false), stale = ref(false);
const loaded = ref(false);
const statusTab = ref<ReviewStatusTab>('PENDING_REVIEW');
const statusCounts = ref<Record<string, number>>({});
const stage = ref<'detail' | 'success'>('detail'), resultApproved = ref(false);
const error = ref(''), comment = ref('');
const thumbs = ref<Record<string, string>>({});
const epoch = requestEpoch();
const typeIcons: Record<string, string> = { STUDY: 'book', LIFE: 'home', SPORT: 'growth', HOBBY: 'star', CHORE: 'leaf', OTHER: 'task' };
const tabs = computed(() => [
  { key: 'ALL' as ReviewStatusTab, label: '全部', count: null as number | null },
  { key: 'PENDING_REVIEW' as ReviewStatusTab, label: '待审核', count: statusCounts.value.SUBMITTED ?? 0 },
  { key: 'APPROVED' as ReviewStatusTab, label: '已通过', count: null as number | null },
  { key: 'REJECTED' as ReviewStatusTab, label: '已驳回', count: null as number | null }
]);
const canAct = computed(() => detail.value?.latestCheckIn?.status === 'SUBMITTED');
const reviewCommentText = computed(() => detail.value?.latestCheckIn?.reviewComment || '');
const detailImages = computed(() => (detail.value?.latestCheckIn?.attachments || []).filter(a => a.contentType.startsWith('image/')));
const detailDate = computed(() => {
  const date = detail.value?.scheduledDate;
  return date ? `${dotDate(date)}（${weekdayLabel(date)}）` : '未设置';
});
const memberLabel = computed(() => {
  const d = detail.value;
  if (!d) return '';
  return d.currentClassName ? `${d.studentName}（${d.currentClassName}）` : d.studentName;
});
function dotDate(iso: string | null) {
  return iso ? iso.replace(/-/g, '.') : '未设置';
}
function weekdayLabel(iso: string) {
  const matched = iso.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!matched) return '';
  const labels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  return labels[new Date(Number(matched[1]), Number(matched[2]) - 1, Number(matched[3])).getDay()];
}
function formatTime(value: string | null | undefined) {
  if (!value) return '未提交';
  return value.replace('T', ' ').slice(0, 16);
}
function statusLabel(item: ParentTaskReview) {
  return ({ SUBMITTED: '待审核', APPROVED: '已通过', REJECTED: '已驳回' } as Record<string, string>)[item.latestCheckIn?.status || ''] || '待审核';
}
function statusClass(item: ParentTaskReview) {
  return ({ SUBMITTED: 'submitted', APPROVED: 'approved', REJECTED: 'rejected' } as Record<string, string>)[item.latestCheckIn?.status || ''] || 'submitted';
}
function thumbOf(item: ParentTaskReview) {
  const image = (item.latestCheckIn?.attachments || []).find(a => a.contentType.startsWith('image/'));
  return image ? (thumbs.value[image.id] || '') : '';
}
async function loadThumbs(list: ParentTaskReview[]) {
  const token = getParentSession()?.accessToken;
  if (!token) return;
  const targets = new Map<string, string>();
  for (const item of [...list, ...(detail.value ? [detail.value] : [])]) {
    for (const attachment of item.latestCheckIn?.attachments || []) {
      if (attachment.contentType.startsWith('image/') && !thumbs.value[attachment.id] && !targets.has(attachment.id)) {
        targets.set(attachment.id, attachment.contentUrl);
      }
    }
  }
  for (const [id, url] of targets) {
    try {
      const temp = await parentReviewApi.download(token, url);
      thumbs.value = { ...thumbs.value, [id]: temp };
    } catch { thumbs.value = { ...thumbs.value, [id]: '' }; }
  }
}
async function previewDetailImage(index: number) {
  const token = getParentSession()?.accessToken;
  if (!token || !detailImages.value.length) return;
  await loadThumbs(detail.value ? [detail.value] : []);
  const urls = detailImages.value.map(image => thumbs.value[image.id]).filter(Boolean);
  if (urls.length) void uni.previewImage({ urls, current: urls[index] });
}
function clear() { stage.value = 'detail'; loaded.value = false; items.value = []; detail.value = null; total.value = 0; allowed.value = false; }
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
function back() {
  if (busy.value) return;
  if (detail.value || stage.value === 'success') { backToList(); return; }
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/family-tasks/family-tasks' }) });
}
function backToList() { void load(page.value); }
function switchTab(tab: ReviewStatusTab) {
  if (statusTab.value === tab) return;
  statusTab.value = tab;
  void load(1);
}
async function load(target = 1) {
  const n = epoch.next(), token = getParentSession()?.accessToken; clear(); error.value = ''; busy.value = true;
  if (!token) { busy.value = false; error.value = '请先登录家长账号'; return; }
  try {
    if (!await access(n, token)) return;
    const result = await parentReviewApi.list(token, target, statusTab.value);
    if (!valid(n, token)) return;
    loaded.value = true; items.value = result.items; total.value = result.total; page.value = result.page;
    statusCounts.value = result.statusCounts || {}; stale.value = false;
    void loadThumbs(result.items);
  } catch (e) { if (valid(n, token)) failed(e); }
  finally { if (epoch.current(n)) busy.value = false; }
}
async function open(id: string) {
  stage.value = 'detail';
  const n = epoch.next(), token = getParentSession()?.accessToken; if (!token) { clear(); return; }
  busy.value = true; error.value = ''; items.value = []; detail.value = null; comment.value = ''; stale.value = false;
  try {
    if (!await access(n, token)) return;
    const value = await parentReviewApi.detail(token, id);
    if (valid(n, token)) { detail.value = value; void loadThumbs([value]); }
  } catch (e) { if (valid(n, token)) failed(e); }
  finally { if (epoch.current(n)) busy.value = false; }
}
function confirmSubmit(approve: boolean) {
  if (busy.value || stale.value || !detail.value?.latestCheckIn) return;
  if (!approve && !comment.value.trim()) { error.value = '驳回时必须填写评语'; return; }
  uni.showModal({
    title: approve ? '确认通过本次打卡？' : '确认驳回本次打卡？',
    content: approve ? '通过后将发放成长积分。' : '驳回后成长小主人可修改后重新提交。',
    success: choice => { if (choice.confirm) void submit(approve); }
  });
}
async function submit(approve: boolean) {
  if (busy.value || stale.value || !detail.value?.latestCheckIn) return;
  if (!approve && !comment.value.trim()) { error.value = '驳回时必须填写评语'; return; }
  const current = detail.value, expectedCheckInId = detail.value.latestCheckIn.id, token = getParentSession()?.accessToken; if (!token) { clear(); return; }
  const n = epoch.next(); busy.value = true; error.value = '';
  try {
    if (!await access(n, token)) return;
    // 重新读取最新打卡，避免对已经重新提交的内容沿用旧审核决定。
    const latest = await parentReviewApi.detail(token, current.assignmentId);
    if (!valid(n, token)) return;
    if (latest.latestCheckIn?.id !== current.latestCheckIn?.id) throw new Error('任务已更新，请刷新后重新审核');
    if (approve) await parentReviewApi.approve(token, current.assignmentId, expectedCheckInId, comment.value.trim() || undefined);
    else await parentReviewApi.reject(token, current.assignmentId, comment.value.trim(), expectedCheckInId);
    if (valid(n, token)) { resultApproved.value = approve; stage.value = 'success'; comment.value = ''; }
  } catch (e) { if (valid(n, token)) { stale.value = true; failed(e); error.value += '；请刷新后重试'; } }
  finally { if (epoch.current(n)) busy.value = false; }
}
function hide() { epoch.invalidate(); clear(); comment.value = ''; busy.value = false; }
onShow(() => load(1)); onHide(hide); onUnload(hide);
</script>
<style scoped>
.page { padding: 32rpx; background: #f4f7f5; min-height: calc(100vh - var(--window-top,0px)); box-sizing: border-box; color: #1c2b28; }
.card { background:white; border-radius:16rpx; padding:28rpx; margin-top:24rpx; }
.card text { display:block; margin-bottom:16rpx; overflow-wrap:anywhere; }
.heading { font-size:32rpx; font-weight:600; }
button { margin:16rpx 0; font-size:28rpx; }.muted,.error { display:block; margin:20rpx 0; }.error { color:#a33c2e; }
textarea { width:100%; box-sizing:border-box; border:1px solid #ccc; padding:16rpx; height:160rpx; }
.paging { display:flex; align-items:center; justify-content:space-between; gap:12rpx; }
</style>

<style lang="scss" scoped>@import "@/styles/workspace-prototype.scss";</style>

<style scoped>
.prototype-workspace{background:#f4faf7!important;padding:0 28rpx 48rpx!important;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;color:#172d27;}
.card,.section-band{background:#fff!important;border:1rpx solid #e2eee7!important;border-radius:24rpx!important;box-shadow:none!important;margin-bottom:22rpx;padding:24rpx!important;}
textarea{background:#fbfcfb!important;border:1rpx solid #dce5df!important;border-radius:14rpx!important;padding:18rpx!important;box-sizing:border-box;font-size:26rpx!important;width:100%;}
button{border-radius:18rpx!important;font-size:26rpx!important;}button::after{border:0;}
.heading,.section-title{color:#162b24;font-weight:650;font-size:28rpx;}
.review-echo,.hint{color:#78847e;font-size:24rpx;line-height:1.65;}
.status-tabs{display:flex;align-items:center;gap:8rpx;margin:6rpx 0 24rpx;}
.status-tab{flex:1;text-align:center;padding:18rpx 0;font-size:27rpx;color:#5c6b63;border-bottom:4rpx solid transparent;display:flex;justify-content:center;gap:2rpx;}
.status-tab.active{color:#086544;font-weight:700;border-bottom-color:#3f9e63;}
.tab-count{font-size:24rpx;}
.review-list-card{display:flex;gap:18rpx;align-items:flex-start;position:relative;padding-left:110rpx!important;min-height:180rpx;box-sizing:border-box;}
.review-list-card>.review-symbol{position:absolute;left:24rpx;top:26rpx;}
.review-symbol{width:80rpx;height:80rpx;flex:none;background:#e9f8ed;color:#066444;border-radius:26rpx;display:flex;align-items:center;justify-content:center;}
.review-main{flex:1;min-width:0;}
.review-top{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx;}
.review-top .heading{flex:1;min-width:0;overflow-wrap:anywhere;}
.review-meta{display:block;color:#78847e;font-size:24rpx;line-height:1.7;margin-top:6rpx;}
.status-badge{flex:none;font-size:22rpx;border-radius:10rpx;padding:6rpx 14rpx;font-weight:600;}
.status-badge.submitted{background:#fff3d5;color:#dd8e12;}
.status-badge.approved{background:#e7f7e9;color:#168450;}
.status-badge.rejected{background:#fdecec;color:#d9534f;}
.review-thumb{width:150rpx;height:150rpx;border-radius:16rpx;background:#eef3ee;flex:none;}
.review-thumb.placeholder{display:flex;align-items:center;justify-content:center;color:#9aa8a0;}
.detail-hero{display:flex;align-items:flex-start;gap:18rpx;}
.detail-hero-main{flex:1;min-width:0;}
.detail-table{padding:6rpx 26rpx!important;}
.detail-row{display:flex;align-items:flex-start;gap:20rpx;padding:22rpx 0;border-bottom:1rpx solid #edf1ee;}
.detail-row:last-child{border-bottom:0;}
.detail-label{flex:none;width:150rpx;font-size:25rpx;color:#78847e;}
.detail-value{flex:1;font-size:25rpx;color:#1c2b28;line-height:1.6;overflow-wrap:anywhere;}
.required-label{display:flex;align-items:center;gap:4rpx;margin:6rpx 0 18rpx;font-size:27rpx;font-weight:650;color:#172b24;}
.label-hint{font-size:22rpx;font-weight:400;color:#78847e;}
.checkin-content{font-size:26rpx;color:#1c2b28;line-height:1.7;overflow-wrap:anywhere;white-space:pre-wrap;}
.photo-grid{display:flex;flex-wrap:wrap;gap:14rpx;margin-top:18rpx;}
.photo-cell{width:180rpx;height:180rpx;border-radius:16rpx;background:#eef3ee;}
.counter{display:block;text-align:right;color:#7b8881;font-size:22rpx;margin-top:10rpx;}
.review-question{margin:30rpx 6rpx 20rpx;font-size:27rpx;font-weight:650;color:#172b24;}
.review-empty{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:20rpx;min-height:260rpx;background:#fffefa;border:1rpx solid #e1e9e2;border-radius:24rpx;color:#809486;font-size:26rpx;}
.success-card{display:flex;flex-direction:column;align-items:center;gap:18rpx;padding:60rpx 28rpx!important;text-align:center;}
.review-back{margin:26rpx 0 40rpx;background:#edf5ef!important;color:#35624e!important;}
.review-fixed-actions{position:fixed;bottom:0;left:0;right:0;padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));background:white;display:flex;gap:18rpx;z-index:10;border-top:1rpx solid #e1eae4;}
.review-fixed-actions button{flex:1;margin:0;min-width:0;}
.review-reject{background:white!important;color:#df3e3e!important;border:1rpx solid #df3e3e;}
.review-approve{background:#086344!important;color:white!important;}
</style>
