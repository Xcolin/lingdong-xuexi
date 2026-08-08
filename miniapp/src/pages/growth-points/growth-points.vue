<template>
  <view class="page-shell">
    <view v-if="capabilityLoading" class="state-view">
      <text>正在检查功能状态</text>
    </view>
    <template v-else-if="growthPointEnabled">
      <view class="account-band">
        <view>
          <text class="student-name">{{ account?.studentName || '我的积分' }}</text>
          <text class="updated-at">{{ account ? `更新于 ${formatDateTime(account.updatedAt)}` : '' }}</text>
        </view>
        <view class="balance-row">
          <view class="balance-item">
            <text class="balance-label">累计积分</text>
            <text class="balance-value">{{ account?.totalPoints ?? 0 }}</text>
          </view>
          <view class="balance-item">
            <text class="balance-label">可用积分</text>
            <text class="balance-value available">{{ account?.availablePoints ?? 0 }}</text>
          </view>
        </view>
      </view>

      <view v-if="errorMessage" class="error-band">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view class="section-heading">积分明细</view>
      <view class="ledger-list">
        <view v-for="ledger in ledgers" :key="ledger.id" class="ledger-row">
          <view class="ledger-main">
            <view class="ledger-title-row">
              <text class="ledger-title">{{ ledgerTitle(ledger) }}</text>
              <text class="ledger-amount" :class="{ negative: ledger.amount < 0 }">
                {{ ledger.amount > 0 ? '+' : '' }}{{ ledger.amount }}
              </text>
            </view>
            <view class="ledger-meta">
              <text v-if="ledger.sourceType" class="source-label" :class="`source-${ledger.sourceType.toLowerCase()}`">
                {{ sourceLabel(ledger.sourceType) }}
              </text>
              <text>{{ changeLabel(ledger.changeType) }}</text>
              <text>{{ formatDateTime(ledger.occurredAt) }}</text>
            </view>
            <text v-if="ledger.reviewerDisplayName" class="reviewer-name">
              审核人：{{ ledger.reviewerDisplayName }}
            </text>
            <text v-if="decaySummary(ledger)" class="reviewer-name">
              {{ decaySummary(ledger) }}
            </text>
            <text v-if="ledger.changeType === 'CORRECTION' && ledger.remark" class="correction-reason">
              纠错原因：{{ ledger.remark }}
            </text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="list-footer">正在加载</view>
      <view v-else-if="hasMore" class="list-footer">继续上滑加载</view>
      <view v-else-if="ledgers.length" class="list-footer">已加载全部积分明细</view>
      <view v-else-if="!errorMessage" class="state-view compact">
        <text class="state-title">暂无积分变动</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  getMyGrowthPointAccount,
  listMyGrowthPointLedgers,
  type GrowthPointAccount,
  type GrowthPointChangeType,
  type GrowthPointLedger,
  type GrowthPointSourceType
} from '@/api/growth-point';
import { getStudentSession } from '@/session/student-session';

const PAGE_SIZE = 20;
const capabilityLoading = ref(true);
const growthPointEnabled = ref(false);
const loading = ref(false);
const errorMessage = ref('');
const account = ref<GrowthPointAccount | null>(null);
const ledgers = ref<GrowthPointLedger[]>([]);
const page = ref(1);
const total = ref(0);
const hasMore = computed(() => ledgers.value.length < total.value);

onShow(() => {
  void initialize();
});

onPullDownRefresh(async () => {
  await initialize();
  uni.stopPullDownRefresh();
});

onReachBottom(() => {
  if (hasMore.value) {
    void loadLedgers(false);
  }
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
    growthPointEnabled.value = capabilities.growthPointQueryEnabled;
    if (!growthPointEnabled.value) {
      await uni.reLaunch({ url: '/pages/student-home/student-home' });
      return;
    }
    await Promise.all([loadAccount(), loadLedgers(true)]);
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    capabilityLoading.value = false;
  }
}

async function loadAccount(): Promise<void> {
  account.value = await getMyGrowthPointAccount();
}

async function loadLedgers(reset: boolean): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  const nextPage = reset ? 1 : page.value + 1;
  try {
    const result = await listMyGrowthPointLedgers(nextPage, PAGE_SIZE);
    ledgers.value = reset ? result.items : [...ledgers.value, ...result.items];
    page.value = result.page;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function reload(): void {
  void initialize();
}

function sourceLabel(source: GrowthPointSourceType): string {
  return source === 'FAMILY' ? '家庭' : source === 'ORGANIZATION' ? '机构' : '教师';
}

function changeLabel(changeType: GrowthPointChangeType): string {
  const labels: Record<GrowthPointChangeType, string> = {
    TASK_REWARD: '任务奖励',
    REDEMPTION: '积分兑换',
    DORMANCY_CLEAR: '休眠清理',
    CORRECTION: '台账更正'
  };
  return labels[changeType];
}

function ledgerTitle(ledger: GrowthPointLedger): string {
  if (ledger.changeType === 'REDEMPTION' && ledger.sourceExchangeId && ledger.remark) {
    return ledger.remark;
  }
  return ledger.taskTitle || changeLabel(ledger.changeType);
}

function decaySummary(ledger: GrowthPointLedger): string {
  if (ledger.changeType !== 'TASK_REWARD' || !ledger.basePointsSnapshot || !ledger.streakDays) {
    return '';
  }
  return `连续第 ${ledger.streakDays} 天 · 基础 ${ledger.basePointsSnapshot} 分 · 衰减 ${ledger.decayPercent ?? 0}%`;
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '积分数据加载失败';
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding-bottom: 48rpx; background: #f4f7f5; }

/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */

.account-band { padding: 36rpx 40rpx 34rpx; background: #ffffff; border-bottom: 2rpx solid #dce4e1; }
.student-name, .updated-at, .balance-label, .balance-value { display: block; }
.student-name { color: #1c2b28; font-size: 32rpx; font-weight: 650; }
.updated-at { min-height: 34rpx; margin-top: 8rpx; color: #708078; font-size: 22rpx; }
.balance-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 32rpx; margin-top: 32rpx; }
.balance-item { min-width: 0; }
.balance-label { color: #708078; font-size: 24rpx; }
.balance-value { margin-top: 8rpx; color: #1c2b28; font-size: 48rpx; font-weight: 700; line-height: 1.2; }
.balance-value.available { color: #167c5a; }

.section-heading { padding: 26rpx 36rpx 18rpx; color: #536760; font-size: 26rpx; font-weight: 600; }
.ledger-list { background: #ffffff; }
.ledger-row { min-height: 162rpx; padding: 26rpx 36rpx; box-sizing: border-box; border-bottom: 2rpx solid #e5ece8; }
.ledger-main { min-width: 0; }
.ledger-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 24rpx; }
.ledger-title { min-width: 0; color: #1c2b28; font-size: 30rpx; font-weight: 650; line-height: 1.4; overflow-wrap: anywhere; }
.ledger-amount { flex: 0 0 auto; color: #167c5a; font-size: 32rpx; font-weight: 700; }
.ledger-amount.negative { color: #c13f3f; }
.ledger-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12rpx 20rpx; margin-top: 14rpx; color: #708078; font-size: 22rpx; }
.source-label { padding: 4rpx 10rpx; border-radius: 6rpx; }
.source-family { background: #fceff3; color: #9d3658; }
.source-organization { background: #edf3ff; color: #315f9f; }
.source-teacher { background: #e9f7f5; color: #147069; }
.reviewer-name { display: block; margin-top: 12rpx; color: #536760; font-size: 23rpx; }
.correction-reason { display: block; margin-top: 10rpx; color: #8f4b3d; font-size: 23rpx; line-height: 1.5; overflow-wrap: anywhere; }

.list-footer, .state-view { min-height: 180rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 26rpx; }
.state-view.compact { min-height: 280rpx; }
.state-title { color: #536760; font-size: 30rpx; }
.error-band { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 20rpx 32rpx; background: #fff1ed; color: #9a4b38; font-size: 24rpx; }
.retry-button { width: 120rpx; height: 56rpx; margin: 0; border-radius: 8rpx; background: #ffffff; color: #9a4b38; font-size: 26rpx; }
.retry-button::after { border-color: #e8b4a8; }
</style>
