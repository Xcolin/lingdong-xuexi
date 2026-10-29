<template>
  <view class="prototype-workspace ld-page has-tabbar">
    <RewardShopHeader v-if="!selectedReward&&!selectedExchange" :points="summary?.availablePoints" :title="rewardLayout==='list'?'成长奖励':'奖品商城'" :show-balance="rewardLayout!=='list'" @menu="showRewardMenu" @records="activeTab='exchanges'" />
    <PrototypePageHeader v-if="selectedReward||selectedExchange" :title="selectedReward?'兑换确认':'兑换记录详情'" icon="gift"/>
    <view v-if="selectedReward||selectedExchange" class="exchange-screen">
      <button class="screen-back" :disabled="!!applyingRewardId" @tap="closeExchange">‹ 返回奖励</button>
      <view v-if="selectedReward" class="exchange-summary"><view class="summary-thumb"><image v-if="selectedReward.imageUrl && !imageFailed[selectedReward.id]" :src="selectedReward.imageUrl" mode="aspectFill" @error="imageFailed[selectedReward.id] = true" /><PrototypeIcon v-else name="gift" tone="amber" :size="52" /></view><view><text class="item-title">{{selectedReward.rewardName}}</text><text class="item-description">{{selectedReward.description}}</text><text class="exchange-price">{{selectedReward.requiredPoints}} 分</text></view></view>
      <view v-if="selectedReward" class="exchange-facts"><view><text>可用积分</text><text>{{summary?.availablePoints??'—'}}</text></view><view><text>有效期</text><text>{{selectedReward.expiresAt?formatDateTime(selectedReward.expiresAt):'长期有效'}}</text></view></view>
      <view v-if="selectedReward" class="exchange-info">
        <view class="info-row">
          <text class="info-label">兑换对象</text>
          <view class="info-value"><view class="student-avatar"><PrototypeIcon name="user" tone="green" :size="30" /></view><text class="student-name">{{ summary?.studentName || '本人' }}</text><text v-if="summary?.currentClassName" class="student-class">{{ summary.currentClassName }}</text></view>
        </view>
        <view class="info-row"><text class="info-label">收货方式</text><text class="info-static">学校发放（班主任处领取）</text></view>
        <view class="info-row info-row-last"><text class="info-label">预计发放时间</text><text class="info-static">3-5 个工作日</text></view>
      </view>
      <view v-if="selectedReward" class="exchange-tips">
        <view class="tips-title"><text class="tips-bulb">💡</text><text>温馨提示</text></view>
        <text class="tip-item">1. 兑换后将扣除 {{selectedReward.requiredPoints}} 积分，无法撤销。</text>
        <text class="tip-item">2. 申请由家长审核，同意后由家长兑现并核销。</text>
        <text class="tip-item">3. 如有问题请联系班主任。</text>
      </view>
      <button v-if="selectedReward" class="screen-submit" :disabled="isExchangeDisabled(selectedReward)" @tap="submitExchange(selectedReward)">{{applyingRewardId?'正在提交':`确认兑换（扣除 ${selectedReward.requiredPoints} 分）`}}</button>
      <template v-if="selectedExchange"><view class="exchange-summary"><view class="summary-thumb"><image v-if="exchangeImage && !imageFailed[selectedExchange.id]" :src="exchangeImage" mode="aspectFill" @error="imageFailed[selectedExchange.id] = true" /><PrototypeIcon v-else name="gift" tone="amber" :size="52" /></view><view><view class="summary-title-row"><text class="item-title">{{selectedExchange.rewardName}}</text><text class="status-badge" :class="`status-${selectedExchange.status.toLowerCase()}`">{{ statusLabel(selectedExchange.status) }}</text></view><text class="exchange-price">{{selectedExchange.requiredPoints}} 分</text><text class="item-description">兑换于 {{formatDateTime(selectedExchange.requestedAt)}}</text></view></view><view class="exchange-timeline"><view v-for="(node,idx) in timelineNodes" :key="idx" class="timeline-node" :class="node.state"><view class="node-dot">{{ node.state==='done' ? '✓' : node.state==='rejected' ? '✕' : '' }}</view><view class="node-body"><text class="node-title">{{node.title}}</text><text v-if="node.time" class="node-time">{{node.time}}</text><text v-if="node.desc" class="node-desc">{{node.desc}}</text></view></view></view><view class="exchange-info"><view class="info-row"><text class="info-label">兑换对象</text><view class="info-value"><view class="student-avatar"><PrototypeIcon name="user" tone="green" :size="30" /></view><text class="student-name">{{ summary?.studentName || '本人' }}</text><text v-if="summary?.currentClassName" class="student-class">{{ summary.currentClassName }}</text></view></view><view class="info-row"><text class="info-label">收货方式</text><text class="info-static">学校发放（班主任处领取）</text></view><view class="info-row"><text class="info-label">发放人</text><text class="info-static">{{ selectedExchange.verifiedBy || selectedExchange.reviewedBy ? '家长' : '待家长处理' }}</text></view><view class="info-row info-row-last"><text class="info-label">备注</text><text class="info-static">{{ selectedExchange.description || '—' }}</text></view></view></template>
      <text v-if="errorMessage" class="error-band">{{errorMessage}}</text>
    </view>
    <template v-else>
    <view v-if="capabilityLoading" class="state-view">
      <text>正在检查功能状态</text>
    </view>
    <template v-else-if="rewardExchangeEnabled">
      <view v-if="rewardLayout==='list'" class="growth-reward-banner"><image class="reward-botanical" src="/static/growth-botanical.svg" mode="aspectFit"/><PrototypeIcon name="star" tone="amber" :size="60"/><view><text class="growth-balance-caption">我的成长积分</text><text class="growth-balance">{{summary?.availablePoints??'—'}}<text> 积分</text></text></view></view>

      <view v-if="activeTab!=='rewards'" class="tab-bar" role="tablist">
        <button
          class="tab-button"
          :aria-selected="false"
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
        <view class="category-chips" role="tablist">
          <button v-for="c in categories" :key="c.value" class="chip" :class="{ active: activeCategory === c.value }" :aria-selected="activeCategory === c.value" @tap="activeCategory = c.value">{{ c.label }}</button>
        </view>
        <view class="reward-grid" :class="{'reward-list':rewardLayout==='list'}">
          <view v-for="reward in filteredRewards" :key="reward.id" class="reward-card">
            <view class="card-image">
              <image v-if="reward.imageUrl && !imageFailed[reward.id]" :src="reward.imageUrl" mode="aspectFill" @error="imageFailed[reward.id] = true" />
              <view v-else class="image-placeholder"><PrototypeIcon name="gift" :size="52" tone="amber" /></view>
            </view>
            <view class="card-body"><text class="card-title">{{ reward.rewardName }}</text>
            <view class="card-points"><text class="points-num">{{ reward.requiredPoints }}</text><text class="points-unit">分</text></view>
            <view class="card-meta-row"><text class="meta-days">{{ daysLeft(reward.expiresAt) }}</text><text class="meta-stock">库存 {{ reward.stockQuantity ?? 0 }}</text></view></view>
            <button class="card-exchange" :disabled="isExchangeDisabled(reward)" @tap.stop="confirmExchange(reward)">{{ summary && summary.availablePoints < reward.requiredPoints ? '积分不足' : '立即兑换' }}</button>
          </view>
        </view>
        <view v-if="!loading && filteredRewards.length === 0 && !errorMessage" class="state-view compact">
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
    <AppTabBar v-if="!selectedReward&&!selectedExchange" :items="STUDENT_TABS" :active="2" />
  </view>
</template>

<script setup lang="ts">
import {createExchangeFlight} from './exchange-flight';
import PrototypePageHeader from "@/components/PrototypePageHeader.vue";
import RewardShopHeader from '@/components/RewardShopHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref, computed } from 'vue';
import { onLoad, onPullDownRefresh, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  applyRewardExchange,
  getMyRewardAccountSummary,
  listMyRewardExchanges,
  listMyRewards,
  type RewardAccountSummary,
  type RewardCategory,
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
function showRewardMenu(){uni.showActionSheet({itemList:['我的兑换','积分明细'],success:result=>{activeTab.value=result.tapIndex===0?'exchanges':'points';}});}
const rewardLayout = ref<'shop'|'list'>('shop');
onLoad(options => {
  activeTab.value = options?.tab === 'exchanges' ? 'exchanges' : options?.tab === 'points' ? 'points' : 'rewards';
  rewardLayout.value = options?.layout === 'list' ? 'list' : 'shop';
});
const summary = ref<RewardAccountSummary | null>(null);
const rewards = ref<StudentReward[]>([]);
const exchanges = ref<StudentRewardExchange[]>([]);
const categories: Array<{ value: 'ALL' | RewardCategory; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'STUDY_SUPPLIES', label: '学习用品' },
  { value: 'LIFE_GOODS', label: '生活好物' },
  { value: 'EXPERIENCE', label: '体验活动' }
];
const activeCategory = ref<'ALL' | RewardCategory>('ALL');
const imageFailed = ref<Record<string, boolean>>({});
const filteredRewards = computed(() => activeCategory.value === 'ALL'
  ? rewards.value
  : rewards.value.filter(reward => reward.category === activeCategory.value));

function daysLeft(expiresAt: string | null): string {
  if (!expiresAt) return '长期有效';
  const days = Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 86400000);
  return days > 0 ? `剩余 ${days} 天` : '已过期';
}

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

const exchangeImage = computed(() => {
  const ex = selectedExchange.value;
  if (!ex) return '';
  const reward = rewards.value.find(item => item.id === ex.rewardId);
  return reward?.imageUrl || '';
});

interface TimelineNode { title: string; time: string; desc: string; state: 'done' | 'pending' | 'rejected'; }
const timelineNodes = computed<TimelineNode[]>(() => {
  const ex = selectedExchange.value;
  if (!ex) return [];
  const nodes: TimelineNode[] = [
    { title: '兑换申请', time: formatDateTime(ex.requestedAt), desc: '已提交兑换申请', state: 'done' }
  ];
  if (ex.rejectReason) {
    nodes.push({ title: '审核驳回', time: ex.reviewedAt ? formatDateTime(ex.reviewedAt) : '', desc: ex.rejectReason, state: 'rejected' });
  } else if (ex.reviewedAt) {
    nodes.push({ title: '家长审批', time: formatDateTime(ex.reviewedAt), desc: '家长审核通过', state: 'done' });
  } else {
    nodes.push({ title: '家长审批', time: '', desc: `待家长审批${ex.approvalDeadline ? `，截止 ${formatDateTime(ex.approvalDeadline)}` : ''}`, state: 'pending' });
  }
  if (ex.verifiedAt) {
    nodes.push({ title: '奖励发放', time: formatDateTime(ex.verifiedAt), desc: '家长已发放，请注意查收', state: 'done' });
  } else {
    nodes.push({ title: '奖励发放', time: '', desc: ex.status === 'PENDING_VERIFICATION' ? '家长确认兑现后发放' : '审批通过后发放', state: 'pending' });
  }
  return nodes;
});

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
.points-compact {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 8rpx;
  padding: 20rpx 28rpx;
  border-radius: 20rpx;
  background: #ffffff;
  box-shadow: 0 4rpx 16rpx rgba(24, 79, 64, 0.05);
}
.points-compact-label { color: $ld-text-secondary; font-size: $ld-font-caption; font-weight: 600; }
.points-compact-right { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; }
.points-compact-value-row { display: flex; align-items: baseline; gap: 6rpx; }
.points-compact-value { font-size: 40rpx; font-weight: 700; color: #13674e; line-height: 1; }
.points-compact-unit { font-size: $ld-font-caption; color: $ld-text-secondary; }
.points-compact-time { font-size: $ld-font-mini; color: $ld-text-muted; }
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
.status-badge.status-verified { background: #e8f6ef; color: $ld-primary; }
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
.prototype-workspace{padding:24rpx 28rpx 80rpx!important;background:#f5f8f3!important;}.balance-band{background:linear-gradient(110deg,#679472,#d8e9c8)!important;color:white!important;min-height:180rpx;padding:30rpx 32rpx 24rpx 166rpx!important;position:relative;}.balance-band>.prototype-icon{position:absolute;left:36rpx;top:46rpx;}.balance-label{color:white!important;font-size:26rpx;}.balance-value{font-size:44rpx!important;color:white!important;}.balance-time{font-size:18rpx;color:#f2f8ef;}.tab-bar{background:transparent;border:0;gap:14rpx;margin:14rpx 0;}.tab-button{font-size:23rpx;border-radius:30rpx;background:#edf2ec;padding:10rpx 16rpx;}.tab-button.active{background:white;color:#145940;}.reward-item{min-height:190rpx;padding:24rpx 22rpx 24rpx 188rpx!important;border:0!important;background:white!important;border-radius:24rpx!important;}.visual-tile{left:22rpx;top:30rpx;width:140rpx;height:140rpx;border-radius:22rpx;}.item-heading{display:block;}.item-title{display:block;font-size:28rpx;}.points-badge{display:block;margin-top:12rpx;background:transparent!important;color:#f89c0f!important;font-size:28rpx;padding:0;}.item-description{font-size:23rpx;color:#80877f;margin-top:8rpx;}.item-meta{font-size:18rpx;color:#818b84;}.exchange-button{width:140rpx;margin:12rpx 0 0 auto;font-size:24rpx;padding:0;line-height:60rpx;}.exchange-item{padding:26rpx!important;}.ld-heading{display:none;}
.exchange-summary{display:flex;align-items:center;gap:28rpx;background:white;border-radius:24rpx;padding:28rpx;margin:24rpx 0;}.exchange-price{display:block;color:#f69508;font-size:34rpx;font-weight:600;margin-top:14rpx;}
.summary-thumb{width:120rpx;height:120rpx;border-radius:20rpx;background:#fff2d6;overflow:hidden;display:flex;align-items:center;justify-content:center;flex-shrink:0;}.summary-thumb image{width:100%;height:100%;}
.exchange-info{background:white;border-radius:24rpx;padding:8rpx 28rpx;margin:24rpx 0;font-size:26rpx;}.info-row{display:flex;align-items:center;justify-content:space-between;gap:20rpx;padding:26rpx 0;border-bottom:1rpx solid #edf0e8;}.info-row-last{border-bottom:none;}.info-label{color:#809486;flex-shrink:0;}.info-value{display:flex;align-items:center;gap:14rpx;min-width:0;}.student-avatar{width:56rpx;height:56rpx;border-radius:50%;background:#e4f1ea;display:flex;align-items:center;justify-content:center;overflow:hidden;flex-shrink:0;}.student-name{font-weight:500;color:#333c37;}.student-class{color:#809486;font-size:24rpx;}.info-static{color:#333c37;text-align:right;}
.exchange-tips{background:#fffbe8;border-radius:20rpx;padding:28rpx;margin:24rpx 0;display:flex;flex-direction:column;gap:10rpx;}.tips-title{display:flex;align-items:center;gap:10rpx;color:#936534;font-weight:600;font-size:26rpx;margin-bottom:6rpx;}.tips-bulb{font-size:28rpx;}.tip-item{color:#936534;font-size:24rpx;line-height:1.7;}.exchange-facts{background:white;border-radius:24rpx;padding:28rpx;margin:24rpx 0;font-size:26rpx;}.exchange-facts>view{display:flex;justify-content:space-between;gap:20rpx;padding:24rpx 0;border-bottom:1rpx solid #edf0e8;}.exchange-note{background:#fff4df;color:#936534;border-radius:20rpx;padding:28rpx;line-height:1.7;font-size:25rpx;}.screen-submit{background:#13674e;color:white;border-radius:20rpx;margin-top:28rpx;font-size:30rpx;}.screen-back{background:transparent;color:#176c51;text-align:left;font-size:25rpx;padding:0;}
.exchange-timeline{background:white;border-radius:24rpx;padding:28rpx 28rpx 18rpx;margin:24rpx 0;}
.timeline-node{display:flex;gap:20rpx;position:relative;padding:14rpx 0;}
.timeline-node:not(:last-child)::before{content:'';position:absolute;left:21rpx;top:60rpx;bottom:-4rpx;width:4rpx;background:#e3e9e0;}
.node-dot{width:44rpx;height:44rpx;border-radius:50%;background:#cfd8d0;color:white;display:flex;align-items:center;justify-content:center;font-size:24rpx;flex-shrink:0;position:relative;z-index:1;margin-top:2rpx;box-sizing:border-box;}
.timeline-node.done .node-dot{background:#13674e;}
.timeline-node.rejected .node-dot{background:#c4562f;}
.node-body{display:flex;flex-direction:column;gap:4rpx;min-width:0;flex:1;}
.node-title{font-weight:600;font-size:27rpx;color:#333c37;}
.timeline-node.pending .node-title{color:#8a968d;font-weight:500;}
.node-time{font-size:22rpx;color:#809486;}
.node-desc{font-size:23rpx;color:#6b7c71;overflow-wrap:anywhere;}
.timeline-node.rejected .node-desc{color:#c4562f;}
.summary-title-row{display:flex;align-items:center;gap:14rpx;flex-wrap:wrap;}

.category-chips{display:flex;gap:12rpx;margin-top:16rpx;}
.chip{flex-shrink:0;margin:0;padding:0 26rpx;height:56rpx;line-height:56rpx;border-radius:999rpx;background:#edf2ec;color:#5c6f63;font-size:23rpx;font-weight:500;}
.chip::after{border:0;}
.chip.active{background:#13674e;color:#ffffff;font-weight:600;}
.reward-grid{display:flex;flex-wrap:wrap;gap:16rpx;margin-top:16rpx;}
.reward-card{width:calc((100% - 16rpx) / 2);box-sizing:border-box;background:#ffffff;border-radius:20rpx;padding:12rpx 12rpx 16rpx;box-sizing:border-box;box-shadow:0 4rpx 14rpx rgba(24,79,64,0.05);}
.card-image{width:100%;height:176rpx;border-radius:14rpx;background:#f2f6ef;overflow:hidden;}
.card-image image{width:100%;height:100%;}
.image-placeholder{width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:linear-gradient(150deg,#f0f6ec,#e4f0e2);}
.card-title{display:block;margin-top:10rpx;font-size:24rpx;font-weight:600;color:#1c3a2c;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.card-points{display:flex;align-items:baseline;gap:4rpx;margin-top:6rpx;color:#f89c0f;}
.points-num{font-size:28rpx;font-weight:700;}
.points-unit{font-size:20rpx;font-weight:500;}
.card-meta-row{display:flex;justify-content:space-between;gap:8rpx;margin-top:4rpx;font-size:19rpx;color:#8a968d;}
.meta-days,.meta-stock{white-space:nowrap;}
.card-exchange{width:100%;height:56rpx;display:flex;align-items:center;justify-content:center;margin:10rpx 0 0;padding:0;border-radius:999rpx;background:#13674e;color:#ffffff;font-size:24rpx;font-weight:600;}
.card-exchange::after{border:0;}
.card-exchange[disabled]{background:#e2e9e6;color:#9aa79e;}
.card-body{min-width:0;}
.growth-reward-banner{display:flex;align-items:center;gap:24rpx;padding:28rpx;background:linear-gradient(110deg,#8fbc99,#d6e7d3);border-radius:24rpx;color:#fff;}
.growth-reward-banner{position:relative;overflow:hidden;}.growth-reward-banner>.prototype-icon,.growth-reward-banner>view{position:relative;z-index:1;}.reward-botanical{position:absolute;right:-14rpx;bottom:-16rpx;width:210rpx;height:190rpx;opacity:.72;pointer-events:none;}
.growth-balance-caption{display:block;font-size:24rpx;}.growth-balance{display:block;font-size:46rpx;font-weight:650;}.growth-balance text{font-size:24rpx;font-weight:400;}
.growth-reward-banner button{margin:0 0 0 auto;padding:8rpx;background:transparent;color:#fff;font-size:24rpx;}.growth-reward-banner button::after{border:0;}
.reward-list{display:flex;flex-direction:column;}.reward-list .reward-card{width:100%;display:grid;grid-template-columns:100rpx minmax(0,1fr) 118rpx;gap:18rpx;align-items:center;padding:22rpx;}
.reward-list .card-image{width:100rpx;height:110rpx;}.reward-list .card-title{margin-top:0;white-space:normal;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;line-height:1.5;}
.reward-list .card-meta-row{flex-wrap:wrap;}.reward-list .card-exchange{background:#f5a20a;width:118rpx;align-self:end;}
</style>
