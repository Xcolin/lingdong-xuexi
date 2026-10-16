<template>
  <view class="prototype-workspace ld-page">
    <PrototypePageHeader title="成长积分" icon="chart"/>
    <view class="ld-heading">
      <text class="ld-heading-title">我的积分</text>
      <view class="ld-heading-rule" />
    </view>
    <view v-if="capabilityLoading" class="ld-loading">正在检查功能状态</view>
    <template v-else-if="growthPointEnabled">
      <view class="account-band">
        <view class="account-left">
          <text class="student-name">{{ account?.studentName || '我的积分' }}</text>
          <text class="updated-at">{{ account ? `更新于 ${formatDateTime(account.updatedAt)}` : '' }}</text>
        </view>
        <view class="balance-row"><button class="redeem-link" @tap="openRewards">去兑换</button>
          <view class="balance-item">
            <text class="balance-label">累计积分</text>
            <text class="balance-value">{{ account?.totalPoints ?? '—' }}</text>
          </view>
          <view class="balance-divider" /><view class="balance-item"><text class="balance-label">本周获得</text><text class="balance-value">{{ account?.weekEarnedPoints ?? '—' }}</text></view><view class="balance-item"><text class="balance-label">本月获得</text><text class="balance-value">{{ account?.monthEarnedPoints ?? '—' }}</text></view>
          <view class="balance-item">
            <text class="balance-label">可用积分</text>
            <text class="balance-value">{{ account?.availablePoints ?? '—' }}</text>
          </view>
        </view>
      </view>

      <view v-if="errorMessage" class="ld-card error-band">
        <text class="error-text">{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view class="ld-heading">
        <text class="ld-heading-title">积分明细</text>
      </view>
      <view class="ledger-list">
        <view v-for="ledger in ledgers" :key="ledger.id" class="ledger-card">
          <view class="visual-tile"><PrototypeIcon name="chart" :size="30" tone="amber" /></view><view class="ledger-main">
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

      <view v-if="loading" class="ld-loading">正在加载</view>
      <view v-else-if="hasMore" class="list-footer">继续上滑加载</view>
      <view v-else-if="ledgers.length" class="list-footer">已加载全部积分明细</view>
      <view v-else-if="!errorMessage" class="ld-empty">
        <text class="ld-empty-main">暂无积分变动</text>
        <text class="ld-empty-sub">完成任务获得积分后会显示在这里</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import PrototypePageHeader from "@/components/PrototypePageHeader.vue";
import PrototypeIcon from '@/components/PrototypeIcon.vue';
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

function openRewards(): void { uni.navigateTo({url:'/pages/rewards/rewards'}); }
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
      await uni.reLaunch({ url: '/pages/student-dashboard/student-dashboard' });
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

.account-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 24rpx;
  padding: 36rpx 32rpx;
  border-radius: 28rpx;
  background: $ld-gradient-primary;
  box-shadow: 0 10rpx 30rpx rgba(22, 124, 90, 0.25);
  color: #ffffff;
}
.account-left { min-width: 0; }
.student-name, .updated-at { display: block; }
.student-name { font-size: 32rpx; font-weight: 700; overflow-wrap: anywhere; }
.updated-at { margin-top: 10rpx; font-size: $ld-font-mini; opacity: 0.85; }
.balance-row { display: flex; align-items: center; gap: 28rpx; flex-shrink: 0; }
.balance-divider { width: 2rpx; height: 64rpx; background: rgba(255, 255, 255, 0.28); }
.balance-item { text-align: center; }
.balance-label { display: block; font-size: $ld-font-mini; opacity: 0.85; }
.balance-value { display: block; margin-top: 8rpx; font-size: 44rpx; font-weight: 700; line-height: 1; }
.error-band { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; background: $ld-danger-soft; }
.error-text { color: $ld-danger; font-size: $ld-font-caption; flex: 1; }
.retry-button {
  width: 120rpx; height: 56rpx; display: flex; align-items: center; justify-content: center;
  margin: 0; border-radius: $ld-radius-pill; background: $ld-card; color: $ld-danger;
  font-size: $ld-font-caption; flex-shrink: 0;
}
.retry-button::after { border: 0; }
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
.list-footer { padding: 32rpx 0; color: $ld-text-muted; font-size: $ld-font-caption; text-align: center; }

@import "@/styles/workspace-prototype.scss";

.prototype-workspace{background:#f4f8f3;color:#163d30;}
.profile-hero{display:flex;align-items:center;gap:24rpx;padding:44rpx 18rpx;}
.task-card{position:relative;padding:24rpx 24rpx 24rpx 108rpx!important;border:0!important;border-radius:24rpx!important;background:#fff!important;margin-bottom:16rpx!important;box-shadow:none!important;}
.task-symbol{position:absolute;left:22rpx;top:30rpx;width:66rpx;height:66rpx;display:flex;align-items:center;justify-content:center;background:#edf6ed;border-radius:16rpx;color:#126044;}
.task-title{font-size:30rpx!important;font-weight:650;}
.source-button{border-radius:40rpx!important;}
.source-button.active{background:#125e43!important;color:white!important;}
.balance-band,.account-band,.summary-band{background:linear-gradient(125deg,#d9ebd5,#f2f7e9)!important;border-radius:28rpx!important;border:0!important;padding:32rpx!important;}
.balance-band{position:relative;}
.balance-value{font-size:56rpx!important;color:#12533d!important;}
.reward-item,.ledger-card,.detail-band{border:0!important;border-radius:24rpx!important;background:#fff!important;margin-bottom:18rpx!important;box-shadow:none!important;}
.title-band{background:#fff!important;border-radius:26rpx!important;padding:28rpx!important;}
.detail-symbol{width:76rpx;height:76rpx;background:#e5f3ed;border-radius:20rpx;display:flex;align-items:center;justify-content:center;color:#0e6547;margin-bottom:18rpx;}

.reward-item,.ledger-card{position:relative;padding-left:116rpx!important;min-height:100rpx;}.visual-tile{position:absolute;left:22rpx;top:26rpx;width:74rpx;height:74rpx;border-radius:18rpx;background:#fff2d6;display:flex;align-items:center;justify-content:center;}.exchange-button{background:linear-gradient(110deg,#ffb43e,#ed920c)!important;color:white!important;border-radius:32rpx!important;}.points-badge{color:#ec9315!important;background:#fff6e8!important;}
.prototype-workspace{background:#F6F8F5!important;padding:28rpx 28rpx calc(160rpx + env(safe-area-inset-bottom))!important;box-sizing:border-box;}.ld-heading{display:none;}.summary-band,.section-band,.balance-band,.reward-item,.exchange-item{border:1rpx solid #e1e9e2!important;border-radius:24rpx!important;background:#fffefa!important;box-sizing:border-box;}.metric-grid{min-width:0;}.category-row{gap:14rpx;}.category-row>text:first-child{max-width:180rpx;overflow-wrap:anywhere;}.category-track{min-width:0;}.date-option{flex-shrink:0;}.tab-button{min-width:0;padding-left:8rpx;padding-right:8rpx;}.item-title{overflow-wrap:anywhere;}.supplement-form textarea{box-sizing:border-box;width:100%;}.state-view{background:#fffefa;border-radius:24rpx;color:#809486;}
.account-band{background:linear-gradient(110deg,#12664f,#3b8b6b)!important;color:white;border-radius:24rpx!important;padding:30rpx!important;}.student-name,.balance-label,.balance-value,.updated-at{color:inherit!important;}.balance-value{font-size:54rpx!important;}.balance-row{position:relative;}.redeem-link{position:absolute;right:0;top:-66rpx;background:#ffc940;color:#123c2a;font-size:24rpx;border-radius:40rpx;padding:0 24rpx;line-height:58rpx;}.ledger-card{background:white!important;border:0!important;border-bottom:1rpx solid #edf0e8!important;border-radius:0!important;padding:22rpx 22rpx 22rpx 110rpx!important;margin:0!important;}.visual-tile{background:#e1f3e8!important;width:64rpx;height:64rpx;border-radius:50%;}.ledger-amount{color:#f49a00!important;font-size:30rpx;}.ledger-amount.negative{color:#af5d4d!important;}.ledger-title{font-size:27rpx;}.ld-heading{display:flex!important;margin:28rpx 0 18rpx;}.ld-heading:first-of-type{display:none!important;}
</style>
