<template>
  <view class="prototype-workspace page-shell has-tabbar">
    <PrototypePageHeader :title="period==='WEEK'?'成长周报详情':editing?'每日成长复盘':'成长复盘'" icon="growth"/>
    <view v-if="capabilityLoading" class="state-view">正在检查功能状态</view>
    <template v-else-if="enabled">
      <view class="period-tabs"><button v-if="dailyAllowed" :disabled="loading||submitting" :class="{active:period==='DAY'}" @tap="choosePeriod('DAY')">每日复盘</button><button v-if="weeklyAllowed" :disabled="loading||submitting" :class="{active:period==='WEEK'}" @tap="choosePeriod('WEEK')">成长周报</button></view>
      <scroll-view v-if="reviews.length" class="date-strip" scroll-x>
        <view class="date-options">
          <button
            v-for="review in reviews"
            :key="review.reviewId"
            class="date-option"
            :disabled="submitting"
            :class="{ active: review.reviewId === selectedReviewId }"
            @tap="selectReview(review.reviewId)"
          >{{ formatDate(review.periodStart) }}</button>
        </view>
      </scroll-view>

      <view v-if="errorMessage" class="error-band">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @tap="reload">重试</button>
      </view>

      <view v-if="detail&&period==='WEEK'" class="weekly-detail"><text class="week-range">{{detail.periodStart}} — {{detail.periodEnd}}</text><view class="weekly-student"><PrototypeIcon name="user" :size="42"/><text>{{detail.studentName}}</text></view><view class="weekly-metrics"><view><text>{{detail.earnedPoints}}</text><text>本周获得积分</text></view><view><text>{{detail.completedCount}}</text><text>完成任务数</text></view><view><text>{{percent(detail.completionRate)}}</text><text>任务完成率</text></view></view><view class="weekly-section"><text class="section-title">本周任务分类</text><view v-for="category in detail.categories" :key="category.categoryCode" class="category-row"><text>{{category.categoryCode}}</text><text>{{category.completedCount}} / {{category.taskCount}}</text></view><text v-if="!detail.categories.length" class="empty-line">暂无分类统计</text></view><view class="weekly-section"><text class="section-title">每日成长记录</text><view v-for="day in detail.dailyTrends" :key="day.trendDate" class="weekly-trend"><text>{{day.trendDate}}</text><text>完成 {{day.completedCount}} 项 · {{day.earnedPoints}} 积分</text></view></view><view v-if="detail.supplements.length" class="weekly-section"><text class="section-title">补充记录</text><view v-for="item in detail.supplements" :key="item.id"><text>{{item.content}}</text></view></view></view>
      <view v-else-if="detail" class="review-content">
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
            <view v-for="(label,index) in typeLabels" :key="label" class="daily-field"><text class="section-title">★ {{label}}</text><textarea v-model="reflectionFields[index]" class="content-input" maxlength="200" :disabled="submitting" :placeholder="['记录今天的收获','记录遇到的困难','记录明天的计划'][index]"/><text class="field-count">{{reflectionFields[index].length}}/200</text></view>
            <button class="submit-button" :disabled="submitting||reflectionFields.some(value=>!value.trim())" @tap="submitSupplement">{{submitting?'正在提交':'提交复盘'}}</button>
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

      <view v-else-if="!loading && !errorMessage" class="state-view">{{period==='WEEK'?'暂无成长周报':'暂无成长复盘'}}</view>
      <view v-if="loading" class="loading-mask">正在加载</view>
    </template>
  <AppTabBar :items="STUDENT_TABS" :active="2" />
  </view>
</template>

<script setup lang="ts">
import {canReadReviewPeriod,matchesReviewPeriod,applyCurrentReviewRefresh} from './review-period';
import PrototypePageHeader from "@/components/PrototypePageHeader.vue";
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { STUDENT_TABS } from '@/config/tabbar';
import { ref } from 'vue';
import { onPullDownRefresh, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  addMyGrowthReviewSupplement,
  saveMyDailyReflection,
  getMyGrowthReview,
  listMyDailyGrowthReviews,
  listMyGrowthReviews,
  getMyReviewIdentity,
  type GrowthReviewDetail,
  type GrowthReviewSummary,
  type GrowthReviewSupplementType
} from '@/api/growth-review';
import { getStudentSession } from '@/session/student-session';

const supplementTypes: GrowthReviewSupplementType[] = ['INSIGHT', 'STRENGTH_WEAKNESS', 'NEXT_PLAN'];
const typeLabels = ['今日收获', '遇到的困难', '明天的计划'];
const capabilityLoading = ref(true);
const enabled = ref(false),dailyAllowed=ref(false),weeklyAllowed=ref(false),period=ref<'DAY'|'WEEK'>('DAY');
let revision=0;
function clearReview(){revision++;detail.value=null;reviews.value=[];editing.value=false;reflectionFields.value=['','',''];loading.value=false;submitting.value=false;}
onHide(clearReview);onUnload(clearReview);
function valid(n:number,token:string|undefined){return n===revision&&getStudentSession()?.accessToken===token;}
async function choosePeriod(value:'DAY'|'WEEK'){if(loading.value||submitting.value||value==='WEEK'&&!weeklyAllowed.value||value==='DAY'&&!dailyAllowed.value)return;clearReview();period.value=value;selectedReviewId.value='';const n=revision,token=getStudentSession()?.accessToken;try{await loadReviews();}catch(error){if(valid(n,token))errorMessage.value=toMessage(error);}}
const loading = ref(false);
const errorMessage = ref('');
const reviews = ref<GrowthReviewSummary[]>([]);
const selectedReviewId = ref('');
const detail = ref<GrowthReviewDetail | null>(null);
const editing = ref(false);
const submitting = ref(false);
const typeIndex = ref(0);
const supplementContent = ref('');
const reflectionFields=ref(['','','']);

onShow(() => void initialize());
onPullDownRefresh(async () => {
  await initialize();
  uni.stopPullDownRefresh();
});

async function initialize(): Promise<void> {
  clearReview();const n=revision,token=getStudentSession()?.accessToken;dailyAllowed.value=weeklyAllowed.value=enabled.value=false;
  capabilityLoading.value = true;
  errorMessage.value = '';
  if (!getStudentSession()) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  try {
    const [capabilities,identity]=await Promise.all([getMiniappCapabilities(),getMyReviewIdentity()]);
    if(!valid(n,token))return;
    dailyAllowed.value=canReadReviewPeriod(identity,capabilities.dailyGrowthReviewEnabled);
    weeklyAllowed.value=canReadReviewPeriod(identity,capabilities.periodicGrowthReportEnabled);
    enabled.value=dailyAllowed.value||weeklyAllowed.value;
    if(period.value==='DAY'&&!dailyAllowed.value&&weeklyAllowed.value)period.value='WEEK';
    if (!enabled.value) {
      await uni.reLaunch({ url: '/pages/student-dashboard/student-dashboard' });
      return;
    }
    await loadReviews();
  } catch (error) {
    if(valid(n,token))errorMessage.value = toMessage(error);
  } finally {
    if(valid(n,token))capabilityLoading.value = false;
  }
}

async function loadReviews(): Promise<void> {
  const n=revision,token=getStudentSession()?.accessToken,expected=period.value;
  if(expected==='WEEK'?!weeklyAllowed.value:!dailyAllowed.value)return;
  loading.value = true;
  try {
    const page = await listMyGrowthReviews(expected);
    if(!valid(n,token)||period.value!==expected)return;
    reviews.value = page.items.filter(item=>matchesReviewPeriod(item.periodType,expected));
    const targetId = reviews.value.some((item) => item.reviewId === selectedReviewId.value)
      ? selectedReviewId.value : reviews.value[0]?.reviewId;
    if (targetId) await selectReview(targetId);
    else detail.value = null;
  } finally {
    if(valid(n,token))loading.value = false;
  }
}

async function selectReview(reviewId: string): Promise<void> {
  if(submitting.value)return;
  if(!reviews.value.some(item=>item.reviewId===reviewId&&item.periodType===period.value))return;
  const n=revision,token=getStudentSession()?.accessToken,expected=period.value;
  selectedReviewId.value = reviewId;
  loading.value = true;
  errorMessage.value = '';
  try {
    const next=await getMyGrowthReview(reviewId);
    if(!valid(n,token)||period.value!==expected||selectedReviewId.value!==reviewId)return;
    if(!matchesReviewPeriod(next.periodType,expected)){detail.value=null;throw new Error('复盘周期与所选入口不一致');}
    detail.value=next;
    editing.value = false;reflectionFields.value=['','',''];
  } catch (error) {
    if(valid(n,token))errorMessage.value = toMessage(error);
  } finally {
    if(valid(n,token))loading.value = false;
  }
}

function changeType(event: { detail: { value: string | number } }): void {
  typeIndex.value = Number(event.detail.value);
}

async function submitSupplement(): Promise<void> {
  const values=reflectionFields.value.map(value=>value.trim());
  if(period.value!=='DAY'||detail.value?.periodType!=='DAY'||!detail.value||values.some(value=>!value)||submitting.value)return;
  const reviewId=detail.value.reviewId,token=getStudentSession()?.accessToken,n=revision,expected=period.value;
  const current=()=>valid(n,token)&&period.value===expected&&selectedReviewId.value===reviewId&&detail.value?.reviewId===reviewId;
  submitting.value = true;
  try {
    await saveMyDailyReflection(reviewId,values[0],values[1],values[2]);
    if(!current())return;
    reflectionFields.value=['','',''];
    supplementContent.value = '';
    editing.value = false;
    await applyCurrentReviewRefresh(()=>getMyGrowthReview(reviewId),current,next=>{
      if(next.reviewId!==reviewId||next.periodType!==expected)return;
      detail.value=next;
      uni.showToast({ title:'补录已追加',icon:'success' });
    });
  } catch (error) {
    if(current())uni.showToast({ title: toMessage(error), icon: 'none' });
  } finally {
    if(valid(n,token))submitting.value = false;
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

.page-shell { min-height: calc(100vh - var(--window-top,0px)); padding-bottom: 48rpx; background: #f4f7f5; }
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

.prototype-workspace{background:#F6F8F5!important;padding:28rpx 28rpx calc(160rpx + env(safe-area-inset-bottom))!important;box-sizing:border-box;}.ld-heading{display:none;}.summary-band,.section-band,.balance-band,.reward-item,.exchange-item{border:1rpx solid #e1e9e2!important;border-radius:24rpx!important;background:#fffefa!important;box-sizing:border-box;}.metric-grid{min-width:0;}.category-row{gap:14rpx;}.category-row>text:first-child{max-width:180rpx;overflow-wrap:anywhere;}.category-track{min-width:0;}.date-option{flex-shrink:0;}.tab-button{min-width:0;padding-left:8rpx;padding-right:8rpx;}.item-title{overflow-wrap:anywhere;}.supplement-form textarea{box-sizing:border-box;width:100%;}.state-view{background:#fffefa;border-radius:24rpx;color:#809486;}
.summary-band,.section-band{background:white!important;border:0!important;border-radius:24rpx!important;padding:26rpx!important;}.student-name{font-size:30rpx;color:#143e34;}.review-date{font-size:24rpx;color:#828c86;}.completion-ring{border-color:#2b8062;background:#eff8ee;}.section-title{font-size:28rpx;color:#143e34;}.supplement-form{background:#f9fbf8;padding:20rpx;border-radius:20rpx;}.content-input{border:1rpx solid #dde5df;border-radius:18rpx;font-size:27rpx;line-height:1.6;min-height:160rpx;padding:20rpx;box-sizing:border-box;}.submit-button{background:#116a50;color:white;border-radius:24rpx;}.supplement-row{border-bottom:1rpx solid #edf0e8;padding:24rpx 0;}.supplement-type{color:#174d3b;font-weight:600;}.supplement-content{line-height:1.7;font-size:26rpx;color:#3e4944;}.date-option.active{background:#176c51;color:white;}
.daily-field{position:relative;}.daily-field .content-input{margin-top:16rpx;padding-bottom:40rpx;}.field-count{display:block;text-align:right;color:#8b958e;font-size:22rpx;margin-top:-36rpx;padding-right:18rpx;position:relative;}
.period-tabs{display:flex;gap:20rpx;margin-bottom:24rpx;}.period-tabs button{flex:1;background:#edf4ed;border-radius:26rpx;color:#176c51;font-size:26rpx;}.period-tabs button.active{background:#176c51;color:white;}.weekly-detail{background:white;border-radius:24rpx;padding:28rpx;}.week-range{display:block;text-align:center;color:#174a39;font-size:30rpx;font-weight:600;}.weekly-student{display:flex;align-items:center;gap:20rpx;margin:30rpx 0;font-size:29rpx;}.weekly-metrics{display:flex;gap:12rpx;}.weekly-metrics>view{flex:1;border:1rpx solid #e7eee8;border-radius:18rpx;padding:18rpx 10rpx;}.weekly-metrics text{display:block;text-align:center;font-size:22rpx;color:#818b85;}.weekly-metrics text:first-child{font-size:32rpx;color:#173e2e;font-weight:600;margin-bottom:10rpx;}.weekly-section{margin-top:28rpx;}.weekly-trend{display:flex;justify-content:space-between;gap:16rpx;padding:18rpx 0;border-bottom:1rpx solid #edf0e8;font-size:24rpx;}
</style>
