<template>
  <view class="page-shell">
    <view v-if="loading" class="state-view"><text>正在加载任务</text></view>
    <view v-else-if="!learningTaskEnabled" class="state-view unavailable">
      <text>学习任务暂不可用</text>
    </view>
    <view v-else-if="errorMessage" class="state-view error-view">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @tap="reload">重试</button>
    </view>
    <template v-else-if="task">
      <view class="title-band">
        <view class="title-meta">
          <text class="source-label" :class="`source-${task.sourceType.toLowerCase()}`">{{ sourceLabel(task.sourceType) }}</text>
          <text class="status-label">待认领</text>
        </view>
        <text class="task-title">{{ task.title }}</text>
        <text v-if="task.sourceOrganizationName" class="source-name">{{ task.sourceOrganizationName }}</text>
      </view>

      <view class="detail-band">
        <view class="detail-row"><text class="detail-label">计划日期</text><text>{{ task.scheduledDate }}</text></view>
        <view class="detail-row"><text class="detail-label">截止时间</text><text>{{ formatTime(task.dueAt) }}</text></view>
        <view class="detail-row"><text class="detail-label">任务难度</text><text>{{ task.difficultyLevel }} 级</text></view>
        <view class="detail-row"><text class="detail-label">基础积分</text><text>{{ task.basePoints }} 分</text></view>
        <view class="detail-row"><text class="detail-label">执行时长</text><text>{{ task.durationMinutes }} 分钟</text></view>
        <view class="detail-row"><text class="detail-label">审核人</text><text>{{ task.reviewerDisplayName }}</text></view>
        <view v-if="task.categoryCode" class="detail-row"><text class="detail-label">任务分类</text><text>{{ task.categoryCode }}</text></view>
      </view>

      <view v-if="task.tagCodes.length" class="detail-band tag-band">
        <text class="section-title">任务标签</text>
        <view class="tag-list"><text v-for="tag in task.tagCodes" :key="tag" class="task-tag">{{ tag }}</text></view>
      </view>

      <view v-if="task.remark" class="detail-band">
        <text class="section-title">任务备注</text>
        <text class="remark-text">{{ task.remark }}</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  getStudentTaskAssignment,
  type LearningTaskSourceType,
  type StudentTaskAssignment
} from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';

const assignmentId = ref('');
const task = ref<StudentTaskAssignment | null>(null);
const loading = ref(true);
const learningTaskEnabled = ref(false);
const errorMessage = ref('');

onLoad((options) => {
  assignmentId.value = typeof options?.id === 'string' ? options.id : '';
});

onShow(() => {
  if (assignmentId.value) void initialize();
});

async function initialize(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  task.value = null;
  if (!getStudentSession()) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
    if (learningTaskEnabled.value) {
      task.value = await getStudentTaskAssignment(assignmentId.value);
    }
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    loading.value = false;
  }
}

function reload(): void {
  void initialize();
}

function sourceLabel(source: LearningTaskSourceType): string {
  return source === 'FAMILY' ? '家庭任务' : source === 'ORGANIZATION' ? '机构任务' : '教师任务';
}

function formatTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day} ${hour}:${minute}`;
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding-bottom: 48rpx; background: #f4f7f5; }

/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */

.title-band { padding: 40rpx 36rpx 34rpx; background: #ffffff; border-bottom: 2rpx solid #dce4e1; }
.title-meta { display: flex; align-items: center; justify-content: space-between; }
.source-label, .status-label, .task-tag { padding: 6rpx 12rpx; border-radius: 6rpx; font-size: 22rpx; }
.source-family { background: #fceff3; color: #9d3658; }
.source-organization { background: #edf3ff; color: #315f9f; }
.source-teacher { background: #e9f7f5; color: #147069; }
.status-label { background: #fff5de; color: #8b6417; }
.task-title { display: block; margin-top: 24rpx; color: #1c2b28; font-size: 40rpx; font-weight: 700; line-height: 1.45; }
.source-name { display: block; margin-top: 14rpx; color: #708078; font-size: 24rpx; }

.detail-band { margin-top: 20rpx; padding: 12rpx 36rpx; background: #ffffff; border-top: 2rpx solid #e1e9e5; border-bottom: 2rpx solid #e1e9e5; }
.detail-row { min-height: 86rpx; display: flex; align-items: center; justify-content: space-between; gap: 32rpx; border-bottom: 2rpx solid #edf1ef; color: #1c2b28; font-size: 28rpx; }
.detail-row:last-child { border-bottom: 0; }
.detail-label { color: #708078; }
.section-title { display: block; padding: 20rpx 0 8rpx; color: #536760; font-size: 26rpx; font-weight: 650; }
.tag-band { padding-bottom: 28rpx; }
.tag-list { display: flex; flex-wrap: wrap; gap: 12rpx; padding-top: 12rpx; }
.task-tag { background: #edf2f0; color: #536760; }
.remark-text { display: block; padding: 12rpx 0 28rpx; color: #1c2b28; font-size: 28rpx; line-height: 1.75; white-space: pre-wrap; }

.state-view { min-height: 520rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 28rpx; color: #708078; font-size: 28rpx; }
.unavailable { color: #9a4b38; }
.error-view { padding: 0 48rpx; text-align: center; color: #9a4b38; }
.retry-button { width: 180rpx; height: 64rpx; margin: 0; border-radius: 8rpx; background: #ffffff; color: #167c5a; font-size: 26rpx; }
.retry-button::after { border-color: #a9cbbf; }
</style>
