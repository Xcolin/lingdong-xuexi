<template>
  <view class="prototype-workspace page-shell">
    <PixelPageHeader title="申请任务顺延" back fallback="/pages/task-detail/task-detail" />
    <view v-if="loading" class="state-view"><text>正在加载任务</text></view>
    <view v-else-if="errorMessage && !task" class="state-view error-view">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @tap="reload">重试</button>
    </view>
    <template v-else-if="task">
      <!-- 任务顺延申请（原型 05-09）：日历图标 + 引导文案 + 信息表 + 原因 200 字 -->
      <view class="hero-symbol">
        <PrototypeIcon name="calendar" :size="46" />
      </view>
      <text class="guide-title">申请将任务顺延</text>
      <text class="guide-subtitle">如果今天确实无法完成，可以申请顺延。请说明原因，成长伙伴会进行审核。</text>
      <view class="info-table">
        <view class="info-row">
          <text class="info-label">任务名称</text>
          <text class="info-value">{{ task.title }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">原任务日期</text>
          <text class="info-value">{{ formatDate(task.scheduledDate) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">建议顺延至</text>
          <picker
            class="date-picker"
            mode="date"
            :start="minDeferDate"
            :end="maxDeferDate"
            :value="targetDate"
            :disabled="working"
            @change="onDateChange"
          >
            <view class="date-value">
              <text>{{ formatDate(targetDate) }}</text>
              <PrototypeIcon name="calendar" :size="26" />
            </view>
          </picker>
        </view>
      </view>
      <view class="note-box">
        <text class="note-heading">申请原因</text>
        <textarea
          v-model="reason"
          class="note-input"
          :maxlength="200"
          placeholder="请说明需要顺延的原因"
          :disabled="working"
        />
        <text class="note-counter">{{ reason.length }}/200</text>
      </view>
      <button
        class="primary-action"
        :loading="working"
        :disabled="working || !reason.trim()"
        @tap="submitDefer"
      >提交申请</button>
      <view v-if="errorMessage" class="inline-error"><text>{{ errorMessage }}</text></view>
    </template>
  </view>
</template>

<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref } from 'vue';
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  deferStudentTask,
  getStudentTaskAssignment,
  type StudentTaskAssignment
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
const targetDate = ref('');

const today = new Date();
// 按本地时区取日期（toISOString 为 UTC，跨午夜时会与后端 Asia/Shanghai 业务日漂移一天）。
function shiftDate(base: Date, days: number): string {
  const next = new Date(base.getTime() + days * 86400000);
  const month = String(next.getMonth() + 1).padStart(2, '0');
  const day = String(next.getDate()).padStart(2, '0');
  return `${next.getFullYear()}-${month}-${day}`;
}
// 顺延目标日期必须在未来 1 至 7 天内（与后端校验一致）。
const minDeferDate = shiftDate(today, 1);
const maxDeferDate = shiftDate(today, 7);

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
    targetDate.value = minDeferDate;
  } catch (error) {
    if (token && current(n, token)) errorMessage.value = toMessage(error);
  } finally {
    if (token && current(n, token)) loading.value = false;
  }
}

function onDateChange(event: unknown): void {
  const value = (event as { detail?: { value?: string } })?.detail?.value;
  if (value) targetDate.value = value;
}

async function submitDefer(): Promise<void> {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  working.value = true;
  errorMessage.value = '';
  try {
    await deferStudentTask(assignmentId.value, targetDate.value, reason.value.trim());
    if (!current(n, token)) return;
    uni.showToast({ title: '顺延申请已提交', icon: 'success' });
    await uni.navigateBack();
  } catch (error) {
    if (current(n, token)) {
      errorMessage.value = toMessage(error);
      working.value = false;
    }
  }
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
.hero-symbol { margin: 30rpx auto 0; width: 112rpx; height: 112rpx; display: flex; align-items: center; justify-content: center; background: #dff0f7; border-radius: 32rpx; color: #2d7fa3; }
.guide-title { display: block; margin-top: 20rpx; color: #1c2b28; font-size: 34rpx; font-weight: 650; text-align: center; }
.guide-subtitle { display: block; margin-top: 12rpx; color: #8a9981; font-size: 25rpx; text-align: center; line-height: 1.6; }
.info-table { margin-top: 32rpx; padding: 10rpx 30rpx; background: #ffffff; border-radius: 22rpx; }
.info-row { min-height: 96rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 2rpx solid #eef3f0; }
.info-row:last-child { border-bottom: 0; }
.info-label { flex-shrink: 0; color: #8a9981; font-size: 26rpx; }
.info-value { color: #1c2b28; font-size: 27rpx; font-weight: 550; text-align: right; }
.date-picker { flex-shrink: 0; }
.date-value { display: flex; align-items: center; gap: 14rpx; color: #1c2b28; font-size: 27rpx; font-weight: 550; }
.note-box { margin-top: 26rpx; padding: 24rpx; background: #ffffff; border-radius: 22rpx; }
.note-heading { display: block; color: #1c2b28; font-size: 28rpx; font-weight: 600; }
.note-input { width: 100%; min-height: 160rpx; margin-top: 16rpx; color: #1c2b28; font-size: 27rpx; line-height: 1.6; }
.note-counter { display: block; margin-top: 12rpx; text-align: right; color: #a8b5ae; font-size: 22rpx; }
.primary-action { width: 100%; height: 88rpx; margin: 40rpx 0 0; border-radius: 44rpx; background: linear-gradient(120deg, #184f40, #256b50); color: #ffffff; font-size: 29rpx; }
.primary-action::after { border: 0; }
.inline-error { margin-top: 22rpx; padding: 18rpx 24rpx; background: #fff1ed; border-radius: 14rpx; color: #9a4b38; font-size: 24rpx; }
</style>
