<template>
  <view class="page-shell">
    <view v-if="capabilityLoading" class="state-view">正在检查功能状态</view>
    <template v-else-if="enabled">
      <scroll-view v-if="reviews.length" class="date-strip" scroll-x>
        <view class="date-options">
          <button
            v-for="review in reviews"
            :key="review.reviewId"
            class="date-option"
            :class="{ active: review.reviewId === selectedReviewId }"
            @tap="selectReview(review.reviewId)"
          >{{ formatDate(review.periodStart) }}</button>
        </view>
      </scroll-view>

      <view v-if="errorMessage" class="error-band">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view v-if="detail" class="review-content">
        <view class="summary-band">
          <view class="summary-heading">
            <view>
              <text class="student-name">{{ detail.studentName }}</text>
              <text class="review-date">{{ detail.periodStart }}</text>
            </view>
            <text class="version-label">第 {{ detail.contentVersion }} 版</text>
          </view>
          <view class="completion-row">
            <view class="completion-ring">
              <text>{{ percent(detail.completionRate) }}</text>
              <small>完成率</small>
            </view>
            <view class="metric-grid">
              <view><strong>{{ detail.earnedPoints }}</strong><text>获取积分</text></view>
              <view><strong>{{ detail.inProgressCount }}</strong><text>进行中</text></view>
              <view><strong>{{ detail.pendingOptimizationCount }}</strong><text>待优化</text></view>
              <view><strong>{{ detail.pauseCount }}</strong><text>暂停次数</text></view>
            </view>
          </view>
        </view>

        <view class="section-band">
          <text class="section-title">任务分类</text>
          <view v-for="category in detail.categories" :key="category.categoryCode" class="category-row">
            <text>{{ category.categoryCode }}</text>
            <view class="category-track"><i :style="{ width: categoryWidth(category) }" /></view>
            <text>{{ category.completedCount }}/{{ category.taskCount }}</text>
          </view>
          <view v-if="!detail.categories.length" class="empty-line">暂无分类统计</view>
        </view>

        <view class="section-band supplement-section">
          <view class="section-heading">
            <text class="section-title">补录记录</text>
            <button class="add-button" @tap="editing = !editing">{{ editing ? '取消' : '补录' }}</button>
          </view>
          <view v-if="editing" class="supplement-form">
            <picker :range="typeLabels" :value="typeIndex" @change="changeType">
              <view class="picker-field">{{ typeLabels[typeIndex] }}<text>⌄</text></view>
            </picker>
            <textarea
              v-model="supplementContent"
              class="content-input"
              maxlength="1000"
              placeholder="记录今天的观察、优势或下一步计划"
            />
            <button class="submit-button" :disabled="submitting || !supplementContent.trim()" @tap="submitSupplement">
              {{ submitting ? '正在提交' : '确认追加' }}
            </button>
          </view>
          <view v-for="item in detail.supplements" :key="item.id" class="supplement-row">
            <view>
              <text class="supplement-type">{{ typeLabel(item.supplementType) }}</text>
              <text class="supplement-content">{{ item.content }}</text>
            </view>
            <text class="editor-label">{{ item.editorRole === 'PARENT' ? '家长' : '我' }}</text>
          </view>
          <view v-if="!detail.supplements.length && !editing" class="empty-line">暂无补录</view>
        </view>
      </view>

      <view v-else-if="!loading && !errorMessage" class="state-view">暂无成长复盘</view>
      <view v-if="loading" class="loading-mask">正在加载</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  addMyGrowthReviewSupplement,
  getMyGrowthReview,
  listMyDailyGrowthReviews,
  type GrowthReviewDetail,
  type GrowthReviewSummary,
  type GrowthReviewSupplementType
} from '@/api/growth-review';
import { getStudentSession } from '@/session/student-session';

const supplementTypes: GrowthReviewSupplementType[] = ['INSIGHT', 'STRENGTH_WEAKNESS', 'NEXT_PLAN'];
const typeLabels = ['成长观察', '优势与待提升', '下一步计划'];
const capabilityLoading = ref(true);
const enabled = ref(false);
const loading = ref(false);
const errorMessage = ref('');
const reviews = ref<GrowthReviewSummary[]>([]);
const selectedReviewId = ref('');
const detail = ref<GrowthReviewDetail | null>(null);
const editing = ref(false);
const submitting = ref(false);
const typeIndex = ref(0);
const supplementContent = ref('');

onShow(() => void initialize());
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
    enabled.value = capabilities.dailyGrowthReviewEnabled;
    if (!enabled.value) {
      await uni.reLaunch({ url: '/pages/student-home/student-home' });
      return;
    }
    await loadReviews();
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    capabilityLoading.value = false;
  }
}

async function loadReviews(): Promise<void> {
  loading.value = true;
  try {
    const page = await listMyDailyGrowthReviews();
    reviews.value = page.items;
    const targetId = page.items.some((item) => item.reviewId === selectedReviewId.value)
      ? selectedReviewId.value : page.items[0]?.reviewId;
    if (targetId) await selectReview(targetId);
    else detail.value = null;
  } finally {
    loading.value = false;
  }
}

async function selectReview(reviewId: string): Promise<void> {
  selectedReviewId.value = reviewId;
  loading.value = true;
  errorMessage.value = '';
  try {
    detail.value = await getMyGrowthReview(reviewId);
    editing.value = false;
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    loading.value = false;
  }
}

function changeType(event: { detail: { value: string | number } }): void {
  typeIndex.value = Number(event.detail.value);
}

async function submitSupplement(): Promise<void> {
  const content = supplementContent.value.trim();
  if (!detail.value || !content || submitting.value) return;
  submitting.value = true;
  try {
    await addMyGrowthReviewSupplement(
      detail.value.reviewId, supplementTypes[typeIndex.value], content
    );
    supplementContent.value = '';
    editing.value = false;
    detail.value = await getMyGrowthReview(detail.value.reviewId);
    uni.showToast({ title: '补录已追加', icon: 'success' });
  } catch (error) {
    uni.showToast({ title: toMessage(error), icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

function reload(): void { void initialize(); }
function percent(rate: number): string { return `${(rate * 100).toFixed(0)}%`; }
function formatDate(value: string): string { return value.slice(5).replace('-', '/'); }
function categoryWidth(category: { taskCount: number; completedCount: number }): string {
  return `${category.taskCount ? Math.max(3, category.completedCount / category.taskCount * 100) : 0}%`;
}
function typeLabel(type: GrowthReviewSupplementType): string {
  return typeLabels[supplementTypes.indexOf(type)] || type;
}
function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '成长复盘加载失败';
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding-bottom: 48rpx; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.date-strip { width: 100%; background: #ffffff; border-bottom: 2rpx solid #dce4e1; white-space: nowrap; }
.date-options { display: inline-flex; gap: 12rpx; padding: 20rpx 28rpx; }
.date-option { width: 132rpx; height: 62rpx; margin: 0; padding: 0; border-radius: 8rpx; background: #f0f4f2; color: #63736d; font-size: 25rpx; }
.date-option::after { border: 0; }
.date-option.active { background: #167c5a; color: #ffffff; }
.summary-band, .section-band { background: #ffffff; }
.summary-band { padding: 34rpx 36rpx; border-bottom: 2rpx solid #dce4e1; }
.summary-heading, .section-heading { display: flex; justify-content: space-between; align-items: center; gap: 20rpx; }
.student-name, .review-date, .metric-grid text, .supplement-type, .supplement-content { display: block; }
.student-name { color: #1c2b28; font-size: 34rpx; font-weight: 700; }
.review-date { margin-top: 6rpx; color: #708078; font-size: 23rpx; }
.version-label { padding: 8rpx 14rpx; border-radius: 6rpx; background: #e8f4ee; color: #116847; font-size: 22rpx; }
.completion-row { display: grid; grid-template-columns: 150rpx minmax(0, 1fr); gap: 34rpx; align-items: center; margin-top: 32rpx; }
.completion-ring { width: 146rpx; height: 146rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 14rpx solid #6ab493; border-radius: 50%; box-sizing: border-box; }
.completion-ring text { color: #155c43; font-size: 32rpx; font-weight: 700; }
.completion-ring small { color: #718179; font-size: 20rpx; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24rpx; }
.metric-grid strong { color: #1c2b28; font-size: 32rpx; }
.metric-grid text { margin-top: 3rpx; color: #718179; font-size: 21rpx; }
.section-band { margin-top: 20rpx; padding: 28rpx 36rpx; }
.section-title { color: #263730; font-size: 28rpx; font-weight: 650; }
.category-row { display: grid; grid-template-columns: 160rpx minmax(80rpx, 1fr) 64rpx; gap: 18rpx; align-items: center; min-height: 72rpx; color: #536760; font-size: 24rpx; }
.category-track { height: 12rpx; overflow: hidden; border-radius: 6rpx; background: #e5ece8; }
.category-track i { height: 100%; display: block; border-radius: 6rpx; background: #3278b5; }
.add-button { width: 112rpx; height: 58rpx; margin: 0; padding: 0; border-radius: 8rpx; background: #e8f4ee; color: #116847; font-size: 24rpx; }
.add-button::after { border: 0; }
.supplement-form { display: grid; gap: 18rpx; margin-top: 24rpx; padding-bottom: 24rpx; border-bottom: 2rpx solid #e5ece8; }
.picker-field, .content-input { width: 100%; box-sizing: border-box; border: 2rpx solid #d4dfda; border-radius: 8rpx; background: #ffffff; color: #263730; font-size: 25rpx; }
.picker-field { height: 72rpx; display: flex; align-items: center; justify-content: space-between; padding: 0 22rpx; }
.content-input { height: 190rpx; padding: 20rpx; }
.submit-button { width: 100%; height: 76rpx; margin: 0; border-radius: 8rpx; background: #167c5a; color: #ffffff; font-size: 27rpx; }
.submit-button::after { border: 0; }
.supplement-row { display: flex; justify-content: space-between; gap: 24rpx; padding: 24rpx 0; border-bottom: 2rpx solid #e5ece8; }
.supplement-row > view { min-width: 0; }
.supplement-type { color: #263730; font-size: 25rpx; font-weight: 600; }
.supplement-content { margin-top: 8rpx; color: #5f7069; font-size: 24rpx; line-height: 1.55; overflow-wrap: anywhere; }
.editor-label { flex: 0 0 auto; color: #8a5a38; font-size: 21rpx; }
.empty-line, .state-view, .loading-mask { min-height: 180rpx; display: flex; align-items: center; justify-content: center; color: #718179; font-size: 25rpx; }
.error-band { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 20rpx 32rpx; background: #fff1ed; color: #9a4b38; font-size: 24rpx; }
.retry-button { width: 120rpx; height: 56rpx; margin: 0; border-radius: 8rpx; background: #ffffff; color: #9a4b38; font-size: 24rpx; }
.retry-button::after { border-color: #e8b4a8; }
</style>
