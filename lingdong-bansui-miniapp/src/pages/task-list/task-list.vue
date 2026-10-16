<template>
  <view class="prototype-workspace ld-page has-tabbar">
    <PixelPageHeader title="我的任务" />
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
          <view class="task-symbol"><PrototypeIcon name="book" :size="26" /></view>
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
    <AppTabBar :items="STUDENT_TABS" :active="1" />
  </view>
</template>

<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onPullDownRefresh, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  listStudentTaskAssignments,
  type LearningTaskSourceType,
  type TaskAssignmentEffectiveStatus,
  type StudentTaskAssignment
} from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';
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

let epoch = 0;
const current = (n: number, token: string) => n === epoch && getStudentSession()?.accessToken === token;
function invalidate() { epoch++; tasks.value = []; total.value = 0; loading.value = false; learningTaskEnabled.value = false; errorMessage.value = ''; }
onHide(invalidate);
onUnload(invalidate);

onShow(() => {
  void initialize();
});

onPullDownRefresh(async () => {
  await initialize();
  uni.stopPullDownRefresh();
});

async function initialize(): Promise<void> {
  invalidate();
  const n = epoch, token = getStudentSession()?.accessToken;
  capabilityLoading.value = true;
  errorMessage.value = '';
  if (!token) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    if (!current(n, token)) return;
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
    if (learningTaskEnabled.value) {
      capabilityLoading.value = false;
      await loadTasks(true);
    } else {
      tasks.value = [];
      total.value = 0;
    }
  } catch (error) {
    if (!token || !current(n, token)) return;
    learningTaskEnabled.value = false;
    errorMessage.value = toMessage(error);
  } finally {
    if (token && current(n, token)) capabilityLoading.value = false;
  }
}

async function loadTasks(reset: boolean): Promise<void> {
  if (loading.value && !reset) return;
  const token = getStudentSession()?.accessToken;
  if (!token) return;
  const n = ++epoch;
  loading.value = true;
  errorMessage.value = '';
  const nextPage = reset ? 1 : page.value + 1;
  try {
    const result = await listStudentTaskAssignments({
      sourceType: selectedSource.value,
      page: nextPage,
      pageSize: PAGE_SIZE
    });
    if (!current(n, token)) return;
    tasks.value = reset ? result.items : [...tasks.value, ...result.items];
    page.value = result.page;
    total.value = result.total;
  } catch (error) {
    if (current(n, token)) errorMessage.value = toMessage(error);
  } finally {
    if (current(n, token)) loading.value = false;
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
@import "@/styles/workspace-prototype.scss";

.source-filter { margin-top: 8rpx; white-space: nowrap; }
.source-filter-inner { padding: 8rpx 4rpx; }

.source-button {
  height: 64rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  border-radius: $ld-radius-pill;
  color: $ld-text-secondary;
  font-size: $ld-font-caption;
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
  line-height: 1.4;
}

.task-meta { display: flex; flex-wrap: wrap; gap: 10rpx 24rpx; margin-top: 16rpx; color: $ld-text-muted; }
.meta-item { display: block; }
.points { color: $ld-accent; font-weight: 600; }

.list-footer { padding: 32rpx 0; color: $ld-text-muted; font-size: $ld-font-caption; text-align: center; }
.load-more { width: 260rpx; height: 68rpx; margin: 32rpx auto 0; font-size: $ld-font-caption; }



.prototype-workspace{background:#f4f8f3;}
.profile-hero{display:flex;align-items:center;gap:24rpx;padding:44rpx 18rpx;}
.task-card{position:relative;padding:24rpx 24rpx 24rpx 108rpx!important;border:0!important;border-radius:24rpx!important;background:#fff!important;margin-bottom:16rpx!important;box-shadow:none!important;}
.task-symbol{position:absolute;left:22rpx;top:30rpx;width:66rpx;height:66rpx;display:flex;align-items:center;justify-content:center;background:#edf6ed;border-radius:16rpx;color:#126044;}
.task-title{font-weight:650;}
.source-button{border-radius:40rpx!important;}
.balance-band,.account-band,.summary-band{background:linear-gradient(125deg,#d9ebd5,#f2f7e9)!important;border-radius:28rpx!important;border:0!important;padding:32rpx!important;}
.balance-band{position:relative;}
.balance-value{font-size:56rpx!important;color:#12533d!important;}
.reward-item,.ledger-card,.detail-band{border:0!important;border-radius:24rpx!important;background:#fff!important;margin-bottom:18rpx!important;box-shadow:none!important;}
.title-band{background:#fff!important;border-radius:26rpx!important;padding:28rpx!important;}
.detail-symbol{width:76rpx;height:76rpx;background:#e5f3ed;border-radius:20rpx;display:flex;align-items:center;justify-content:center;color:#0e6547;margin-bottom:18rpx;}

.prototype-workspace{background:#f7faf6!important;color:#172b25}.task-symbol{border-radius:50%!important;background:#e7f6ed!important}.task-card,.task-list-card{border:0!important;border-radius:22rpx!important;box-shadow:none!important}.task-title{font-size:30rpx!important}.primary-action{border-radius:44rpx!important}.prototype-header{background:#fff!important;color:#172b25!important;justify-content:center;border-radius:0!important;padding:28rpx!important}.prototype-header-title{font-size:34rpx}.publish-actions{position:fixed!important;bottom:0;left:0;right:0;padding:20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));box-sizing:border-box}.page>.task-editor{padding-bottom:180rpx!important}.task-list-card>.heading{display:block;margin-bottom:12rpx}.task-list-card>text:not(.heading){font-size:24rpx;color:#7c8981}.task-list-card button{display:inline-flex;margin:18rpx 12rpx 0 0;line-height:58rpx;padding:0 22rpx}.page-shell{padding:24rpx 28rpx 170rpx!important;box-sizing:border-box}.metadata-band .detail-row{min-height:0}.detail-symbol{background:#fff0d7!important;color:#eea12a!important}.action-band{border:0!important}.checkin-input{height:180rpx}.ld-heading{text-align:center;background:white;margin:0 -32rpx;padding:28rpx}.source-filter-inner{display:flex;width:100%;gap:0;background:#eff3ee;border-radius:24rpx}.source-button{flex:1;min-width:0;padding:0;box-shadow:none;background:transparent}.source-button.active{background:transparent!important;color:#075e43!important;border-bottom:4rpx solid #075e43}.task-row-top{justify-content:space-between}.task-meta{font-size:23rpx}.task-list-card{min-height:148rpx}
</style>
