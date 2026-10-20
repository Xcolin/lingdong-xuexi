<template>
  <view class="page prototype-workspace detail-page">
    <PixelPageHeader title="任务详情" back />
    <text v-if="busy">正在加载任务</text><text v-if="error" class="error">{{ error }}</text>
    <template v-if="task && !busy">
      <view class="card detail-hero">
        <view class="detail-icon"><PrototypeIcon :name="typeIcons[task.taskType||'']||'book'" :size="26" /></view>
        <view class="detail-hero-main">
          <text class="detail-title">{{ task.title }}</text>
          <text class="detail-desc">{{ task.description || '未填写任务描述' }}</text>
        </view>
        <view class="type-badge">{{ typeLabel }}</view>
      </view>
      <view class="card detail-table">
        <view class="detail-row"><text class="detail-label">任务日期</text><text class="detail-value">{{ dateRange }}</text></view>
        <view class="detail-row"><text class="detail-label">重复频率</text><text class="detail-value">{{ frequencySummary }}</text></view>
        <view class="detail-row"><text class="detail-label">参与成员</text><text class="detail-value">{{ memberSummary }}</text></view>
        <view class="detail-row"><text class="detail-label">可见范围</text><text class="detail-value">{{ visibilitySummary }}</text></view>
      </view>
      <view class="progress-block">
        <view class="progress-head"><text class="progress-title">完成进度</text><text class="progress-count">{{ progressCompleted }}/{{ progressTotal }}次</text></view>
        <view class="progress-track"><view class="progress-bar" :style="{ width: progressPercent + '%' }"></view></view>
        <view v-if="days.length" class="day-dots">
          <view v-for="day in days" :key="day.date" class="day-dot">
            <view class="dot-circle" :class="day.state">{{ day.state === 'done' ? '✓' : '' }}</view>
            <text class="dot-label">{{ day.label }}</text>
          </view>
        </view>
      </view>
      <view class="records-block">
        <view class="required-label">打卡记录</view>
        <view v-if="!records.length" class="card records-empty"><text>还没有打卡记录，等待成长小主人完成第一次打卡吧</text></view>
        <view v-for="record in records" :key="record.checkInId" class="card record-card">
          <view class="record-body">
            <image v-if="recordThumb(record)" class="record-thumb" :src="recordThumb(record)" mode="aspectFill" @tap="previewRecordImage(record)" />
            <view v-else class="record-thumb placeholder"><PrototypeIcon name="task" :size="22" /></view>
            <view class="record-main">
              <view class="record-top"><text class="record-time">{{ formatTime(record.submittedAt) }}</text><text class="record-status" :class="record.status === 'APPROVED' ? 'approved' : 'pending'">{{ record.status === 'APPROVED' ? '已通过' : '待审核' }}</text></view>
              <text class="record-content">{{ record.content || '未填写打卡内容' }}</text>
              <text class="record-student">{{ record.studentName }}</text>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>
<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { familyTaskApi, type FamilyTask, type FamilyTaskCheckInRecord, type FamilyTaskOverview } from '@/api/family-task';
import { getParentSession } from '@/session/parent-session';
import { downloadTaskAttachmentAs } from '@/api/attachment';
const taskId = ref('');
const task = ref<FamilyTask | null>(null);
const records = ref<FamilyTaskCheckInRecord[]>([]);
const overview = ref<FamilyTaskOverview | null>(null);
const busy = ref(false), error = ref('');
const previewPaths = ref<Record<string, string>>({});
const typeIcons: Record<string, string> = { STUDY: 'book', LIFE: 'home', SPORT: 'growth', HOBBY: 'star', CHORE: 'leaf', OTHER: 'task' };
const typeLabel = computed(() => ({ STUDY: '学习', LIFE: '生活', SPORT: '运动', HOBBY: '兴趣', CHORE: '家务', OTHER: '其他' } as Record<string, string>)[task.value?.taskType || ''] || '学习');
const dotDate = (iso: string) => iso.replace(/-/g, '.');
const dateRange = computed(() => {
  if (!task.value) return '';
  const start = task.value.scheduledDate;
  const end = task.value.endDate || start;
  return start === end ? dotDate(start) : `${dotDate(start)} - ${dotDate(end)}`;
});
const frequencySummary = computed(() => {
  const t = task.value;
  if (!t) return '';
  if (!t.recurrenceEnabled) return '单次';
  const base = ({ DAILY: '每天', WEEKLY: '每周', CUSTOM: '自定义' } as Record<string, string>)[t.recurrenceFrequency || 'DAILY'] || '重复';
  if (t.recurrenceFrequency && t.recurrenceFrequency !== 'DAILY' && t.recurrenceWeekdays?.length) {
    const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
    return base + ' · ' + t.recurrenceWeekdays.map(d => labels[d - 1]).join('、');
  }
  return base;
});
const memberSummary = computed(() => overview.value?.memberNames || (task.value?.targets.length ? `${task.value.targets.length} 名成员` : '未选择'));
const visibilitySummary = computed(() => {
  const value = task.value?.visibility;
  if (!value) return '未设置';
  const titles: Record<string, string> = { FAMILY_ONLY: '仅家庭', FAMILY_CLASS_PARTNER: '家庭 + 班级成长伙伴', FAMILY_SCHOOL_PARTNER: '家庭 + 本校成长伙伴', CUSTOM: '自定义范围' };
  const descs: Record<string, string> = { FAMILY_ONLY: '仅自己可见', FAMILY_CLASS_PARTNER: '班级成长伙伴可见', FAMILY_SCHOOL_PARTNER: '本校成长伙伴可见', CUSTOM: '自定义可见范围' };
  return `${titles[value] || value}（${descs[value] || ''}）`;
});
const progressCompleted = computed(() => overview.value?.assignmentCompleted ?? records.value.filter(r => r.status === 'APPROVED').length);
const progressTotal = computed(() => overview.value?.assignmentTotal ?? 0);
const progressPercent = computed(() => progressTotal.value > 0 ? Math.min(100, Math.round(progressCompleted.value / progressTotal.value * 100)) : 0);
interface DayDot { date: string; label: string; state: 'done' | 'pending' | 'todo' }
const days = computed<DayDot[]>(() => {
  const t = task.value;
  if (!t) return [];
  const start = new Date(t.scheduledDate + 'T00:00:00');
  const end = new Date((t.endDate || t.scheduledDate) + 'T00:00:00');
  const total = Math.min(Math.floor((end.getTime() - start.getTime()) / 86400000) + 1, 31);
  const result: DayDot[] = [];
  for (let i = 0; i < total; i++) {
    const d = new Date(start.getTime() + i * 86400000);
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const dayRecords = records.value.filter(r => (r.submittedAt || '').slice(0, 10) === iso);
    const state: DayDot['state'] = dayRecords.some(r => r.status === 'APPROVED') ? 'done' : dayRecords.length ? 'pending' : 'todo';
    result.push({ date: iso, label: `${d.getMonth() + 1}/${d.getDate()}`, state });
  }
  return result;
});
function recordThumb(record: FamilyTaskCheckInRecord) {
  for (const attachment of record.attachments) {
    if (attachment.contentType.startsWith('image/')) return previewPaths.value[attachment.id] || '';
  }
  return '';
}
async function loadPreviews() {
  const session = getParentSession();
  if (!session) return;
  for (const record of records.value) {
    for (const attachment of record.attachments) {
      if (!attachment.contentType.startsWith('image/')) continue;
      const key = attachment.id;
      if (previewPaths.value[key]) continue;
      try {
        const temp = await downloadTaskAttachmentAs(attachment.contentUrl, session.accessToken);
        previewPaths.value = { ...previewPaths.value, [key]: temp };
      } catch { previewPaths.value = { ...previewPaths.value, [key]: '' }; }
    }
  }
}
function previewRecordImage(record: FamilyTaskCheckInRecord) {
  const image = record.attachments.find(a => a.contentType.startsWith('image/'));
  const path = image ? previewPaths.value[image.id] : '';
  if (path) void uni.previewImage({ urls: [path] });
}
function formatTime(value: string | null) {
  if (!value) return '';
  const normalized = value.replace('T', ' ');
  const matched = normalized.match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})/);
  if (!matched) return normalized.slice(0, 16);
  return `${Number(matched[2])}月${Number(matched[3])}日 ${matched[4]}:${matched[5]}`;
}
async function load() {
  const session = getParentSession();
  if (!session) { error.value = '请先登录家长账号'; return; }
  busy.value = true; error.value = '';
  try {
    const [detail, recordList, overviewPage] = await Promise.all([
      familyTaskApi.detail(session.accessToken, taskId.value),
      familyTaskApi.checkInRecords(session.accessToken, taskId.value).catch(() => [] as FamilyTaskCheckInRecord[]),
      familyTaskApi.listOverview(session.accessToken, 1, 'ALL', null, null)
    ]);
    task.value = detail;
    records.value = recordList;
    overview.value = overviewPage.items.find(item => item.id === taskId.value) || null;
    void loadPreviews();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '任务详情加载未能完成';
  } finally { busy.value = false; }
}
onLoad(options => { taskId.value = String(options?.id || ''); void load(); });
</script>
<style lang="scss" scoped>
@import "@/styles/workspace-prototype.scss";
.detail-page{padding:24rpx 28rpx 60rpx;background:#f4f7f5;min-height:100vh;box-sizing:border-box}
.detail-hero{display:flex;align-items:flex-start;gap:18rpx;position:relative}
.detail-icon{width:76rpx;height:76rpx;background:#e8f8ed;color:#086544;border-radius:20rpx;display:flex;align-items:center;justify-content:center;flex:none}
.detail-hero-main{flex:1;min-width:0}
.detail-title{display:block;font-size:30rpx;font-weight:700;color:#162b24}
.detail-desc{display:block;margin-top:8rpx;font-size:24rpx;color:#78847e;line-height:1.6;overflow-wrap:anywhere}
.type-badge{flex:none;background:#e8f8ed;color:#086544;font-size:22rpx;padding:6rpx 18rpx;border-radius:999rpx;font-weight:600}
.detail-table{margin-top:22rpx;padding:6rpx 26rpx!important}
.detail-row{display:flex;align-items:flex-start;gap:20rpx;padding:22rpx 0;border-bottom:1rpx solid #edf1ee}
.detail-row:last-child{border-bottom:0}
.detail-label{flex:none;width:150rpx;font-size:25rpx;color:#78847e}
.detail-value{flex:1;font-size:25rpx;color:#1c2b28;line-height:1.6;overflow-wrap:anywhere}
.progress-block{background:#fff;border:1rpx solid #e2eee7;border-radius:24rpx;padding:24rpx 26rpx;margin-top:22rpx}
.progress-head{display:flex;align-items:center;justify-content:space-between}
.progress-title{font-size:27rpx;font-weight:650;color:#162b24}
.progress-count{font-size:25rpx;color:#086544;font-weight:650}
.progress-track{height:14rpx;background:#eef3ee;border-radius:999rpx;margin-top:18rpx;overflow:hidden}
.progress-bar{height:100%;background:#3f9e63;border-radius:999rpx}
.day-dots{display:flex;gap:8rpx;margin-top:24rpx;overflow-x:auto;padding-bottom:6rpx}
.day-dot{display:flex;flex-direction:column;align-items:center;gap:8rpx;min-width:64rpx}
.dot-circle{width:56rpx;height:56rpx;border-radius:50%;border:2rpx solid #d5e0d8;display:flex;align-items:center;justify-content:center;font-size:26rpx;color:#fff;background:#fff;box-sizing:border-box}
.dot-circle.done{background:#3f9e63;border-color:#3f9e63}
.dot-circle.pending{background:#fff0d7;border-color:#eea12a;color:#eea12a}
.dot-label{font-size:21rpx;color:#78847e}
.records-block{margin-top:26rpx}
.records-empty{text-align:center;color:#819487;font-size:24rpx;padding:48rpx 24rpx!important}
.record-card{margin-bottom:20rpx}
.record-body{display:flex;gap:18rpx}
.record-thumb{width:120rpx;height:120rpx;border-radius:16rpx;background:#eef3ee;flex:none}
.record-thumb.placeholder{display:flex;align-items:center;justify-content:center;color:#9aa8a0}
.record-main{flex:1;min-width:0}
.record-top{display:flex;align-items:center;justify-content:space-between}
.record-time{font-size:24rpx;color:#78847e}
.record-status{font-size:21rpx;border-radius:10rpx;padding:4rpx 12rpx}
.record-status.approved{background:#e7f7e9;color:#168450}
.record-status.pending{background:#fff0d7;color:#eea12a}
.record-content{display:block;margin-top:10rpx;font-size:26rpx;color:#1c2b28;line-height:1.6;overflow-wrap:anywhere}
.record-student{display:block;margin-top:10rpx;font-size:23rpx;color:#78847e}
.required-label{display:flex;align-items:center;gap:4rpx;margin:6rpx 0 18rpx;font-size:27rpx;font-weight:650;color:#172b24}
</style>
