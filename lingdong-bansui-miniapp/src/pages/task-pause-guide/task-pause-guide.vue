<template>
  <view class="prototype-workspace page-shell">
    <PixelPageHeader :title="mode === 'EMOTION' ? '情绪暂停' : '任务暂时搁置'" back fallback="/pages/task-detail/task-detail" />
    <view v-if="loading" class="state-view"><text>正在加载任务</text></view>
    <view v-else-if="errorMessage && !task" class="state-view error-view">
      <text>{{ errorMessage }}</text>
      <button class="retry-button" @tap="reload">重试</button>
    </view>
    <template v-else-if="task">
      <!-- 情绪暂停引导（原型 05-06）：插图 + 引导文案 + 四调节方式卡 -->
      <template v-if="mode === 'EMOTION'">
        <view class="hero-illustration">
          <image class="hero-image" src="/static/growth-botanical.svg" mode="aspectFit" />
        </view>
        <text class="guide-title">先停一停，照顾一下自己的情绪</text>
        <text class="guide-subtitle">当您感到不开心、着急或有压力时，可以选择一个方式，让自己平静下来。</text>
        <view class="method-grid">
          <view
            v-for="method in emotionMethods"
            :key="method.label"
            class="method-card"
            :class="{ selected: selectedMethod === method.label }"
            @tap="selectedMethod = method.label"
          >
            <view class="method-icon"><PrototypeIcon :name="method.icon" :size="40"/></view>
            <text class="method-label">{{ method.label }}</text>
            <text class="method-hint">{{ method.hint }}</text>
          </view>
        </view>
        <button
          class="primary-action"
          :loading="working"
          :disabled="working || !selectedMethod"
          @tap="confirmEmotionPause"
        >我感觉好多了</button>
      </template>

      <!-- 难题搁置原因收集（原型 05-07）：四原因 radio + 说明 50 字 -->
      <template v-else>
        <view class="shelve-symbol"><PrototypeIcon name="clock" :size="44"/></view>
        <view class="guide-headline">
          <text class="guide-title">遇到了困难？</text>
          <text class="guide-title">可以先把这个任务暂时放一放</text>
        </view>
        <text class="guide-subtitle">你可以选择一个原因，并在稍后再试。</text>
        <view class="reason-list">
          <label
            v-for="reason in shelveReasons"
            :key="reason"
            class="reason-item"
            @tap="selectedReason = reason"
          >
            <view class="radio-dot" :class="{ on: selectedReason === reason }"/>
            <text class="reason-text">{{ reason }}</text>
          </label>
        </view>
        <view class="note-box">
          <textarea
            v-model="reasonNote"
            class="note-input"
            :maxlength="noteMaxLength"
            placeholder="简单说一说（选填）"
            :disabled="working"
          />
          <text class="note-counter">{{ reasonNote.length }}/{{ noteMaxLength }}</text>
        </view>
        <button
          class="primary-action"
          :loading="working"
          :disabled="working || !selectedReason"
          @tap="confirmShelve"
        >确认暂时搁置</button>
      </template>

      <view v-if="errorMessage" class="inline-error"><text>{{ errorMessage }}</text></view>
    </template>
  </view>
</template>

<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  getStudentTaskAssignment,
  pauseStudentTask,
  type StudentTaskAssignment,
  type TaskPauseType
} from '@/api/learning-task';
import { getStudentSession } from '@/session/student-session';

const emotionMethods = [
  { label: '深呼吸', hint: '1分钟', icon: 'leaf', minutes: 1 },
  { label: '听一段舒缓音乐', hint: '3分钟', icon: 'growth', minutes: 3 },
  { label: '安静待一会儿', hint: '5分钟', icon: 'clock', minutes: 5 },
  { label: '换个积极想法', hint: '看看小贴士', icon: 'star', minutes: 5 }
] as const;
const shelveReasons = [
  '我现在有点情绪不太好',
  '任务有些难，需要更多时间',
  '需要成长伙伴的帮助',
  '其他原因'
] as const;

const assignmentId = ref('');
const mode = ref<TaskPauseType>('EMOTION');
const durationMinutes = ref(30);
const task = ref<StudentTaskAssignment | null>(null);
const loading = ref(true);
const working = ref(false);
const learningTaskEnabled = ref(false);
const errorMessage = ref('');
const selectedMethod = ref('');
const selectedReason = ref('');
const reasonNote = ref('');
// 原因标签与说明合并进 50 字 reasonNote，说明按剩余空间动态限长。
const noteMaxLength = computed(() => {
  const reason = selectedReason.value;
  return reason ? Math.max(0, 50 - reason.length - 1) : 50;
});

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
  mode.value = options?.mode === 'DIFFICULTY' ? 'DIFFICULTY' : 'EMOTION';
  const duration = Number(options?.duration);
  if (Number.isFinite(duration) && duration >= 1 && duration <= 120) durationMinutes.value = duration;
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

async function confirmEmotionPause(): Promise<void> {
  if (working.value || !selectedMethod.value) return;
  const method = emotionMethods.find((item) => item.label === selectedMethod.value);
  if (!method) return;
  await executePause('EMOTION', method.label, null);
}

async function confirmShelve(): Promise<void> {
  if (working.value || !selectedReason.value) return;
  const note = reasonNote.value.trim();
  const reasonNoteCombined = note
    ? `${selectedReason.value}：${note}`.slice(0, 50)
    : selectedReason.value;
  await executePause('DIFFICULTY', null, reasonNoteCombined);
}

async function executePause(
  pauseType: TaskPauseType,
  adjustMethod: string | null,
  reasonNoteValue: string | null
): Promise<void> {
  if (working.value || !learningTaskEnabled.value || !getStudentSession()) return;
  const n = epoch, token = getStudentSession()?.accessToken;
  if (!token) return;
  working.value = true;
  errorMessage.value = '';
  try {
    await pauseStudentTask(assignmentId.value, pauseType, durationMinutes.value, adjustMethod ?? undefined, reasonNoteValue ?? undefined);
    if (!current(n, token)) return;
    await uni.navigateBack();
  } catch (error) {
    if (current(n, token)) {
      errorMessage.value = toMessage(error);
      working.value = false;
    }
  }
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
.hero-illustration { margin-top: 30rpx; height: 300rpx; display: flex; align-items: center; justify-content: center; }
.hero-image { width: 300rpx; height: 300rpx; }
.guide-title { display: block; margin-top: 18rpx; color: #1c2b28; font-size: 34rpx; font-weight: 650; text-align: center; line-height: 1.5; }
.guide-headline { display: flex; flex-direction: column; }
.guide-subtitle { display: block; margin-top: 12rpx; color: #8a9981; font-size: 25rpx; text-align: center; line-height: 1.6; }
.method-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22rpx; margin-top: 34rpx; }
.method-card { padding: 30rpx 20rpx 26rpx; background: #ffffff; border: 3rpx solid transparent; border-radius: 22rpx; display: flex; flex-direction: column; align-items: center; gap: 8rpx; box-sizing: border-box; }
.method-card.selected { border-color: #167c5a; background: #eef7f0; }
.method-icon { width: 72rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; background: #e7f6ed; border-radius: 50%; color: #0e6547; }
.method-label { color: #1c2b28; font-size: 27rpx; font-weight: 600; }
.method-hint { color: #8a9981; font-size: 22rpx; }
.shelve-symbol { margin: 30rpx auto 0; width: 96rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; background: #fff0d7; border-radius: 50%; color: #eea12a; }
.reason-list { margin-top: 34rpx; background: #ffffff; border-radius: 22rpx; padding: 6rpx 30rpx; }
.reason-item { min-height: 92rpx; display: flex; align-items: center; gap: 20rpx; border-bottom: 2rpx solid #eef3f0; }
.reason-item:last-child { border-bottom: 0; }
.radio-dot { width: 34rpx; height: 34rpx; flex-shrink: 0; border-radius: 50%; border: 4rpx solid #c4d2cb; background: #ffffff; box-sizing: border-box; }
.radio-dot.on { border-width: 10rpx; border-color: #167c5a; }
.reason-text { color: #1c2b28; font-size: 28rpx; }
.note-box { margin-top: 26rpx; padding: 24rpx; background: #ffffff; border-radius: 22rpx; }
.note-input { width: 100%; min-height: 140rpx; color: #1c2b28; font-size: 27rpx; line-height: 1.6; }
.note-counter { display: block; margin-top: 12rpx; text-align: right; color: #a8b5ae; font-size: 22rpx; }
.primary-action { width: 100%; height: 88rpx; margin: 40rpx 0 0; border-radius: 44rpx; background: linear-gradient(120deg, #184f40, #256b50); color: #ffffff; font-size: 29rpx; }
.primary-action::after { border: 0; }
.inline-error { margin-top: 22rpx; padding: 18rpx 24rpx; background: #fff1ed; border-radius: 14rpx; color: #9a4b38; font-size: 24rpx; }
</style>
