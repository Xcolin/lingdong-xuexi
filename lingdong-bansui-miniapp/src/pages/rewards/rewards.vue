<template>
  <view class="prototype-workspace ld-page has-tabbar">
    <PrototypePageHeader :title="selectedReward?'兑换确认':selectedExchange?'兑换记录详情':'成长奖励'" icon="gift"/>
    <view v-if="selectedReward||selectedExchange" class="exchange-screen">
      <button class="screen-back" :disabled="!!applyingRewardId" @tap="closeExchange">‹ 返回奖励</button>
      <view v-if="selectedReward" class="exchange-summary"><PrototypeIcon name="gift" tone="amber" :size="56"/><view><text class="item-title">{{selectedReward.rewardName}}</text><text class="item-description">{{selectedReward.description}}</text><text class="exchange-price">{{selectedReward.requiredPoints}} 分</text></view></view>
      <view v-if="selectedReward" class="exchange-facts"><view><text>可用积分</text><text>{{summary?.availablePoints??'—'}}</text></view><view><text>有效期</text><text>{{selectedReward.expiresAt?formatDateTime(selectedReward.expiresAt):'长期有效'}}</text></view></view>
      <view v-if="selectedReward" class="exchange-note">申请由家长审核，同意后扣除 {{selectedReward.requiredPoints}} 积分。实际兑现后由家长核销。</view>
      <button v-if="selectedReward" class="screen-submit" :disabled="isExchangeDisabled(selectedReward)" @tap="submitExchange(selectedReward)">{{applyingRewardId?'正在提交':'确认申请兑换'}}</button>
      <template v-if="selectedExchange"><view class="exchange-summary"><PrototypeIcon name="gift" tone="amber" :size="56"/><view><text class="item-title">{{selectedExchange.rewardName}}</text><text class="exchange-price">{{selectedExchange.requiredPoints}} 分</text><text class="item-description">{{statusLabel(selectedExchange.status)}}</text></view></view><view class="exchange-timeline"><view><text>兑换申请</text><text>{{formatDateTime(selectedExchange.requestedAt)}}</text></view><view v-if="selectedExchange.reviewedAt"><text>{{selectedExchange.rejectReason?'审核驳回':'审核处理'}}</text><text>{{formatDateTime(selectedExchange.reviewedAt)}}</text></view><view v-if="selectedExchange.verifiedAt"><text>已核销</text><text>{{formatDateTime(selectedExchange.verifiedAt)}}</text></view><view v-if="selectedExchange.status==='PENDING_APPROVAL'"><text>审批截止</text><text>{{formatDateTime(selectedExchange.approvalDeadline)}}</text></view></view><view v-if="selectedExchange.rejectReason" class="exchange-note">{{selectedExchange.rejectReason}}</view><view v-if="selectedExchange.description" class="exchange-facts">{{selectedExchange.description}}</view></template>
      <text v-if="errorMessage" class="error-band">{{errorMessage}}</text>
    </view>
    <template v-else>
    <view v-if="capabilityLoading" class="state-view">
      <text>正在检查功能状态</text>
    </view>
    <template v-else-if="rewardExchangeEnabled">
      <view class="ld-heading">
        <text class="ld-heading-title">奖励兑换</text>
        <view class="ld-heading-rule" />
      </view>
      <view class="balance-band"><PrototypeIcon name="gift" :size="36" />
        <text class="balance-label">我的成长积分</text>
        <view class="balance-value-row">
          <text class="balance-value">{{ summary?.availablePoints ?? '—' }}</text>
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
        >全部奖励</button>
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
          <view class="visual-tile"><PrototypeIcon name="gift" :size="30" tone="amber" /></view><view class="item-heading">
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
            {{ summary && summary.availablePoints < reward.requiredPoints ? '积分不足' : '兑换' }}
          </button>
        </view>
        <view v-if="!loading && rewards.length === 0 && !errorMessage" class="state-view compact">
          <text class="state-title">暂无可兑换奖励</text>
          <text class="state-description">家长上架奖励后会显示在这里</text>
        </view>
      </view>

      <view v-if="activeTab === 'exchanges'" class="content-list">
        <view v-for="exchange in exchanges" :key="exchange.id" class="exchange-item" @tap="selectedExchange=exchange">
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
  </template>
  </view>
</template>

<script setup lang="ts">
import {createExchangeFlight} from './exchange-flight';
import PrototypePageHeader from "@/components/PrototypePageHeader.vue";
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref } from 'vue';
import { onPullDownRefresh, onShow, onHide, onUnload } from '@dcloudio/uni-app';
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

const selectedReward=ref<StudentReward|null>(null),selectedExchange=ref<StudentRewardExchange|null>(null);
let sessionRevision=0;
const exchangeFlight=createExchangeFlight(()=>getStudentSession()?.accessToken);
function closeExchange(){if(applyingRewardId.value)return;selectedReward.value=null;selectedExchange.value=null;}
function invalidate(){sessionRevision++;selectedReward.value=null;selectedExchange.value=null;summary.value=null;rewards.value=[];exchanges.value=[];applyingRewardId.value='';loading.value=false;}
onHide(invalidate);onUnload(invalidate);
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
  const token=getStudentSession()?.accessToken,current=++sessionRevision;
  selectedReward.value=null;selectedExchange.value=null;summary.value=null;rewards.value=[];exchanges.value=[];
  capabilityLoading.value = true;
  errorMessage.value = '';
  if (!getStudentSession()) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const capabilities = await getMiniappCapabilities();
    if(current!==sessionRevision||getStudentSession()?.accessToken!==token)return;
    rewardExchangeEnabled.value = capabilities.rewardExchangeEnabled;
    if (!rewardExchangeEnabled.value) {
      await uni.reLaunch({ url: '/pages/student-dashboard/student-dashboard' });
      return;
    }
    await loadRewardData();
  } catch (error) {
    if(current===sessionRevision&&getStudentSession()?.accessToken===token)errorMessage.value = toMessage(error);
  } finally {
    if(current===sessionRevision)capabilityLoading.value = false;
  }
}

async function loadRewardData(): Promise<void> {
  if (loading.value) return;
  const token=getStudentSession()?.accessToken,current=sessionRevision;
  loading.value = true;
  try {
    const [nextSummary, nextRewards, nextExchanges] = await Promise.all([
      getMyRewardAccountSummary(),
      listMyRewards(),
      listMyRewardExchanges()
    ]);
    if(current!==sessionRevision||getStudentSession()?.accessToken!==token)return;
    summary.value = nextSummary;
    rewards.value = nextRewards;
    exchanges.value = nextExchanges;
  } finally {
    if(current===sessionRevision)loading.value = false;
  }
}

function isExchangeDisabled(reward: StudentReward): boolean {
  return loading.value
    || applyingRewardId.value !== ''
    || !summary.value
    || summary.value.availablePoints < reward.requiredPoints;
}

function confirmExchange(reward: StudentReward): void { if(isExchangeDisabled(reward))return;errorMessage.value='';selectedReward.value=reward; }

async function submitExchange(reward: StudentReward): Promise<void> {
  if (isExchangeDisabled(reward) || !getStudentSession()) return;
  const token=getStudentSession()?.accessToken,current=sessionRevision;
  applyingRewardId.value = reward.id;
  errorMessage.value = '';
  try {
    const applied=await exchangeFlight(true,()=>applyRewardExchange(reward.id),created=>{if(current===sessionRevision){selectedReward.value=null;selectedExchange.value=created;}});
    if(!applied||current!==sessionRevision||getStudentSession()?.accessToken!==token)return;
    uni.showToast({ title: '申请已提交', icon: 'success' });
    activeTab.value = 'exchanges';
    await loadRewardData();
  } catch (error) {
    if(current===sessionRevision&&getStudentSession()?.accessToken===token)errorMessage.value = toMessage(error);
  } finally {
    if(current===sessionRevision)applyingRewardId.value = '';
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
.prototype-workspace{padding:24rpx 28rpx 80rpx!important;background:#f5f8f3!important;}.balance-band{background:linear-gradient(110deg,#679472,#d8e9c8)!important;color:white!important;min-height:180rpx;padding:30rpx 32rpx 24rpx 166rpx!important;position:relative;}.balance-band>.prototype-icon{position:absolute;left:36rpx;top:46rpx;}.balance-label{color:white!important;font-size:26rpx;}.balance-value{font-size:44rpx!important;color:white!important;}.balance-time{font-size:18rpx;color:#f2f8ef;}.tab-bar{background:transparent;border:0;gap:18rpx;margin:24rpx 0;}.tab-button{font-size:23rpx;border-radius:30rpx;background:#edf2ec;padding:10rpx 16rpx;}.tab-button.active{background:white;color:#145940;}.reward-item{min-height:190rpx;padding:24rpx 22rpx 24rpx 188rpx!important;border:0!important;background:white!important;border-radius:24rpx!important;}.visual-tile{left:22rpx;top:30rpx;width:140rpx;height:140rpx;border-radius:22rpx;}.item-heading{display:block;}.item-title{display:block;font-size:28rpx;}.points-badge{display:block;margin-top:12rpx;background:transparent!important;color:#f89c0f!important;font-size:28rpx;padding:0;}.item-description{font-size:23rpx;color:#80877f;margin-top:8rpx;}.item-meta{font-size:18rpx;color:#818b84;}.exchange-button{width:140rpx;margin:12rpx 0 0 auto;font-size:24rpx;padding:0;line-height:60rpx;}.exchange-item{padding:26rpx!important;}.ld-heading{display:none;}
.exchange-summary{display:flex;align-items:center;gap:28rpx;background:white;border-radius:24rpx;padding:28rpx;margin:24rpx 0;}.exchange-price{display:block;color:#f69508;font-size:34rpx;font-weight:600;margin-top:14rpx;}.exchange-facts,.exchange-timeline{background:white;border-radius:24rpx;padding:28rpx;margin:24rpx 0;font-size:26rpx;}.exchange-facts>view,.exchange-timeline>view{display:flex;justify-content:space-between;gap:20rpx;padding:24rpx 0;border-bottom:1rpx solid #edf0e8;}.exchange-note{background:#fff4df;color:#936534;border-radius:20rpx;padding:28rpx;line-height:1.7;font-size:25rpx;}.screen-submit{background:#13674e;color:white;border-radius:20rpx;margin-top:28rpx;font-size:30rpx;}.screen-back{background:transparent;color:#176c51;text-align:left;font-size:25rpx;padding:0;}
</style>
