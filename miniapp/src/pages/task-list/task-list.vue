<template>
  <view class="page-shell">
    <view v-if="capabilityLoading" class="state-view">
      <text>正在检查功能状态</text>
    </view>
    <view v-else-if="!learningTaskEnabled" class="state-view unavailable">
      <text class="state-title">学习任务暂不可用</text>
    </view>
    <template v-else>
      <scroll-view class="source-filter" scroll-x :show-scrollbar="false">
        <view class="source-filter-inner">
          <button
            v-for="option in sourceOptions"
            :key="option.value || 'ALL'"
            class="source-button"
            :class="{ active: selectedSource === option.value }"
            @tap="selectSource(option.value)"
          >{{ option.label }}</button>
        </view>
      </scroll-view>

      <view v-if="errorMessage" class="error-band">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view class="task-list">
        <view
          v-for="task in tasks"
          :key="task.id"
          class="task-row"
          hover-class="task-row-hover"
          @tap="openDetail(task.id)"
        >
          <view class="task-row-top">
            <text class="source-label" :class="`source-${task.sourceType.toLowerCase()}`">
              {{ sourceLabel(task.sourceType) }}
            </text>
            <text class="status-label" :class="`status-${task.effectiveStatus.toLowerCase()}`">
              {{ statusLabel(task.effectiveStatus) }}
            </text>
            <text v-if="task.overnightMigrated" class="migration-label">隔夜迁移</text>
          </view>
          <text class="task-title">{{ task.title }}</text>
          <view class="task-meta">
            <text>{{ task.scheduledDate }}</text>
            <text>{{ task.difficultyLevel }} 级</text>
            <text>{{ task.basePoints }} 分</text>
            <text>{{ task.durationMinutes }} 分钟</text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="list-footer">正在加载</view>
      <button v-else-if="hasMore" class="load-more" @tap="loadNext">加载更多</button>
      <view v-else-if="tasks.length" class="list-footer">已加载全部任务</view>
      <view v-else-if="!errorMessage" class="state-view compact">
        <text class="state-title">暂无学习任务</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  listStudentTaskAssignments,
  type LearningTaskSourceType,
  type TaskAssignmentEffectiveStatus,
  type StudentTaskAssignment
} from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';

const PAGE_SIZE = 20;
const capabilityLoading = ref(true);
const learningTaskEnabled = ref(false);
const loading = ref(false);
const errorMessage = ref('');
const selectedSource = ref<LearningTaskSourceType | undefined>(undefined);
const tasks = ref<StudentTaskAssignment[]>([]);
const page = ref(1);
const total = ref(0);

const sourceOptions: Array<{ value: LearningTaskSourceType | undefined; label: string }> = [
  { value: undefined, label: '全部' },
  { value: 'FAMILY', label: '家庭' },
  { value: 'ORGANIZATION', label: '机构' },
  { value: 'TEACHER', label: '教师' }
];

const hasMore = computed(() => tasks.value.length < total.value);

onShow(() => {
  void initialize();
});

onPullDownRefresh(async () => {
  await initialize();
  uni.stopPullDownRefresh();
});

async function initialize(): Promise<void> {
  capabilityLoading.value = true;
  errorMessage.value = '';
  if (!getStudentSession()) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
    if (learningTaskEnabled.value) {
      await loadTasks(true);
    } else {
      tasks.value = [];
      total.value = 0;
    }
  } catch (error) {
    learningTaskEnabled.value = false;
    errorMessage.value = toMessage(error);
  } finally {
    capabilityLoading.value = false;
  }
}

async function loadTasks(reset: boolean): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  errorMessage.value = '';
  const nextPage = reset ? 1 : page.value + 1;
  try {
    const result = await listStudentTaskAssignments({
      sourceType: selectedSource.value,
      page: nextPage,
      pageSize: PAGE_SIZE
    });
    tasks.value = reset ? result.items : [...tasks.value, ...result.items];
    page.value = result.page;
    total.value = result.total;
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    loading.value = false;
  }
}

function selectSource(source?: LearningTaskSourceType): void {
  if (selectedSource.value === source) return;
  selectedSource.value = source;
  void loadTasks(true);
}

function reload(): void {
  void initialize();
}

function loadNext(): void {
  void loadTasks(false);
}

function openDetail(id: string): void {
  uni.navigateTo({ url: `/pages/task-detail/task-detail?id=${encodeURIComponent(id)}` });
}

function sourceLabel(source: LearningTaskSourceType): string {
  return source === 'FAMILY' ? '家庭' : source === 'ORGANIZATION' ? '机构' : '教师';
}

function statusLabel(status: TaskAssignmentEffectiveStatus): string {
  const labels: Record<TaskAssignmentEffectiveStatus, string> = {
    PENDING_CLAIM: '待认领',
    IN_PROGRESS: '进行中',
    PAUSED: '已暂停',
    PENDING_REVIEW: '待审核',
    NEEDS_IMPROVEMENT: '待优化',
    EXEMPT: '免执行',
    COMPLETED: '已完成'
  };
  return labels[status];
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

.source-filter {
  width: 100%;
  padding: 24rpx 0;
  box-sizing: border-box;
  background: #ffffff;
  border-bottom: 2rpx solid #dce4e1;
  white-space: nowrap;
}

.source-filter-inner { display: inline-flex; gap: 12rpx; padding: 0 32rpx; }

.source-button {
  min-width: 128rpx;
  height: 64rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 28rpx;
  border-radius: 8rpx;
  background: #edf2f0;
  color: #536760;
  font-size: 26rpx;
}

.source-button::after { border: 0; }
.source-button.active { background: #167c5a; color: #ffffff; }

.task-list { background: #ffffff; }

.task-row {
  min-height: 194rpx;
  padding: 28rpx 36rpx;
  box-sizing: border-box;
  border-bottom: 2rpx solid #e5ece8;
}

.task-row-hover { background: #f1f6f3; }
.task-row-top {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 12rpx;
}

.source-label,
.status-label,
.migration-label {
  padding: 6rpx 12rpx;
  border-radius: 6rpx;
  font-size: 22rpx;
}

.migration-label { background: #eef1f2; color: #66716c; }

.source-family { background: #fceff3; color: #9d3658; }
.source-organization { background: #edf3ff; color: #315f9f; }
.source-teacher { background: #e9f7f5; color: #147069; }
.status-label { background: #fff5de; color: #8b6417; }
.status-in_progress { background: #e8f6ef; color: #167c5a; }
.status-paused { background: #eef1f5; color: #526170; }
.status-pending_review { background: #edf3ff; color: #315f9f; }
.status-needs_improvement { background: #fff0e8; color: #9a4b38; }
.status-exempt { background: #f0f1f2; color: #606b66; }
.status-completed { background: #e8f6ef; color: #167c5a; }

.task-title {
  display: block;
  margin-top: 18rpx;
  color: #1c2b28;
  font-size: 32rpx;
  font-weight: 650;
  line-height: 1.4;
}

.task-meta { display: flex; flex-wrap: wrap; gap: 10rpx 24rpx; margin-top: 14rpx; color: #708078; font-size: 24rpx; }

.list-footer,
.state-view {
  min-height: 220rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #708078;
  font-size: 26rpx;
}

.state-view.compact { min-height: 300rpx; }
.state-title { color: #536760; font-size: 30rpx; }
.unavailable .state-title { color: #9a4b38; }

.error-band { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 20rpx 32rpx; background: #fff1ed; color: #9a4b38; font-size: 24rpx; }

.retry-button,
.load-more { border-radius: 8rpx; font-size: 26rpx; }
.retry-button { width: 120rpx; height: 56rpx; margin: 0; color: #9a4b38; background: #ffffff; }
.retry-button::after { border-color: #e8b4a8; }
.load-more { width: 240rpx; height: 68rpx; margin: 32rpx auto 0; color: #167c5a; background: #ffffff; }
.load-more::after { border-color: #a9cbbf; }
</style>
