<template>
  <view class="page-shell">
    <view v-if="loading" class="state-view"><text>正在加载任务</text></view>
    <view v-else-if="!learningTaskEnabled" class="state-view unavailable">
      <text>学习任务暂不可用</text>
    </view>
    <view v-else-if="errorMessage && !task" class="state-view error-view">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @tap="reload">重试</button>
    </view>
    <template v-else-if="task">
      <view class="title-band">
        <view class="title-meta">
          <text class="source-label" :class="`source-${task.sourceType.toLowerCase()}`">
            {{ sourceLabel(task.sourceType) }}
          </text>
          <text class="status-label" :class="`status-${task.effectiveStatus.toLowerCase()}`">
            {{ statusLabel(task.effectiveStatus) }}
          </text>
          <text v-if="task.overnightMigrated" class="migration-label">隔夜迁移</text>
        </view>
        <text class="task-title">{{ task.title }}</text>
        <text v-if="task.sourceOrganizationName" class="source-name">
          {{ task.sourceOrganizationName }}
        </text>
      </view>

      <view v-if="errorMessage" class="error-band"><text>{{ errorMessage }}</text></view>

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

      <view v-if="task.latestCheckIn" class="detail-band checkin-history">
        <view class="section-heading">
          <text class="section-title">最近打卡</text>
          <text class="submission-number">第 {{ task.latestCheckIn.submissionNo }} 次</text>
        </view>
        <text v-if="task.latestCheckIn.content" class="remark-text">{{ task.latestCheckIn.content }}</text>
        <view v-if="task.latestCheckIn.attachments.length" class="history-image-grid">
          <view
            v-for="(attachment, index) in task.latestCheckIn.attachments"
            :key="attachment.id"
            class="history-image-item"
            @tap="previewHistoryImage(index)"
          >
            <image
              v-if="historyPreviewPaths[attachment.id]"
              :src="historyPreviewPaths[attachment.id]"
              mode="aspectFill"
            />
            <text v-else>{{ attachment.originalName }}</text>
          </view>
        </view>
        <text v-if="task.latestCheckIn.reviewComment" class="review-comment">
          审核意见：{{ task.latestCheckIn.reviewComment }}
        </text>
      </view>

      <view class="action-band">
        <button
          v-if="task.effectiveStatus === 'PENDING_CLAIM'"
          class="primary-action"
          :loading="working"
          :disabled="working"
          @tap="claimTask"
        >认领任务</button>

        <template v-else-if="task.effectiveStatus === 'IN_PROGRESS'">
          <textarea
            v-model="checkInContent"
            class="checkin-input"
            maxlength="1000"
            placeholder="记录本次完成情况"
            :disabled="working"
          />
          <view class="upload-section">
            <view class="pending-image-grid">
              <view v-for="item in pendingImages" :key="item.localId" class="pending-image-item">
                <image :src="item.filePath" mode="aspectFill" @tap="previewPendingImage(item.filePath)" />
                <view v-if="item.status === 'UPLOADING'" class="upload-mask">
                  <text>{{ item.progress }}%</text>
                </view>
                <view v-else-if="item.status === 'FAILED'" class="upload-mask failed">
                  <button @tap.stop="retryUpload(item)">重试</button>
                </view>
                <button
                  class="remove-image"
                  :disabled="working || item.status === 'UPLOADING'"
                  aria-label="移除图片"
                  @tap.stop="removeImage(item)"
                >×</button>
              </view>
              <button
                v-if="pendingImages.length < 9"
                class="add-image"
                :disabled="working"
                @tap="chooseImages"
              >添加图片</button>
            </view>
            <text class="upload-hint">支持 JPG、PNG，最多 9 张，单张不超过 10MB</text>
          </view>
          <view class="input-counter">{{ checkInContent.length }}/1000</view>
          <button
            class="primary-action"
            :loading="working"
            :disabled="working || hasUploading || (!checkInContent.trim() && !uploadedFileIds.length)"
            @tap="submitCheckIn"
          >提交打卡</button>
          <picker
            mode="selector"
            :range="pauseDurations"
            range-key="label"
            :value="pauseDurationIndex"
            @change="onPauseDurationChange"
          >
            <view class="duration-picker">暂停时长：{{ selectedPauseDuration.label }}</view>
          </picker>
          <view class="secondary-actions">
            <button :disabled="working" @tap="pauseTask('EMOTION')">情绪暂停</button>
            <button :disabled="working" @tap="pauseTask('DIFFICULTY')">难题搁置</button>
          </view>
          <button class="danger-action" :disabled="working" @tap="confirmAbandon">放弃任务</button>
        </template>

        <template v-else-if="task.effectiveStatus === 'PAUSED'">
          <view v-if="task.activePause" class="pause-summary">
            <text>{{ pauseTypeLabel(task.activePause.pauseType) }}</text>
            <text>至 {{ formatTime(task.activePause.expiresAt) }}</text>
          </view>
          <button
            class="primary-action"
            :loading="working"
            :disabled="working"
            @tap="resumeTask"
          >继续任务</button>
        </template>

        <view v-else class="terminal-state">{{ terminalStateText(task.effectiveStatus) }}</view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  abandonStudentTask,
  claimStudentTask,
  getStudentTaskAssignment,
  pauseStudentTask,
  resumeStudentTask,
  submitStudentTaskCheckIn,
  type LearningTaskSourceType,
  type StudentTaskAssignment,
  type TaskAssignmentEffectiveStatus,
  type TaskPauseType
} from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';
import {
  deleteTaskAttachment,
  downloadTaskAttachment,
  uploadTaskAttachment
} from '@/api/attachment';

interface PendingImage {
  localId: string;
  filePath: string;
  fileId: string | null;
  progress: number;
  status: 'UPLOADING' | 'UPLOADED' | 'FAILED';
}

const pauseDurations = [
  { label: '30 分钟', value: 30 },
  { label: '60 分钟', value: 60 },
  { label: '120 分钟', value: 120 }
];
const assignmentId = ref('');
const task = ref<StudentTaskAssignment | null>(null);
const loading = ref(true);
const working = ref(false);
const learningTaskEnabled = ref(false);
const errorMessage = ref('');
const checkInContent = ref('');
const pendingImages = ref<PendingImage[]>([]);
const historyPreviewPaths = ref<Record<string, string>>({});
const pauseDurationIndex = ref(0);
const selectedPauseDuration = computed(() => pauseDurations[pauseDurationIndex.value]);
const uploadedFileIds = computed(() => pendingImages.value
  .filter((item) => item.status === 'UPLOADED' && item.fileId)
  .map((item) => item.fileId as string));
const hasUploading = computed(() => pendingImages.value.some((item) => item.status === 'UPLOADING'));

onLoad((options) => {
  assignmentId.value = typeof options?.id === 'string' ? options.id : '';
});

onShow(() => {
  if (assignmentId.value) void initialize();
});

async function initialize(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  if (!getStudentSession()) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    learningTaskEnabled.value = capabilities.learningTaskManagementEnabled;
    task.value = learningTaskEnabled.value
      ? await getStudentTaskAssignment(assignmentId.value)
      : null;
    await prepareHistoryPreviews();
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    loading.value = false;
  }
}

function claimTask(): void {
  void execute(() => claimStudentTask(assignmentId.value));
}

function pauseTask(pauseType: TaskPauseType): void {
  void execute(() => pauseStudentTask(
    assignmentId.value,
    pauseType,
    selectedPauseDuration.value.value
  ));
}

function resumeTask(): void {
  void execute(() => resumeStudentTask(assignmentId.value));
}

function submitCheckIn(): void {
  const content = checkInContent.value.trim();
  if ((!content && !uploadedFileIds.value.length) || hasUploading.value) return;
  void execute(() => submitStudentTaskCheckIn(
    assignmentId.value,
    content,
    uploadedFileIds.value
  ), true);
}

function chooseImages(): void {
  if (working.value || pendingImages.value.length >= 9) return;
  uni.chooseImage({
    count: 9 - pendingImages.value.length,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (result) => {
      const paths = Array.isArray(result.tempFilePaths)
        ? result.tempFilePaths
        : [result.tempFilePaths];
      paths.forEach((filePath: string, index: number) => {
        const item: PendingImage = {
          localId: `${Date.now()}-${index}-${filePath}`,
          filePath,
          fileId: null,
          progress: 0,
          status: 'UPLOADING'
        };
        pendingImages.value.push(item);
        void uploadImage(item);
      });
    }
  });
}

async function uploadImage(item: PendingImage): Promise<void> {
  item.status = 'UPLOADING';
  item.progress = 0;
  try {
    const uploaded = await uploadTaskAttachment(
      item.filePath,
      (progress) => { item.progress = progress; }
    );
    item.fileId = uploaded.id;
    item.progress = 100;
    item.status = 'UPLOADED';
  } catch (error) {
    item.status = 'FAILED';
    errorMessage.value = toMessage(error);
  }
}

function retryUpload(item: PendingImage): void {
  if (working.value || item.status !== 'FAILED') return;
  void uploadImage(item);
}

async function removeImage(item: PendingImage): Promise<void> {
  if (working.value || item.status === 'UPLOADING') return;
  try {
    if (item.fileId) await deleteTaskAttachment(item.fileId);
    pendingImages.value = pendingImages.value.filter((candidate) => candidate.localId !== item.localId);
  } catch (error) {
    errorMessage.value = toMessage(error);
  }
}

function previewPendingImage(filePath: string): void {
  uni.previewImage({ current: filePath, urls: pendingImages.value.map((item) => item.filePath) });
}

function previewHistoryImage(index: number): void {
  if (!task.value?.latestCheckIn) return;
  const urls = task.value.latestCheckIn.attachments
    .map((attachment) => historyPreviewPaths.value[attachment.id])
    .filter((path): path is string => Boolean(path));
  const attachment = task.value.latestCheckIn.attachments[index];
  const current = historyPreviewPaths.value[attachment.id];
  if (current && urls.length) uni.previewImage({ current, urls });
}

async function prepareHistoryPreviews(): Promise<void> {
  const attachments = task.value?.latestCheckIn?.attachments ?? [];
  historyPreviewPaths.value = {};
  await Promise.all(attachments.map(async (attachment) => {
    try {
      const path = await downloadTaskAttachment(attachment.contentUrl);
      historyPreviewPaths.value = { ...historyPreviewPaths.value, [attachment.id]: path };
    } catch {
      // 图片读取失败不影响任务文字和审核状态展示。
    }
  }));
}

function confirmAbandon(): void {
  if (working.value) return;
  uni.showModal({
    title: '确认放弃任务',
    content: '任务将转为待优化，不会扣分。',
    confirmText: '确认放弃',
    confirmColor: '#b54732',
    success: (result) => {
      if (result.confirm) {
        void execute(() => abandonStudentTask(assignmentId.value, '学生主动放弃'));
      }
    }
  });
}

async function execute(
  action: () => Promise<StudentTaskAssignment>,
  clearCheckIn = false
): Promise<void> {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  working.value = true;
  errorMessage.value = '';
  try {
    task.value = await action();
    if (clearCheckIn) {
      checkInContent.value = '';
      pendingImages.value = [];
    }
    await prepareHistoryPreviews();
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    working.value = false;
  }
}

function onPauseDurationChange(event: { detail: { value: string | number } }): void {
  pauseDurationIndex.value = Number(event.detail.value);
}

function reload(): void {
  void initialize();
}

function sourceLabel(source: LearningTaskSourceType): string {
  return source === 'FAMILY' ? '家庭任务' : source === 'ORGANIZATION' ? '机构任务' : '教师任务';
}

function statusLabel(status: TaskAssignmentEffectiveStatus): string {
  const labels: Record<TaskAssignmentEffectiveStatus, string> = {
    PENDING_CLAIM: '待认领', IN_PROGRESS: '进行中', PAUSED: '已暂停',
    PENDING_REVIEW: '待审核', NEEDS_IMPROVEMENT: '待优化', EXEMPT: '免执行', COMPLETED: '已完成'
  };
  return labels[status];
}

function pauseTypeLabel(type: TaskPauseType): string {
  return type === 'EMOTION' ? '情绪暂停' : '难题搁置';
}

function terminalStateText(status: TaskAssignmentEffectiveStatus): string {
  if (status === 'PENDING_REVIEW') return '打卡已提交，等待审核';
  if (status === 'NEEDS_IMPROVEMENT') return '任务已转为待优化';
  if (status === 'EXEMPT') return '本任务已免执行';
  if (status === 'COMPLETED') return '任务已完成';
  return '';
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
.title-meta, .section-heading { display: flex; align-items: center; gap: 20rpx; }
.title-meta { justify-content: flex-start; flex-wrap: wrap; }
.section-heading { justify-content: space-between; }
.source-label, .status-label, .task-tag, .migration-label { padding: 6rpx 12rpx; border-radius: 6rpx; font-size: 22rpx; }
.migration-label { background: #eef1f2; color: #66716c; }
.source-family { background: #fceff3; color: #9d3658; }
.source-organization, .status-pending_review { background: #edf3ff; color: #315f9f; }
.source-teacher, .status-in_progress, .status-completed { background: #e9f7f5; color: #147069; }
.status-pending_claim { background: #fff5de; color: #8b6417; }
.status-paused, .status-exempt { background: #eef1f2; color: #52615c; }
.status-needs_improvement { background: #fff0e8; color: #9a4b38; }
.task-title { display: block; margin-top: 24rpx; color: #1c2b28; font-size: 40rpx; font-weight: 700; line-height: 1.45; }
.source-name { display: block; margin-top: 14rpx; color: #708078; font-size: 24rpx; }
.error-band { padding: 20rpx 36rpx; background: #fff1ed; color: #9a4b38; font-size: 24rpx; }
.detail-band { margin-top: 20rpx; padding: 12rpx 36rpx; background: #ffffff; border-top: 2rpx solid #e1e9e5; border-bottom: 2rpx solid #e1e9e5; }
.detail-row { min-height: 86rpx; display: flex; align-items: center; justify-content: space-between; gap: 32rpx; border-bottom: 2rpx solid #edf1ef; color: #1c2b28; font-size: 28rpx; }
.detail-row:last-child { border-bottom: 0; }
.detail-label { color: #708078; }
.section-title { display: block; padding: 20rpx 0 8rpx; color: #536760; font-size: 26rpx; font-weight: 650; }
.submission-number { color: #708078; font-size: 24rpx; }
.tag-band { padding-bottom: 28rpx; }
.tag-list { display: flex; flex-wrap: wrap; gap: 12rpx; padding-top: 12rpx; }
.task-tag { background: #edf2f0; color: #536760; }
.remark-text { display: block; padding: 12rpx 0 28rpx; color: #1c2b28; font-size: 28rpx; line-height: 1.75; white-space: pre-wrap; }
.checkin-history .remark-text { padding-bottom: 16rpx; }
.history-image-grid, .pending-image-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; padding: 16rpx 0 24rpx; }
.history-image-item, .pending-image-item, .add-image { position: relative; width: 100%; aspect-ratio: 1; overflow: hidden; border: 2rpx solid #d5dfda; border-radius: 8rpx; background: #f5f8f6; }
.history-image-item { display: flex; align-items: center; justify-content: center; color: #64746d; font-size: 22rpx; text-align: center; }
.history-image-item image, .pending-image-item image { width: 100%; height: 100%; }
.review-comment { display: block; margin: 0 -36rpx; padding: 20rpx 36rpx; background: #fff4ef; color: #944633; font-size: 26rpx; line-height: 1.6; }
.action-band { margin-top: 20rpx; padding: 32rpx 36rpx; background: #ffffff; border-top: 2rpx solid #e1e9e5; border-bottom: 2rpx solid #e1e9e5; }
.primary-action, .secondary-actions button, .danger-action, .retry-button { border-radius: 8rpx; font-size: 28rpx; }
.primary-action { width: 100%; height: 84rpx; margin: 0; background: #167c5a; color: #ffffff; }
.primary-action::after { border: 0; }
.checkin-input { width: 100%; height: 220rpx; padding: 24rpx; box-sizing: border-box; border: 2rpx solid #cad7d2; border-radius: 8rpx; background: #fbfdfc; color: #1c2b28; font-size: 28rpx; line-height: 1.6; }
.upload-section { margin-top: 18rpx; }
.upload-mask { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(28, 43, 40, 0.68); color: #ffffff; font-size: 24rpx; }
.upload-mask.failed button { min-width: 112rpx; height: 56rpx; padding: 0 18rpx; border-radius: 8rpx; background: #ffffff; color: #9a4b38; font-size: 22rpx; line-height: 56rpx; }
.remove-image { position: absolute; top: 8rpx; right: 8rpx; width: 48rpx; height: 48rpx; margin: 0; padding: 0; border-radius: 50%; background: rgba(28, 43, 40, 0.76); color: #ffffff; font-size: 34rpx; line-height: 44rpx; }
.remove-image::after, .add-image::after { border: 0; }
.add-image { display: flex; align-items: center; justify-content: center; margin: 0; color: #167c5a; font-size: 24rpx; }
.upload-hint { display: block; color: #7b8983; font-size: 22rpx; }
.input-counter { margin: 12rpx 0 20rpx; text-align: right; color: #7b8983; font-size: 22rpx; }
.duration-picker { min-height: 76rpx; display: flex; align-items: center; justify-content: space-between; margin-top: 28rpx; padding: 0 24rpx; border: 2rpx solid #d7e0dc; border-radius: 8rpx; color: #536760; font-size: 26rpx; }
.secondary-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; margin-top: 18rpx; }
.secondary-actions button { width: 100%; height: 76rpx; margin: 0; background: #edf3f0; color: #315f50; }
.secondary-actions button::after { border-color: #c0d2ca; }
.danger-action { height: 72rpx; margin: 28rpx 0 0; background: transparent; color: #a04432; }
.danger-action::after { border-color: #dfb6ac; }
.pause-summary { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin-bottom: 24rpx; color: #536760; font-size: 26rpx; }
.terminal-state { min-height: 80rpx; display: flex; align-items: center; justify-content: center; color: #536760; font-size: 28rpx; }
.state-view { min-height: 520rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 28rpx; color: #708078; font-size: 28rpx; }
.unavailable, .error-view { color: #9a4b38; }
.error-view { padding: 0 48rpx; text-align: center; }
.retry-button { width: 180rpx; height: 64rpx; margin: 0; background: #ffffff; color: #167c5a; }
.retry-button::after { border-color: #a9cbbf; }
</style>
