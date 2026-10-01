<template>
  <view class="ld-page has-tabbar">
    <view v-if="capabilityLoading" class="state-view">
      <text>正在检查功能状态</text>
    </view>
    <template v-else-if="rewardExchangeEnabled">
      <view class="ld-heading">
        <text class="ld-heading-title">奖励兑换</text>
        <view class="ld-heading-rule" />
      </view>
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
        <button
          class="tab-button"
          :class="{ active: activeTab === 'points' }"
          :aria-selected="activeTab === 'points'"
          @tap="activeTab = 'points'"
        >积分明细</button>
      </view>

      <view v-if="errorMessage" class="error-band">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view v-if="activeTab === 'points'" class="content-list">
        <PointLedger />
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
  <AppTabBar :items="STUDENT_TABS" :active="1" />
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
import AppTabBar from '@/components/AppTabBar.vue';
import PointLedger from '@/components/PointLedger.vue';
import { STUDENT_TABS } from '@/config/tabbar';

const capabilityLoading = ref(true);
const rewardExchangeEnabled = ref(false);
const loading = ref(false);
const applyingRewardId = ref('');
const errorMessage = ref('');
const activeTab = ref<'rewards' | 'exchanges' | 'points'>('rewards');
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
.balance-band {
  margin-top: 24rpx;
  padding: 36rpx 32rpx;
  border-radius: 28rpx;
  background: $ld-gradient-primary;
  box-shadow: 0 10rpx 30rpx rgba(22, 124, 90, 0.25);
  color: #ffffff;
}
.balance-label { display: block; font-size: $ld-font-caption; opacity: 0.88; }
.balance-value-row { display: flex; align-items: baseline; gap: 10rpx; margin-top: 12rpx; }
.balance-value { font-size: 64rpx; font-weight: 700; line-height: 1; }
.balance-unit { font-size: $ld-font-body; opacity: 0.88; }
.balance-time { display: block; margin-top: 14rpx; font-size: $ld-font-mini; opacity: 0.8; }

.tab-bar { display: flex; gap: 16rpx; margin-top: 28rpx; }
.tab-button {
  flex: 1;
  height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  border-radius: $ld-radius-pill;
  background: #ffffff;
  color: $ld-text-secondary;
  font-size: $ld-font-caption;
  font-weight: 600;
  box-shadow: $ld-shadow-card;
}
.tab-button::after { border: 0; }
.tab-button.active { background: $ld-primary; color: #ffffff; box-shadow: $ld-shadow-btn; }

.error-band { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin-top: 24rpx; padding: 26rpx 30rpx; border-radius: $ld-radius-lg; background: $ld-danger-soft; }
.error-band text { color: $ld-danger; font-size: $ld-font-caption; flex: 1; }
.retry-button {
  width: 120rpx; height: 56rpx; display: flex; align-items: center; justify-content: center;
  margin: 0; border-radius: $ld-radius-pill; background: $ld-card; color: $ld-danger;
  font-size: $ld-font-caption; flex-shrink: 0;
}
.retry-button::after { border: 0; }

.content-list { margin-top: 8rpx; }
.reward-item, .exchange-item {
  margin-top: $ld-gap-block;
  padding: 28rpx 30rpx;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
}
.item-heading { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.item-title { color: $ld-text; font-size: 30rpx; font-weight: 650; overflow-wrap: anywhere; }
.points-badge, .status-badge {
  padding: 6rpx 18rpx; border-radius: $ld-radius-pill; font-size: $ld-font-mini; font-weight: 600; flex-shrink: 0;
  background: $ld-primary-soft; color: $ld-primary;
}
.status-badge.status-pending_approval { background: $ld-warning-soft; color: $ld-warning; }
.status-badge.status-approved { background: #e8f6ef; color: $ld-primary; }
.status-badge.status-rejected, .status-badge.status-expired { background: $ld-danger-soft; color: $ld-danger; }
.status-badge.status-verified { background: #eef1f5; color: #526170; }
.item-description { display: block; margin-top: 14rpx; color: $ld-text-secondary; font-size: $ld-font-caption; line-height: 1.6; }
.item-meta { display: block; margin-top: 14rpx; color: $ld-text-muted; font-size: $ld-font-mini; }
.exchange-points { display: block; margin-top: 14rpx; color: $ld-text-secondary; font-size: $ld-font-caption; }
.exchange-time-list { margin-top: 12rpx; }
.exchange-time-list text { display: block; margin-top: 6rpx; color: $ld-text-muted; font-size: $ld-font-mini; }
.reject-reason { display: block; margin-top: 12rpx; color: $ld-danger; font-size: $ld-font-caption; }
.exchange-button {
  width: 100%; height: 76rpx; display: flex; align-items: center; justify-content: center;
  margin: 24rpx 0 0; border-radius: $ld-radius-md; background: $ld-gradient-primary;
  color: #ffffff; font-size: $ld-font-body; font-weight: 600; box-shadow: $ld-shadow-btn;
}
.exchange-button::after { border: 0; }
.exchange-button[disabled] { background: #e2e9e6; color: $ld-text-muted; box-shadow: none; }

.state-view { padding: 64rpx 32rpx; text-align: center; }
.state-title { display: block; color: $ld-text-secondary; font-size: 30rpx; font-weight: 600; }
.state-description { display: block; margin-top: 12rpx; color: $ld-text-muted; font-size: $ld-font-caption; }
</style>
