<template>
  <view class="ld-page has-tabbar">
    <view class="ld-heading">
      <text class="ld-heading-title">学习任务</text>
      <text class="ld-heading-sub">来自家庭、机构和教师的任务安排</text>
    </view>
    <StudentTodayTasks />
    <view v-if="capabilityLoading" class="ld-loading">正在检查功能状态</view>
    <view v-else-if="!learningTaskEnabled" class="ld-empty">
      <text class="ld-empty-main">学习任务暂不可用</text>
      <text class="ld-empty-sub">请联系管理员确认任务功能开关</text>
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

      <view v-if="errorMessage" class="ld-card error-band">
        <text class="error-text">{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view class="task-list">
        <view
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
          hover-class="task-card-hover"
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
            <text class="meta-item">{{ task.scheduledDate }}</text>
            <text class="meta-item">{{ task.difficultyLevel }} 级</text>
            <text class="meta-item points">{{ task.basePoints }} 分</text>
            <text class="meta-item">{{ task.durationMinutes }} 分钟</text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="ld-loading">正在加载</view>
      <button v-else-if="hasMore" class="ld-btn ld-btn-ghost load-more" @tap="loadNext">加载更多</button>
      <view v-else-if="tasks.length" class="list-footer">已加载全部任务</view>
      <view v-else-if="!errorMessage" class="ld-empty">
        <text class="ld-empty-main">暂无学习任务</text>
        <text class="ld-empty-sub">有新任务时会在这里出现</text>
      </view>
    </template>
    <AppTabBar :items="STUDENT_TABS" :active="0" />
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
import StudentTodayTasks from '@/components/StudentTodayTasks.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { STUDENT_TABS } from '@/config/tabbar';

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
.source-filter { margin-top: 8rpx; white-space: nowrap; }
.source-filter-inner { display: inline-flex; gap: 16rpx; padding: 8rpx 4rpx; }

.source-button {
  min-width: 128rpx;
  height: 64rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 32rpx;
  border-radius: $ld-radius-pill;
  background: #ffffff;
  color: $ld-text-secondary;
  font-size: $ld-font-caption;
  box-shadow: $ld-shadow-card;
}

.source-button::after { border: 0; }
.source-button.active { background: $ld-primary; color: #ffffff; box-shadow: $ld-shadow-btn; }

.error-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  background: $ld-danger-soft;
}
.error-text { color: $ld-danger; font-size: $ld-font-caption; flex: 1; }
.retry-button {
  width: 120rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  border-radius: $ld-radius-pill;
  background: $ld-card;
  color: $ld-danger;
  font-size: $ld-font-caption;
  flex-shrink: 0;
}
.retry-button::after { border: 0; }

.task-list { margin-top: 8rpx; }

.task-card {
  margin-top: $ld-gap-block;
  padding: 28rpx 30rpx;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
}

.task-card-hover { background: #f2f8f5; }
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
  padding: 6rpx 16rpx;
  border-radius: $ld-radius-pill;
  font-size: $ld-font-mini;
  font-weight: 600;
}

.migration-label { background: #eef1f2; color: #66716c; }

.source-family { background: #fceff3; color: #9d3658; }
.source-organization { background: #edf3ff; color: #315f9f; }
.source-teacher { background: #e9f7f5; color: #147069; }
.status-label { background: $ld-warning-soft; color: $ld-warning; }
.status-in_progress { background: #e8f6ef; color: $ld-primary; }
.status-paused { background: #eef1f5; color: #526170; }
.status-pending_review { background: #edf3ff; color: #315f9f; }
.status-needs_improvement { background: $ld-danger-soft; color: $ld-danger; }
.status-exempt { background: #f0f1f2; color: #606b66; }
.status-completed { background: #e8f6ef; color: $ld-primary; }

.task-title {
  display: block;
  margin-top: 20rpx;
  color: $ld-text;
  font-size: 31rpx;
  font-weight: 650;
  line-height: 1.4;
}

.task-meta { display: flex; flex-wrap: wrap; gap: 10rpx 24rpx; margin-top: 16rpx; color: $ld-text-muted; font-size: $ld-font-caption; }
.meta-item { display: block; }
.points { color: $ld-accent; font-weight: 600; }

.list-footer { padding: 32rpx 0; color: $ld-text-muted; font-size: $ld-font-caption; text-align: center; }
.load-more { width: 260rpx; height: 68rpx; margin: 32rpx auto 0; font-size: $ld-font-caption; }
</style>
