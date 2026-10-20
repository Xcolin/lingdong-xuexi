<template>
  <view class="prototype-workspace page-shell">
    <PixelPageHeader title="放弃任务" back fallback="/pages/task-detail/task-detail" />
    <view v-if="loading" class="state-view"><text>正在加载任务</text></view>
    <view v-else-if="errorMessage && !task" class="state-view error-view">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @tap="reload">重试</button>
    </view>
    <template v-else-if="task">
      <!-- 放弃任务确认（原型 05-10）：警示图标 + 双类型选择 + 原因 200 字 + 双按钮 -->
      <view class="warn-symbol"><text>!</text></view>
      <text class="guide-title">确认放弃或申请免执行？</text>
      <text class="guide-subtitle">放弃后本次任务将不再进行。请确认是否继续。</text>
      <view class="info-table">
        <view class="info-row">
          <text class="info-label">任务名称</text>
          <text class="info-value">{{ task.title }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">任务来源</text>
          <text class="info-value">{{ sourceLabel(task.sourceType) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">任务日期</text>
          <text class="info-value">{{ formatDate(task.scheduledDate) }}</text>
        </view>
        <view class="info-row type-row">
          <text class="info-label">放弃类型</text>
          <view class="type-options">
            <label class="type-item" @tap="abandonType = 'NORMAL'">
              <view class="radio-dot" :class="{ on: abandonType === 'NORMAL' }"/>
              <text class="type-text">放弃本次任务</text>
            </label>
            <label class="type-item" @tap="abandonType = 'EXEMPT_REQUEST'">
              <view class="radio-dot" :class="{ on: abandonType === 'EXEMPT_REQUEST' }"/>
              <text class="type-text">申请免执行（特殊情况）</text>
            </label>
          </view>
        </view>
      </view>
      <view class="note-box">
        <text class="note-heading">说明原因（选填）</text>
        <textarea
          v-model="reason"
          class="note-input"
          :maxlength="200"
          placeholder="说一说放弃或申请免执行的原因"
          :disabled="working"
        />
        <text class="note-counter">{{ reason.length }}/200</text>
      </view>
      <button
        class="danger-action"
        :loading="working"
        :disabled="working"
        @tap="confirmAbandon"
      >确认放弃</button>
      <button class="ghost-action" :disabled="working" @tap="goBack">我再想想</button>
      <view v-if="errorMessage" class="inline-error"><text>{{ errorMessage }}</text></view>
    </template>
  </view>
</template>

<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import { ref } from 'vue';
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  abandonStudentTask,
  getStudentTaskAssignment,
  type LearningTaskSourceType,
  type StudentTaskAssignment,
  type TaskAbandonType
} from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

const assignmentId = ref('');
const task = ref<StudentTaskAssignment | null>(null);
const loading = ref(true);
const working = ref(false);
const learningTaskEnabled = ref(false);
const errorMessage = ref('');
const reason = ref('');
const abandonType = ref<TaskAbandonType>('NORMAL');

let epoch = 0;
const current = (n: number, token: string) => n === epoch && getStudentSession()?.accessToken === token;
function invalidate() {
  epoch++;
  task.value = null;
  working.value = false;
  learningTaskEnabled.value = false;
  errorMessage.value = '';
}
onUnload(invalidate);

onLoad((options) => {
  assignmentId.value = typeof options?.id === 'string' ? options.id : '';
});

onShow(() => {
  if (assignmentId.value) void initialize();
});

async function initialize(): Promise<void> {
  invalidate();
  const n = epoch, token = getStudentSession()?.accessToken;
  loading.value = true;
  errorMessage.value = '';
  if (!token) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    if (!current(n, token)) return;
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
    const result = learningTaskEnabled.value ? await getStudentTaskAssignment(assignmentId.value) : null;
    if (!current(n, token)) return;
    task.value = result;
  } catch (error) {
    if (token && current(n, token)) errorMessage.value = toMessage(error);
  } finally {
    if (token && current(n, token)) loading.value = false;
  }
}

async function confirmAbandon(): Promise<void> {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  working.value = true;
  errorMessage.value = '';
  try {
    await abandonStudentTask(assignmentId.value, reason.value.trim() || undefined, abandonType.value);
    if (!current(n, token)) return;
    uni.showToast({ title: abandonType.value === 'EXEMPT_REQUEST' ? '免执行申请已提交' : '任务已放弃', icon: 'success' });
    await uni.navigateBack();
  } catch (error) {
    if (current(n, token)) {
      errorMessage.value = toMessage(error);
      working.value = false;
    }
  }
}

function goBack(): void {
  if (!working.value) void uni.navigateBack();
}

function sourceLabel(source: LearningTaskSourceType): string {
  return source === 'FAMILY' ? '家庭任务' : source === 'ORGANIZATION' ? '机构任务' : '教师任务';
}

function formatDate(value: string): string {
  if (!value) return '';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAY_LABELS[date.getDay()]}`;
}

function reload(): void {
  void initialize();
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
</script>
<style lang="scss" scoped>
@import "@/styles/workspace-prototype.scss";

.page-shell { padding: 0 28rpx 48rpx; box-sizing: border-box; background: #f7faf6; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.state-view { min-height: 520rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 28rpx; color: #708078; font-size: 28rpx; }
.error-view { color: #9a4b38; padding: 0 48rpx; text-align: center; }
.retry-button { width: 180rpx; height: 64rpx; margin: 0; background: #ffffff; color: #167c5a; }
.retry-button::after { border-color: #a9cbbf; }
.warn-symbol { margin: 30rpx auto 0; width: 108rpx; height: 108rpx; display: flex; align-items: center; justify-content: center; background: linear-gradient(150deg, #f2b93c, #e79a1f); border-radius: 50%; }
.warn-symbol text { color: #ffffff; font-size: 60rpx; font-weight: 700; line-height: 1; }
.guide-title { display: block; margin-top: 22rpx; color: #1c2b28; font-size: 34rpx; font-weight: 650; text-align: center; }
.guide-subtitle { display: block; margin-top: 12rpx; color: #8a9981; font-size: 25rpx; text-align: center; line-height: 1.6; }
.info-table { margin-top: 32rpx; padding: 10rpx 30rpx; background: #ffffff; border-radius: 22rpx; }
.info-row { min-height: 96rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 2rpx solid #eef3f0; }
.type-row { flex-direction: column; align-items: flex-start; padding: 22rpx 0; gap: 18rpx; }
.type-options { width: 100%; display: flex; flex-direction: column; gap: 18rpx; }
.type-item { display: flex; align-items: center; gap: 18rpx; }
.radio-dot { width: 34rpx; height: 34rpx; flex-shrink: 0; border-radius: 50%; border: 4rpx solid #c4d2cb; background: #ffffff; box-sizing: border-box; }
.radio-dot.on { border-width: 10rpx; border-color: #167c5a; }
.type-text { color: #1c2b28; font-size: 27rpx; }
.info-label { flex-shrink: 0; color: #8a9981; font-size: 26rpx; }
.info-value { color: #1c2b28; font-size: 27rpx; font-weight: 550; text-align: right; }
.note-box { margin-top: 26rpx; padding: 24rpx; background: #ffffff; border-radius: 22rpx; }
.note-heading { display: block; color: #1c2b28; font-size: 28rpx; font-weight: 600; }
.note-input { width: 100%; min-height: 160rpx; margin-top: 16rpx; color: #1c2b28; font-size: 27rpx; line-height: 1.6; }
.note-counter { display: block; margin-top: 12rpx; text-align: right; color: #a8b5ae; font-size: 22rpx; }
.danger-action { width: 100%; height: 88rpx; margin: 40rpx 0 0; border-radius: 44rpx; background: linear-gradient(120deg, #e79a1f, #f2b93c); color: #ffffff; font-size: 29rpx; }
.danger-action::after { border: 0; }
.ghost-action { width: 100%; height: 88rpx; margin: 22rpx 0 0; border-radius: 44rpx; background: #ffffff; color: #167c5a; font-size: 29rpx; }
.ghost-action::after { border-color: #a9cbbf; }
.inline-error { margin-top: 22rpx; padding: 18rpx 24rpx; background: #fff1ed; border-radius: 14rpx; color: #9a4b38; font-size: 24rpx; }
</style>
