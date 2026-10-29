<template>
  <view class="prototype-workspace page-shell" :class="{ 'compact-detail': compactView }">
    <PixelPageHeader title="任务详情" back fallback="/pages/task-list/task-list" />
    <view v-if="loading" class="state-view"><text>正在加载任务</text></view>
    <view v-else-if="!learningTaskEnabled" class="state-view unavailable">
      <text>学习任务暂不可用</text>
    </view>
    <view v-else-if="errorMessage && !task" class="state-view error-view">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @tap="reload">重试</button>
    </view>
    <template v-else-if="task">
      <view v-if="compactView" class="compact-summary">
        <view class="compact-heading"><view class="detail-symbol"><PrototypeIcon name="book" :size="42" /></view><view class="title-copy"><text class="task-title">{{ task.title }}</text><text v-if="task.categoryCode" class="compact-category">{{ task.categoryCode }}</text></view><text class="compact-points">+{{ task.basePoints }} 积分</text></view>
        <text v-if="task.description" class="compact-description">{{ task.description }}</text>
      </view>
      <view v-else class="title-band"><view class="detail-symbol"><PrototypeIcon name="book" :size="42"/></view><view class="title-copy"><text class="task-title">{{task.title}}</text><view class="title-meta"><text class="source-label" :class="`source-${task.sourceType.toLowerCase()}`">{{sourceLabel(task.sourceType)}}</text><text class="status-label" :class="`status-${task.effectiveStatus.toLowerCase()}`">{{statusLabel(task.effectiveStatus)}}</text><text v-if="task.overnightMigrated" class="migration-label">隔夜迁移</text></view><text v-if="task.sourceOrganizationName" class="source-name">{{task.sourceOrganizationName}}</text></view></view>

      <view v-if="errorMessage" class="error-band"><text>{{ errorMessage }}</text></view>

      <view v-if="!compactView" class="detail-band metadata-band">
        <view class="detail-row"><text class="detail-label">计划日期</text><text>{{ task.scheduledDate }}</text></view>
        <view class="detail-row"><text class="detail-label">截止时间</text><text>{{ formatTime(task.dueAt) }}</text></view>
        <view class="detail-row"><text class="detail-label">任务难度</text><text>{{ task.difficultyLevel }} 级</text></view>
        <view class="detail-row"><text class="detail-label">基础积分</text><text>{{ task.basePoints }} 分</text></view>
        <view class="detail-row"><text class="detail-label">执行时长</text><text>{{ task.durationMinutes }} 分钟</text></view>
        <view class="detail-row"><text class="detail-label">审核人</text><text>{{ task.reviewerDisplayName }}</text></view>
        <view v-if="task.categoryCode" class="detail-row"><text class="detail-label">任务分类</text><text>{{ task.categoryCode }}</text></view>
      </view>

      <view v-if="!compactView && task.tagCodes.length" class="detail-band tag-band">
        <text class="section-title">任务标签</text>
        <view class="tag-list"><text v-for="tag in task.tagCodes" :key="tag" class="task-tag">{{ tag }}</text></view>
      </view>

      <view v-if="!compactView && task.remark" class="detail-band">
        <text class="section-title">任务备注</text>
        <text class="remark-text">{{ task.remark }}</text>
      </view>

      <view v-if="!compactView && task.description" class="detail-band">
        <text class="section-title">任务内容</text>
        <text class="remark-text">{{ task.description }}</text>
      </view>

      <view v-if="task.requirementItems.length" class="detail-band requirement-band">
        <text class="section-title">{{ compactView ? '目标要求' : '任务要求' }}</text>
        <view class="requirement-list">
          <view v-for="(item, index) in task.requirementItems" :key="index" class="requirement-item">
            <view class="requirement-check"><PrototypeIcon name="check" tone="green" :size="24"/></view>
            <text class="requirement-text">{{ item }}</text>
          </view>
        </view>
      </view>

      <view v-if="!compactView && examplePreviewList.length" class="detail-band example-band">
        <text class="section-title">参考示例</text>
        <view class="history-image-grid">
          <view
            v-for="(preview, index) in examplePreviewList"
            :key="preview.fileId"
            class="history-image-item"
            @tap="previewExampleImage(index)"
          >
            <image :src="preview.path" mode="aspectFill" />
          </view>
        </view>
      </view>

      <view v-if="!compactView && timelineSteps.length" class="detail-band timeline-band">
        <text class="section-title">执行时间线</text>
        <view class="timeline-list">
          <view
            v-for="(step, index) in timelineSteps"
            :key="step.label"
            class="timeline-node"
            :class="`is-${step.state}`"
          >
            <view class="timeline-rail">
              <view class="timeline-dot"><PrototypeIcon v-if="step.state === 'done'" name="check" tone="white" :size="22"/></view>
              <view v-if="index < timelineSteps.length - 1" class="timeline-line"/>
            </view>
            <view class="timeline-copy">
              <view class="timeline-head">
                <text class="timeline-label">{{ step.label }}</text>
                <text v-if="step.state === 'active'" class="timeline-active-badge">进行中</text>
              </view>
              <text v-if="step.time" class="timeline-time">{{ step.time }}</text>
              <text class="timeline-desc">{{ step.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <view v-if="!compactView && task.latestCheckIn" class="detail-band checkin-history">
        <view class="section-heading">
          <text class="section-title">最近打卡</text>
          <text class="submission-number">第 {{ task.latestCheckIn.submissionNo }} 次</text>
        </view>
        <text v-if="task.latestCheckIn.content" class="remark-text">{{ task.latestCheckIn.content }}</text>
        <view v-if="attachmentServiceEnabled && task.latestCheckIn.attachments.length" class="history-image-grid">
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

      <view class="action-band" :class="{'awaiting-claim':task.effectiveStatus==='PENDING_CLAIM'}"><text v-if="!compactView && task.effectiveStatus!=='PENDING_CLAIM'" class="section-title">我的任务行动</text>
        <button
          v-if="task.effectiveStatus === 'PENDING_CLAIM'"
          class="primary-action"
          :loading="working"
          :disabled="working"
          @tap="claimTask"
        >认领任务</button>

        <template v-else-if="task.effectiveStatus === 'IN_PROGRESS'">
          <text class="form-heading">{{ compactView ? '我的完成记录' : '完成情况' }}</text>
          <view class="completion-options">
            <view class="completion-option" @tap="checkInCompletion = 'FULL'">
              <view class="radio-dot" :class="{ on: checkInCompletion === 'FULL' }"/>
              <text class="completion-label">已完成</text>
            </view>
            <view class="completion-option" @tap="checkInCompletion = 'PARTIAL'">
              <view class="radio-dot" :class="{ on: checkInCompletion === 'PARTIAL' }"/>
              <text class="completion-label">部分完成</text>
            </view>
          </view>
          <text v-if="!compactView" class="form-heading">文字记录</text>
          <textarea
            v-model="checkInContent"
            class="checkin-input"
            maxlength="1000"
            placeholder="记录本次完成情况"
            :disabled="working"
          />
          <view v-if="attachmentServiceEnabled" class="upload-section"><text v-if="!compactView" class="form-heading">上传图片</text>
            <view class="pending-image-grid" :class="{ 'empty-upload': !pendingImages.length }">
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
                v-if="pendingImages.length < 3"
                class="add-image"
                :disabled="working"
                @tap="chooseImages"
              ><PrototypeIcon name="plus" :size="24" /><text>{{ compactView ? '上传记录' : '添加图片' }}</text></button>
            </view>
            <text class="upload-hint">支持 JPG、PNG，最多 3 张，单张不超过 10MB</text>
          </view>
          <view class="input-counter">{{ checkInContent.length }}/1000</view>
          <button
            class="primary-action"
            :loading="working"
            :disabled="working || hasUploading || (!checkInContent.trim() && !uploadedFileIds.length)"
            @tap="submitCheckIn"
          >{{ compactView ? '提交完成' : '提交打卡' }}</button>
          <view v-if="compactView" class="compact-links"><button @tap="showFullDetails = true">任务详情</button><button @tap="showMoreActions = !showMoreActions">{{ showMoreActions ? '收起操作' : '更多操作' }}</button></view>
          <template v-if="!compactView || showMoreActions">
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
            <button :disabled="working" @tap="openPauseGuide('EMOTION')">情绪暂停</button>
            <button :disabled="working" @tap="openPauseGuide('DIFFICULTY')">难题搁置</button>
          </view>
          <view class="secondary-actions">
            <button :disabled="working" @tap="openDeferPage">任务顺延</button>
          </view>
          <button class="danger-action" :disabled="working" @tap="openAbandonPage">放弃任务</button>
          </template>
        </template>

        <template v-else-if="task.effectiveStatus === 'PAUSED'">
          <view v-if="task.activePause" class="pause-summary">
            <text>{{ pauseTypeLabel(task.activePause.pauseType) }}{{ task.activePause.adjustMethod ? '：' + task.activePause.adjustMethod : '' }}{{ task.activePause.reasonNote ? '（' + task.activePause.reasonNote + '）' : '' }}</text>
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
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { isCompactTaskDetail } from './task-detail-presentation';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  claimStudentTask,
  getStudentTaskAssignment,
  resumeStudentTask,
  submitStudentTaskCheckIn,
  type LearningTaskSourceType,
  type StudentTaskAssignment,
  type TaskAssignmentEffectiveStatus,
  type TaskCheckInCompletion,
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
const requestedLayout = ref<unknown>('');
const showFullDetails = ref(false);
const showMoreActions = ref(false);
const assignmentId = ref('');
const task = ref<StudentTaskAssignment | null>(null);
const compactView = computed(() => !showFullDetails.value && isCompactTaskDetail(requestedLayout.value, task.value?.effectiveStatus));
const loading = ref(true);
const working = ref(false);
const learningTaskEnabled = ref(false);
const attachmentServiceEnabled = ref(true);
const errorMessage = ref('');
const checkInContent = ref('');
const checkInCompletion = ref<TaskCheckInCompletion>('FULL');
const pendingImages = ref<PendingImage[]>([]);
const historyPreviewPaths = ref<Record<string, string>>({});
const examplePreviewList = ref<{ fileId: string; path: string }[]>([]);
const pauseDurationIndex = ref(0);
const selectedPauseDuration = computed(() => pauseDurations[pauseDurationIndex.value]);
const uploadedFileIds = computed(() => pendingImages.value
  .filter((item) => item.status === 'UPLOADED' && item.fileId)
  .map((item) => item.fileId as string));
const hasUploading = computed(() => pendingImages.value.some((item) => item.status === 'UPLOADING'));

interface TimelineStep {
  label: string;
  desc: string;
  state: 'done' | 'active' | 'pending';
  time: string;
}
const timelineSteps = computed<TimelineStep[]>(() => {
  const assignment = task.value;
  if (!assignment) return [];
  const status = assignment.effectiveStatus;
  if (status === 'PENDING_CLAIM') return [];
  const nodes = assignment.timeline ?? [];
  // 重认领/多次打卡场景取最近一次事件时间。
  const eventOf = (type: string) => [...nodes].reverse().find((node) => node.eventType === type);
  const claimed = eventOf('CLAIMED');
  const checkedIn = eventOf('CHECKED_IN');
  const claimedTime = claimed ? formatTime(claimed.occurredAt) : '';
  const checkInStage: TimelineStep['state'] =
    status === 'PENDING_REVIEW' || status === 'COMPLETED' || status === 'EXEMPT' ? 'done'
      : status === 'IN_PROGRESS' || status === 'PAUSED' || status === 'NEEDS_IMPROVEMENT' ? 'active'
        : 'pending';
  return [
    {
      label: '任务已认领',
      desc: '我已认领，准备开始啦！',
      state: claimed ? 'done' : 'pending',
      time: claimedTime
    },
    {
      label: '开始执行',
      desc: '按任务要求完成执行。',
      state: claimed ? 'done' : 'pending',
      time: claimedTime
    },
    {
      label: '提交打卡',
      desc: '拍照或文字记录完成情况。',
      state: checkInStage,
      time: checkInStage === 'done' && checkedIn ? formatTime(checkedIn.occurredAt) : ''
    },
    {
      label: '完成审核',
      desc: '等待成长伙伴确认。',
      state: status === 'COMPLETED' ? 'done' : status === 'PENDING_REVIEW' ? 'active' : 'pending',
      time: ''
    }
  ];
});

let epoch = 0;
let nativePickerOpen = false;
let pickerReturnPending = false;
let pickerToken = '';
const current = (n: number, token: string) => n === epoch && getStudentSession()?.accessToken === token;
function invalidate() { epoch++; task.value = null; pendingImages.value = []; historyPreviewPaths.value = {}; examplePreviewList.value = []; checkInContent.value = ''; working.value = false; learningTaskEnabled.value = false; errorMessage.value = ''; }
onHide(() => { if (nativePickerOpen) pickerReturnPending = true; else invalidate(); });
onUnload(invalidate);

onLoad((options) => {
  requestedLayout.value = options?.layout;
  assignmentId.value = typeof options?.id === 'string' ? options.id : '';
});

onShow(() => {
  if (pickerReturnPending && getStudentSession()?.accessToken === pickerToken) { pickerReturnPending = false; return; }
  pickerReturnPending = false;
  if (nativePickerOpen && getStudentSession()?.accessToken === pickerToken) return;
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
    attachmentServiceEnabled.value = capabilities.attachmentServiceEnabled !== false;
    if (!attachmentServiceEnabled.value) pendingImages.value = [];
    const result = learningTaskEnabled.value ? await getStudentTaskAssignment(assignmentId.value) : null;
    if (!current(n, token)) return;
    task.value = result;
    await prepareHistoryPreviews();
    await prepareExamplePreviews();
  } catch (error) {
    if (token && current(n, token)) errorMessage.value = toMessage(error);
  } finally {
    if (token && current(n, token)) loading.value = false;
  }
}

async function prepareExamplePreviews(): Promise<void> {
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  const files = task.value?.exampleFiles ?? [];
  examplePreviewList.value = [];
  if (!attachmentServiceEnabled.value || !files.length) return;
  await Promise.all(files.map(async (file) => {
    try {
      const path = await downloadTaskAttachment(file.contentUrl);
      if (current(n, token)) {
        examplePreviewList.value = [...examplePreviewList.value, { fileId: file.id, path }]
          .sort((a, b) => files.findIndex((f) => f.id === a.fileId) - files.findIndex((f) => f.id === b.fileId));
      }
    } catch {
      // 示例图读取失败不影响其余任务内容展示。
    }
  }));
}

function previewExampleImage(index: number): void {
  if (!attachmentServiceEnabled.value) return;
  const urls = examplePreviewList.value.map((item) => item.path);
  if (urls.length) uni.previewImage({ current: urls[index], urls });
}

function claimTask(): void {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  uni.navigateTo({ url: '/pages/task-claim-confirm/task-claim-confirm?id=' + assignmentId.value });
}

function openPauseGuide(pauseType: TaskPauseType): void {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  uni.navigateTo({
    url: `/pages/task-pause-guide/task-pause-guide?id=${assignmentId.value}&mode=${pauseType}&duration=${selectedPauseDuration.value.value}`
  });
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
    uploadedFileIds.value,
    checkInCompletion.value
  ), true);
}

function chooseImages(): void {
  if (!attachmentServiceEnabled.value || working.value || pendingImages.value.length >= 3) return;
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  nativePickerOpen = true;
  pickerToken = token;
  uni.chooseImage({
    count: 3 - pendingImages.value.length,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    complete: () => { nativePickerOpen = false; },
    success: (result) => {
      if (!current(n, token)) return;
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
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  item.status = 'UPLOADING';
  item.progress = 0;
  try {
    const uploaded = await uploadTaskAttachment(
      item.filePath,
      (progress) => { if (current(n, token)) item.progress = progress; }
    );
    if (!current(n, token)) return;
    item.fileId = uploaded.id;
    item.progress = 100;
    item.status = 'UPLOADED';
  } catch (error) {
    if (!current(n, token)) return;
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
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  try {
    if (item.fileId) await deleteTaskAttachment(item.fileId);
    if (!current(n, token)) return;
    pendingImages.value = pendingImages.value.filter((candidate) => candidate.localId !== item.localId);
  } catch (error) {
    if (current(n, token)) errorMessage.value = toMessage(error);
  }
}

function previewPendingImage(filePath: string): void {
  if (!attachmentServiceEnabled.value) return;
  uni.previewImage({ current: filePath, urls: pendingImages.value.map((item) => item.filePath) });
}

function previewHistoryImage(index: number): void {
  if (!attachmentServiceEnabled.value || !task.value?.latestCheckIn) return;
  const urls = task.value.latestCheckIn.attachments
    .map((attachment) => historyPreviewPaths.value[attachment.id])
    .filter((path): path is string => Boolean(path));
  const attachment = task.value.latestCheckIn.attachments[index];
  const current = historyPreviewPaths.value[attachment.id];
  if (current && urls.length) uni.previewImage({ current, urls });
}

async function prepareHistoryPreviews(): Promise<void> {
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  if (!attachmentServiceEnabled.value) {
    historyPreviewPaths.value = {};
    return;
  }
  const attachments = task.value?.latestCheckIn?.attachments ?? [];
  historyPreviewPaths.value = {};
  await Promise.all(attachments.map(async (attachment) => {
    try {
      const path = await downloadTaskAttachment(attachment.contentUrl);
      if (current(n, token)) historyPreviewPaths.value = { ...historyPreviewPaths.value, [attachment.id]: path };
    } catch {
      // 图片读取失败不影响任务文字和审核状态展示。
    }
  }));
}

// 顺延与放弃均经独立确认页（原型 05-09/05-10），替代原生弹窗。
function openDeferPage(): void {
  if (working.value) return;
  uni.navigateTo({
    url: `/pages/task-defer/task-defer?id=${encodeURIComponent(assignmentId.value)}`
  });
}

function openAbandonPage(): void {
  if (working.value) return;
  uni.navigateTo({
    url: `/pages/task-abandon/task-abandon?id=${encodeURIComponent(assignmentId.value)}`
  });
}

async function execute(
  action: () => Promise<StudentTaskAssignment>,
  clearCheckIn = false
): Promise<void> {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  working.value = true;
  errorMessage.value = '';
  try {
    const result = await action();
    if (!current(n, token)) return;
    task.value = result;
    if (clearCheckIn) {
      checkInContent.value = '';
      pendingImages.value = [];
    }
    await prepareHistoryPreviews();
  } catch (error) {
    if (current(n, token)) errorMessage.value = toMessage(error);
  } finally {
    if (current(n, token)) working.value = false;
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
@import "@/styles/workspace-prototype.scss";

.page-shell { padding-bottom: 48rpx; background: #f4f7f5; }
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
.task-title { margin-top: 24rpx; color: #1c2b28; font-size: 40rpx; }
.source-name { margin-top: 14rpx; color: #708078; }
.error-band { padding: 20rpx 36rpx; background: #fff1ed; color: #9a4b38; font-size: 24rpx; }
.detail-band { margin-top: 20rpx; padding: 12rpx 36rpx; background: #ffffff; border-top: 2rpx solid #e1e9e5; border-bottom: 2rpx solid #e1e9e5; }
.detail-row { min-height: 86rpx; display: flex; align-items: center; justify-content: space-between; gap: 32rpx; border-bottom: 2rpx solid #edf1ef; color: #1c2b28; font-size: 28rpx; }
.detail-row:last-child { border-bottom: 0; }
.detail-label { color: #708078; }
.section-title { display: block; padding: 20rpx 0 8rpx; color: #536760; font-size: 26rpx; font-weight: 650; }
.submission-number { color: #708078; font-size: 24rpx; }
.tag-band { padding-bottom: 28rpx; }
.requirement-band { padding-bottom: 28rpx; }
.requirement-list { display: flex; flex-direction: column; gap: 16rpx; padding-top: 12rpx; }
.requirement-item { display: flex; align-items: center; gap: 16rpx; }
.requirement-check { width: 44rpx; height: 44rpx; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: #e9f7f5; border-radius: 50%; }
.requirement-text { color: #1c2b28; font-size: 27rpx; line-height: 1.6; }
.example-band { padding-bottom: 28rpx; }
.timeline-band { padding-bottom: 30rpx; }
.timeline-list { display: flex; flex-direction: column; padding-top: 12rpx; }
.timeline-node { display: flex; align-items: stretch; gap: 20rpx; }
.timeline-rail { width: 34rpx; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; }
.timeline-dot { width: 36rpx; height: 36rpx; margin-top: 4rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: #cdd9d2; }
.timeline-line { flex: 1; width: 4rpx; min-height: 40rpx; background: #cdd9d2; }
.timeline-copy { display: flex; flex-direction: column; gap: 4rpx; padding-bottom: 30rpx; }
.timeline-head { display: flex; align-items: center; gap: 12rpx; }
.timeline-active-badge { padding: 2rpx 12rpx; border-radius: 999rpx; background: #fff0d7; color: #b0731a; font-size: 20rpx; }
.timeline-desc { color: #8a9981; font-size: 23rpx; line-height: 1.5; }
.timeline-label { color: #8a9981; font-size: 27rpx; }
.timeline-time { color: #a8b5ae; font-size: 23rpx; }
.timeline-node.is-done .timeline-dot { background: #167c5a; }
.timeline-node.is-done .timeline-line { background: #167c5a; }
.timeline-node.is-done .timeline-label { color: #147069; }
.timeline-node.is-done .timeline-time { color: #5c7a6e; }
.timeline-node.is-active .timeline-dot { background: #eea12a; box-shadow: 0 0 0 8rpx #fff0d7; }
.timeline-node.is-active .timeline-label { color: #b0731a; font-weight: 650; }
.timeline-node.is-active .timeline-time { color: #d5a04a; }
.tag-list { display: flex; flex-wrap: wrap; gap: 12rpx; padding-top: 12rpx; }
.task-tag { background: #edf2f0; color: #536760; }
.remark-text { display: block; padding: 12rpx 0 28rpx; color: #1c2b28; font-size: 28rpx; line-height: 1.75; white-space: pre-wrap; }
.checkin-history .remark-text { padding-bottom: 16rpx; }
.history-image-grid, .pending-image-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; padding: 16rpx 0 24rpx; }
.history-image-item, .pending-image-item, .add-image { position: relative; width: 100%; aspect-ratio: 1; overflow: hidden; border: 2rpx solid #d5dfda; border-radius: 8rpx; background: #f5f8f6; }
.history-image-item { display: flex; align-items: center; justify-content: center; color: #64746d; font-size: 22rpx; text-align: center; }
.history-image-item image, .pending-image-item image { width: 100%; height: 100%; }
.review-comment { display: block; margin: 0 -36rpx; padding: 20rpx 36rpx; background: #fff4ef; color: #944633; font-size: 26rpx; line-height: 1.6; }
.action-band { padding: 32rpx 36rpx; background: #ffffff; border-top: 2rpx solid #e1e9e5; border-bottom: 2rpx solid #e1e9e5; }
.primary-action, .secondary-actions button, .danger-action, .retry-button { border-radius: 8rpx; font-size: 28rpx; }
.primary-action { width: 100%; height: 84rpx; margin: 0; background: #167c5a; color: #ffffff; }
.primary-action::after { border: 0; }
.checkin-input { width: 100%; padding: 24rpx; border: 2rpx solid #cad7d2; border-radius: 8rpx; background: #fbfdfc; color: #1c2b28; font-size: 28rpx; line-height: 1.6; }
.upload-section { margin-top: 18rpx; }
.upload-mask { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(28, 43, 40, 0.68); color: #ffffff; font-size: 24rpx; }
.upload-mask.failed button { min-width: 112rpx; height: 56rpx; padding: 0 18rpx; border-radius: 8rpx; background: #ffffff; color: #9a4b38; font-size: 22rpx; line-height: 56rpx; }
.remove-image { position: absolute; top: 8rpx; right: 8rpx; width: 48rpx; height: 48rpx; margin: 0; padding: 0; border-radius: 50%; background: rgba(28, 43, 40, 0.76); color: #ffffff; font-size: 34rpx; line-height: 44rpx; }
.remove-image::after, .add-image::after { border: 0; }
.add-image { margin: 0; color: #167c5a; font-size: 24rpx; }
.upload-hint { display: block; color: #7b8983; font-size: 22rpx; }
.input-counter { margin: 12rpx 0 20rpx; text-align: right; color: #7b8983; font-size: 22rpx; }
.duration-picker { min-height: 76rpx; display: flex; align-items: center; justify-content: space-between; border: 2rpx solid #d7e0dc; color: #536760; font-size: 26rpx; }
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

.page-shell{padding-bottom:160rpx!important;}.action-band{background:white!important;border-radius:26rpx;padding:28rpx!important;margin-top:24rpx;}.form-heading{display:block;font-size:27rpx;font-weight:650;margin:24rpx 0 14rpx;color:#203e32;}
.completion-options{display:flex;align-items:center;gap:56rpx;padding:6rpx 0 4rpx;}
.completion-option{display:flex;align-items:center;gap:14rpx;}
.completion-label{color:#1c2b28;font-size:27rpx;}
.radio-dot{width:34rpx;height:34rpx;flex-shrink:0;border-radius:50%;border:4rpx solid #c4d2cb;background:#ffffff;box-sizing:border-box;}
.radio-dot.on{border-width:11rpx;border-color:#167c5a;}.checkin-input{background:#fcfdfb!important;border:1rpx solid #d9e3dc!important;border-radius:18rpx!important;padding:22rpx!important;box-sizing:border-box;}.primary-action{position:fixed!important;bottom:calc(24rpx + env(safe-area-inset-bottom));left:32rpx;right:32rpx;z-index:20;color:white!important;box-shadow:0 8rpx 24rpx #184c3420;}.secondary-actions{margin-top:24rpx!important;gap:18rpx!important;}.secondary-actions button{background:#edf6ee!important;color:#185b41!important;border-radius:18rpx!important;}.duration-picker{background:#f4f7f2;padding:22rpx;border-radius:16rpx;margin-top:24rpx;}.terminal-state,.pause-summary{padding:26rpx!important;background:#eef6ed;border-radius:22rpx!important;}.add-image{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10rpx;}
.title-band{display:flex;align-items:flex-start;gap:22rpx;}.detail-symbol{flex-shrink:0;margin:0!important;}.title-copy{flex:1;min-width:0;}.task-title{display:block;line-height:1.5;}.title-meta{margin-top:12rpx;}.source-name{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:23rpx;}.metadata-band{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:28rpx 30rpx;padding:28rpx!important;}.metadata-band .detail-row{display:flex;flex-direction:column;align-items:flex-start;gap:8rpx;padding:0;border:0;font-size:26rpx;}.metadata-band .detail-label{font-size:22rpx;color:#8a9981;}.primary-action{width:auto!important;margin:0!important;box-sizing:border-box;background:linear-gradient(120deg,#184f40,#256b50)!important;}.awaiting-claim{background:transparent!important;border:0!important;padding:0!important;margin:0!important;}
.prototype-workspace{background:#f7faf6!important;color:#172b25}.task-symbol{border-radius:50%!important;background:#e7f6ed!important}.task-card,.task-list-card{border:0!important;border-radius:22rpx!important;box-shadow:none!important}.task-title{font-size:30rpx!important}.primary-action{border-radius:44rpx!important}.prototype-header{background:#fff!important;color:#172b25!important;justify-content:center;border-radius:0!important;padding:28rpx!important}.prototype-header-title{font-size:34rpx}.publish-actions{position:fixed!important;bottom:0;left:0;right:0;padding:20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));box-sizing:border-box}.page>.task-editor{padding-bottom:180rpx!important}.task-list-card>.heading{display:block;margin-bottom:12rpx}.task-list-card>text:not(.heading){font-size:24rpx;color:#7c8981}.task-list-card button{display:inline-flex;margin:18rpx 12rpx 0 0;line-height:58rpx;padding:0 22rpx}.page-shell{padding:24rpx 28rpx 170rpx!important;box-sizing:border-box}.metadata-band .detail-row{min-height:0}.detail-symbol{background:#fff0d7!important;color:#eea12a!important}.action-band{border:0!important}.checkin-input{height:180rpx}.ld-heading{text-align:center;background:white;margin:0 -32rpx;padding:28rpx}.source-filter-inner{display:flex;width:100%;gap:0;background:#eff3ee;border-radius:24rpx}.source-button{flex:1;min-width:0;padding:0;box-shadow:none;background:transparent}.source-button.active{background:transparent!important;color:#075e43!important;border-bottom:4rpx solid #075e43}.task-row-top{justify-content:space-between}.task-meta{font-size:23rpx}.task-list-card{min-height:148rpx}

.compact-detail .compact-summary{background:#fff;border-radius:24rpx;padding:24rpx;margin-top:12rpx;}
.compact-heading{display:flex;align-items:center;gap:24rpx;}
.compact-detail .task-title{margin:0;font-size:32rpx!important;line-height:1.4;overflow-wrap:anywhere;}
.compact-points{color:#ef961b;font-size:27rpx;flex-shrink:0;}
.compact-category{display:inline-block;margin-top:10rpx;padding:4rpx 12rpx;border-radius:10rpx;background:#f1f3f0;color:#69736c;font-size:22rpx;}
.compact-description{display:block;margin-top:22rpx;color:#778079;font-size:26rpx;line-height:1.5;white-space:pre-wrap;}
.compact-detail .requirement-band{background:transparent!important;margin:12rpx 0 0!important;padding:0 16rpx 8rpx!important;}
.compact-detail .section-title{color:#172b25;font-size:28rpx;padding:12rpx 0;}
.compact-detail .requirement-list{gap:8rpx;padding-top:0;}
.compact-detail .requirement-check{width:30rpx;height:30rpx;background:transparent;border:1rpx solid #448566;}
.compact-detail .requirement-text{color:#778079;font-size:25rpx;line-height:1.5;}
.compact-detail .action-band{padding:0 8rpx!important;margin-top:20rpx;background:transparent!important;}
.compact-detail .form-heading{margin:12rpx 0;font-size:28rpx;}
.compact-detail .completion-options{gap:40rpx;padding:4rpx 0 14rpx;}
.compact-detail .completion-label{font-size:24rpx;}
.compact-detail .radio-dot{width:28rpx;height:28rpx;}
.compact-detail .radio-dot.on{border-width:9rpx;}
.compact-detail .checkin-input{height:132rpx;padding:16rpx!important;font-size:25rpx;}
.compact-detail .input-counter{margin:8rpx 0 12rpx;}
.compact-detail .pending-image-grid{padding:0;}
.compact-detail .pending-image-grid.empty-upload{display:block;}
.compact-detail .add-image{min-height:96rpx;aspect-ratio:auto;border-style:dashed;}
.compact-detail .upload-hint{font-size:20rpx;margin-top:8rpx;}
.compact-detail .primary-action{position:static!important;width:100%!important;margin:16rpx 0 0!important;}
.compact-detail.page-shell{padding-bottom:calc(48rpx + env(safe-area-inset-bottom))!important;}
.compact-links{display:flex;justify-content:center;gap:30rpx;margin:16rpx 0;}
.compact-links button{margin:0;padding:0 12rpx;background:transparent;color:#557263;font-size:23rpx;line-height:48rpx;}
.compact-links button::after{border:0;}
</style>
