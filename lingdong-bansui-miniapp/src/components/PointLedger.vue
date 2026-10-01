<template>
  <view class="ledger">
    <view v-if="error" class="state error">{{ error }}</view>
    <view v-else-if="loading && ledgers.length === 0" class="state">正在加载积分明细</view>
    <template v-else>
      <view v-for="ledger in ledgers" :key="ledger.id" class="ledger-card">
        <view class="ledger-title-row">
          <text class="ledger-title">{{ ledgerTitle(ledger) }}</text>
          <text class="ledger-amount" :class="{ negative: ledger.amount < 0 }">
            {{ ledger.amount > 0 ? '+' : '' }}{{ ledger.amount }}
          </text>
        </view>
        <view class="ledger-meta">
          <text v-if="ledger.sourceType" class="source-label">{{ sourceLabel(ledger.sourceType) }}</text>
          <text>{{ changeLabel(ledger.changeType) }}</text>
          <text>{{ formatDateTime(ledger.occurredAt) }}</text>
        </view>
        <text v-if="ledger.reviewerDisplayName" class="reviewer-name">审核人：{{ ledger.reviewerDisplayName }}</text>
        <text v-if="decaySummary(ledger)" class="reviewer-name">{{ decaySummary(ledger) }}</text>
        <text v-if="ledger.changeType === 'CORRECTION' && ledger.remark" class="correction-reason">
          纠错原因：{{ ledger.remark }}
        </text>
      </view>

      <view v-if="loading" class="state">正在加载</view>
      <button v-else-if="hasMore" class="load-more" @tap="loadMore">加载更多</button>
      <view v-else-if="ledgers.length" class="state">已加载全部积分明细</view>
      <view v-else class="state">暂无积分变动，完成任务后会显示在这里</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  getMyGrowthPointAccount,
  listMyGrowthPointLedgers,
  type GrowthPointAccount,
  type GrowthPointChangeType,
  type GrowthPointLedger,
  type GrowthPointSourceType
} from '@/api/growth-point';

const PAGE_SIZE = 20;
const loading = ref(false);
const error = ref('');
const account = ref<GrowthPointAccount | null>(null);
const ledgers = ref<GrowthPointLedger[]>([]);
const page = ref(1);
const total = ref(0);
const hasMore = computed(() => ledgers.value.length < total.value);

onMounted(() => {
  void initialize();
});

async function initialize(): Promise<void> {
  error.value = '';
  try {
    await Promise.all([loadAccount(), loadLedgers(true)]);
  } catch (cause) {
    error.value = toMessage(cause);
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

function loadMore(): void {
  void loadLedgers(false);
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

function toMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : '积分数据加载失败';
}
</script>

<style lang="scss" scoped>
.state { padding: 40rpx 0; color: $ld-text-muted; font-size: $ld-font-body; text-align: center; }
.state.error { color: $ld-danger; }
.ledger-card {
  margin-top: $ld-gap-block;
  padding: 26rpx 28rpx;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
}
.ledger-title-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.ledger-title { color: $ld-text; font-size: 29rpx; font-weight: 600; overflow-wrap: anywhere; }
.ledger-amount { color: $ld-primary; font-size: 34rpx; font-weight: 700; flex-shrink: 0; }
.ledger-amount.negative { color: $ld-danger; }
.ledger-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx 20rpx; margin-top: 14rpx; color: $ld-text-muted; font-size: $ld-font-caption; }
.ledger-meta text { display: block; }
.source-label { padding: 4rpx 14rpx; border-radius: $ld-radius-pill; background: $ld-primary-soft; color: $ld-primary; font-size: $ld-font-mini; font-weight: 600; }
.reviewer-name { display: block; margin-top: 12rpx; color: $ld-text-secondary; font-size: $ld-font-caption; }
.correction-reason { display: block; margin-top: 10rpx; color: $ld-accent; font-size: $ld-font-caption; }
.load-more {
  width: 260rpx; height: 68rpx; display: flex; align-items: center; justify-content: center;
  margin: 28rpx auto 0; border-radius: $ld-radius-pill; background: $ld-card;
  color: $ld-primary; font-size: $ld-font-caption; border: 2rpx solid $ld-primary-border;
}
.load-more::after { border: 0; }
</style>
