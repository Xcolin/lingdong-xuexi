<template>
  <view class="prototype-workspace page-shell">
    <PixelPageHeader title="确认认领" back fallback="/pages/task-detail/task-detail" />
    <view v-if="loading" class="state-view"><text>正在加载任务</text></view>
    <view v-else-if="errorMessage && !task" class="state-view error-view">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @tap="reload">重试</button>
    </view>
    <template v-else-if="task">
      <view class="confirm-card">
        <view class="confirm-symbol"><PrototypeIcon name="task" :size="40"/></view>
        <text class="confirm-title">确认认领该任务?</text>
        <text class="confirm-hint">认领后会加入到今日任务中</text>
        <view class="confirm-meta">
          <view class="meta-row"><text class="meta-label">任务名称</text><text class="meta-value">{{ task.title }}</text></view>
          <view class="meta-row"><text class="meta-label">来源</text><text class="meta-value">{{ sourceLabel(task.sourceType) }}<text v-if="task.sourceOrganizationName" class="meta-sub">（{{ task.sourceOrganizationName }}）</text></text></view>
          <view class="meta-row"><text class="meta-label">日期</text><text class="meta-value">{{ task.scheduledDate }}</text></view>
          <view class="meta-row"><text class="meta-label">分类</text><text class="meta-value">{{ task.categoryCode || '未分类' }}</text></view>
        </view>
        <button
          class="confirm-action"
          :loading="working"
          :disabled="working"
          @tap="confirmClaim"
        >确认认领</button>
        <button class="look-action" :disabled="working" @tap="lookBack">我再看看</button>
      </view>
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
  claimStudentTask,
  getStudentTaskAssignment,
  type LearningTaskSourceType,
  type StudentTaskAssignment
} from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';

const assignmentId = ref('');
const task = ref<StudentTaskAssignment | null>(null);
const loading = ref(true);
const working = ref(false);
const learningTaskEnabled = ref(false);
const errorMessage = ref('');

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

async function confirmClaim(): Promise<void> {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  working.value = true;
  errorMessage.value = '';
  try {
    await claimStudentTask(assignmentId.value);
    if (!current(n, token)) return;
    await uni.redirectTo({ url: '/pages/task-detail/task-detail?id=' + assignmentId.value });
  } catch (error) {
    if (current(n, token)) {
      errorMessage.value = toMessage(error);
      working.value = false;
    }
  }
}

function lookBack(): void {
  if (working.value) return;
  uni.navigateBack();
}

function reload(): void {
  void initialize();
}

function sourceLabel(source: LearningTaskSourceType): string {
  return source === 'FAMILY' ? '家庭任务' : source === 'ORGANIZATION' ? '机构任务' : '教师任务';
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
</script>
<style lang="scss" scoped>
@import "@/styles/workspace-prototype.scss";

.page-shell { padding-bottom: 48rpx; background: #f7faf6; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.state-view { min-height: 520rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 28rpx; color: #708078; font-size: 28rpx; }
.error-view { color: #9a4b38; padding: 0 48rpx; text-align: center; }
.retry-button { width: 180rpx; height: 64rpx; margin: 0; background: #ffffff; color: #167c5a; }
.retry-button::after { border-color: #a9cbbf; }
.confirm-card { margin: 24rpx 28rpx 0; padding: 44rpx 36rpx 36rpx; background: #ffffff; border-radius: 26rpx; display: flex; flex-direction: column; align-items: center; }
.confirm-symbol { width: 88rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; background: #fff0d7; border-radius: 50%; color: #eea12a; }
.confirm-title { margin-top: 26rpx; color: #1c2b28; font-size: 36rpx; font-weight: 650; }
.confirm-hint { margin-top: 12rpx; color: #8a9981; font-size: 25rpx; }
.confirm-meta { width: 100%; margin-top: 34rpx; background: #f6faf7; border-radius: 18rpx; padding: 8rpx 26rpx; box-sizing: border-box; }
.meta-row { min-height: 84rpx; display: flex; align-items: center; justify-content: space-between; gap: 28rpx; border-bottom: 2rpx solid #e7efe9; font-size: 27rpx; }
.meta-row:last-child { border-bottom: 0; }
.meta-label { color: #8a9981; flex-shrink: 0; }
.meta-value { color: #1c2b28; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta-sub { color: #708078; font-size: 23rpx; }
.confirm-action { width: 100%; height: 88rpx; margin: 44rpx 0 0; border-radius: 44rpx; background: linear-gradient(120deg, #184f40, #256b50); color: #ffffff; font-size: 29rpx; }
.confirm-action::after { border: 0; }
.look-action { width: 100%; height: 80rpx; margin: 20rpx 0 0; border-radius: 44rpx; background: #edf6ee; color: #185b41; font-size: 28rpx; }
.look-action::after { border-color: #c0d2ca; }
</style>
