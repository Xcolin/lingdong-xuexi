<template>
  <view class="page-shell">
    <view v-if="capabilityLoading" class="state-view">
      <text>正在检查功能状态</text>
    </view>
    <template v-else-if="rewardExchangeEnabled">
      <view class="balance-band">
        <text class="balance-label">当前可用积分</text>
        <view class="balance-value-row">
          <text class="balance-value">{{ summary?.availablePoints ?? 0 }}</text>
          <text class="balance-unit">分</text>
        </view>
        <text class="balance-time">
          {{ summary ? `更新于 ${formatDateTime(summary.updatedAt)}` : '' }}
        </text>
      </view>

      <view class="tab-bar" role="tablist">
        <button
          class="tab-button"
          :class="{ active: activeTab === 'rewards' }"
          :aria-selected="activeTab === 'rewards'"
          @tap="activeTab = 'rewards'"
        >可兑换奖励</button>
        <button
          class="tab-button"
          :class="{ active: activeTab === 'exchanges' }"
          :aria-selected="activeTab === 'exchanges'"
          @tap="activeTab = 'exchanges'"
        >我的兑换</button>
      </view>

      <view v-if="errorMessage" class="error-band">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view v-if="activeTab === 'rewards'" class="content-list">
        <view v-for="reward in rewards" :key="reward.id" class="reward-item">
          <view class="item-heading">
            <text class="item-title">{{ reward.rewardName }}</text>
            <text class="points-badge">{{ reward.requiredPoints }} 分</text>
          </view>
          <text v-if="reward.description" class="item-description">{{ reward.description }}</text>
          <text class="item-meta">
            {{ reward.expiresAt ? `有效期至 ${formatDateTime(reward.expiresAt)}` : '长期有效' }}
          </text>
          <button
            class="exchange-button"
            :disabled="isExchangeDisabled(reward)"
            @tap="confirmExchange(reward)"
          >
            {{ summary && summary.availablePoints < reward.requiredPoints ? '积分不足' : '申请兑换' }}
          </button>
        </view>
        <view v-if="!loading && rewards.length === 0 && !errorMessage" class="state-view compact">
          <text class="state-title">暂无可兑换奖励</text>
          <text class="state-description">家长上架奖励后会显示在这里</text>
        </view>
      </view>

      <view v-else class="content-list">
        <view v-for="exchange in exchanges" :key="exchange.id" class="exchange-item">
          <view class="item-heading">
            <text class="item-title">{{ exchange.rewardName }}</text>
            <text class="status-badge" :class="`status-${exchange.status.toLowerCase()}`">
              {{ statusLabel(exchange.status) }}
            </text>
          </view>
          <text class="exchange-points">兑换积分：{{ exchange.requiredPoints }}</text>
          <text v-if="exchange.description" class="item-description">{{ exchange.description }}</text>
          <view class="exchange-time-list">
            <text>申请时间：{{ formatDateTime(exchange.requestedAt) }}</text>
            <text v-if="exchange.status === 'PENDING_APPROVAL'">
              审批截止：{{ formatDateTime(exchange.approvalDeadline) }}
            </text>
            <text v-if="exchange.reviewedAt">处理时间：{{ formatDateTime(exchange.reviewedAt) }}</text>
            <text v-if="exchange.verifiedAt">核销时间：{{ formatDateTime(exchange.verifiedAt) }}</text>
          </view>
          <text v-if="exchange.rejectReason" class="reject-reason">
            驳回原因：{{ exchange.rejectReason }}
          </text>
        </view>
        <view v-if="!loading && exchanges.length === 0 && !errorMessage" class="state-view compact">
          <text class="state-title">暂无兑换记录</text>
        </view>
      </view>

      <view v-if="loading" class="loading-footer">正在加载奖励数据</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  applyRewardExchange,
  getMyRewardAccountSummary,
  listMyRewardExchanges,
  listMyRewards,
  type RewardAccountSummary,
  type RewardExchangeStatus,
  type StudentReward,
  type StudentRewardExchange
} from '@/api/reward';
import { getStudentSession } from '@/session/student-session';

const capabilityLoading = ref(true);
const rewardExchangeEnabled = ref(false);
const loading = ref(false);
const applyingRewardId = ref('');
const errorMessage = ref('');
const activeTab = ref<'rewards' | 'exchanges'>('rewards');
const summary = ref<RewardAccountSummary | null>(null);
const rewards = ref<StudentReward[]>([]);
const exchanges = ref<StudentRewardExchange[]>([]);

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
    rewardExchangeEnabled.value = capabilities.rewardExchangeEnabled;
    if (!rewardExchangeEnabled.value) {
      await uni.reLaunch({ url: '/pages/student-home/student-home' });
      return;
    }
    await loadRewardData();
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    capabilityLoading.value = false;
  }
}

async function loadRewardData(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    const [nextSummary, nextRewards, nextExchanges] = await Promise.all([
      getMyRewardAccountSummary(),
      listMyRewards(),
      listMyRewardExchanges()
    ]);
    summary.value = nextSummary;
    rewards.value = nextRewards;
    exchanges.value = nextExchanges;
  } finally {
    loading.value = false;
  }
}

function isExchangeDisabled(reward: StudentReward): boolean {
  return loading.value
    || applyingRewardId.value !== ''
    || !summary.value
    || summary.value.availablePoints < reward.requiredPoints;
}

function confirmExchange(reward: StudentReward): void {
  if (isExchangeDisabled(reward)) return;
  uni.showModal({
    title: '确认兑换奖励',
    content: `申请“${reward.rewardName}”需要 ${reward.requiredPoints} 积分，家长同意后扣除。`,
    confirmText: '确认申请',
    confirmColor: '#167c5a',
    success: (result) => {
      if (result.confirm) {
        void submitExchange(reward);
      }
    }
  });
}

async function submitExchange(reward: StudentReward): Promise<void> {
  if (isExchangeDisabled(reward) || !getStudentSession()) return;
  applyingRewardId.value = reward.id;
  errorMessage.value = '';
  try {
    await applyRewardExchange(reward.id);
    uni.showToast({ title: '申请已提交', icon: 'success' });
    activeTab.value = 'exchanges';
    await loadRewardData();
  } catch (error) {
    errorMessage.value = toMessage(error);
  } finally {
    applyingRewardId.value = '';
  }
}

function reload(): void {
  void initialize();
}

function statusLabel(status: RewardExchangeStatus): string {
  const labels: Record<RewardExchangeStatus, string> = {
    PENDING_APPROVAL: '待家长审批',
    PENDING_VERIFICATION: '待兑现',
    REJECTED: '已驳回',
    AUTO_REJECTED: '超时自动驳回',
    EXPIRED: '已过期',
    VERIFIED: '已核销'
  };
  return labels[status];
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '奖励数据加载失败';
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding-bottom: 48rpx; background: #f4f7f5; }

/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */

.balance-band { padding: 34rpx 40rpx 32rpx; background: #ffffff; border-bottom: 2rpx solid #dce4e1; }
.balance-label, .balance-time { display: block; }
.balance-label { color: #708078; font-size: 24rpx; }
.balance-value-row { display: flex; align-items: baseline; gap: 10rpx; margin-top: 6rpx; }
.balance-value { color: #167c5a; font-size: 58rpx; font-weight: 700; line-height: 1.2; }
.balance-unit { color: #536760; font-size: 24rpx; }
.balance-time { min-height: 32rpx; margin-top: 8rpx; color: #819088; font-size: 21rpx; }

.tab-bar { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); background: #ffffff; border-bottom: 2rpx solid #dce4e1; }
.tab-button { height: 88rpx; margin: 0; border-radius: 0; background: transparent; color: #61736c; font-size: 27rpx; }
.tab-button::after { border: 0; border-radius: 0; }
.tab-button.active { color: #167c5a; font-weight: 650; box-shadow: inset 0 -5rpx #167c5a; }

.content-list { display: grid; gap: 20rpx; padding: 26rpx 28rpx; }
.reward-item, .exchange-item { min-width: 0; padding: 28rpx; background: #ffffff; border: 2rpx solid #dce6e0; border-radius: 12rpx; }
.item-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; }
.item-title { min-width: 0; color: #1c2b28; font-size: 31rpx; font-weight: 650; line-height: 1.4; overflow-wrap: anywhere; }
.points-badge, .status-badge { flex: 0 0 auto; padding: 7rpx 12rpx; border-radius: 8rpx; font-size: 22rpx; line-height: 1.3; }
.points-badge { background: #e8f5ef; color: #116a4b; }
.item-description { display: block; margin-top: 18rpx; color: #536760; font-size: 25rpx; line-height: 1.6; overflow-wrap: anywhere; }
.item-meta, .exchange-points { display: block; margin-top: 16rpx; color: #7a8982; font-size: 22rpx; }
.exchange-points { color: #536760; }
.exchange-button { height: 70rpx; display: flex; align-items: center; justify-content: center; margin: 24rpx 0 0; border-radius: 10rpx; background: #167c5a; color: #ffffff; font-size: 26rpx; }
.exchange-button::after { border: 0; }
.exchange-button[disabled] { background: #e8ecea; color: #86928d; opacity: 1; }

.status-pending_approval { background: #eaf2ff; color: #315f9f; }
.status-pending_verification { background: #fff3dc; color: #8a5b18; }
.status-rejected { background: #fff0ec; color: #a44835; }
.status-auto_rejected, .status-expired { background: #eef1ef; color: #61716a; }
.status-verified { background: #e8f5ef; color: #116a4b; }
.exchange-time-list { display: grid; gap: 8rpx; margin-top: 18rpx; color: #7a8982; font-size: 22rpx; }
.reject-reason { display: block; margin-top: 16rpx; padding: 16rpx; background: #fff5f1; color: #914936; font-size: 23rpx; line-height: 1.5; overflow-wrap: anywhere; }

.state-view { min-height: 320rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 26rpx; }
.state-view.compact { min-height: 360rpx; flex-direction: column; gap: 14rpx; }
.state-title { color: #536760; font-size: 30rpx; }
.state-description { color: #85928c; font-size: 23rpx; }
.loading-footer { min-height: 120rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 24rpx; }
.error-band { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 20rpx 32rpx; background: #fff1ed; color: #9a4b38; font-size: 24rpx; }
.retry-button { width: 120rpx; height: 56rpx; margin: 0; border-radius: 8rpx; background: #ffffff; color: #9a4b38; font-size: 26rpx; }
.retry-button::after { border-color: #e8b4a8; }
</style>
