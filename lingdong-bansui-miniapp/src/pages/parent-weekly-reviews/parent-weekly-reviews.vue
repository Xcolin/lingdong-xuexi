<template>
  <view class="prototype-workspace page has-tabbar">
    <PixelPageHeader :title="detail?'周报详情':'成长'" :back="!!detail" intercept-back @back="loadPage(1)" />
    
    <text v-if="busy">正在加载周报</text>
    <text v-if="error" class="error">{{ error }}</text>
    <template v-if="allowed">
      <view v-if="students.length" class="selector-row">
        <picker :range="students" range-key="studentName" :value="selectedIndex" :disabled="busy" @change="changeStudent">
          <view class="selector">孩子：{{ students[selectedIndex]?.studentName }} <text>⌄</text></view>
        </picker>
        <button class="week-pill" :disabled="busy" @tap="loadPage(1)">本周</button>
      </view>
      <view v-else-if="!busy" class="selector selector-empty">暂无可选孩子 <text>⌄</text></view>
      <view v-if="detail" class="card">
        <text class="heading">{{ detail.studentName }}的周报</text>
        <text>{{ detail.periodStart }} 至 {{ detail.periodEnd }}</text>
        <view class="report-stats"><view><PrototypeIcon name="check" :size="20" /><text class="stat-number">{{detail.completedCount}}</text><text>完成任务</text></view><view><PrototypeIcon name="gift" :size="20" /><text class="stat-number">{{detail.earnedPoints}}</text><text>成长积分</text></view><view><PrototypeIcon name="chart" :size="20" /><text class="stat-number">{{rate(detail.completionRate)}}</text><text>完成率</text></view></view><text>任务总数：{{detail.taskTotalCount}} 项</text>
        <text>进行中：{{ detail.inProgressCount }}，待优化：{{ detail.pendingOptimizationCount }}，免执行：{{ detail.exemptedCount }}</text>
        <text>完成率：{{ rate(detail.completionRate) }}</text>
        <text>获得积分：{{ detail.earnedPoints }}，暂停次数：{{ detail.pauseCount }}</text>
        <text>数据截至：{{ time(detail.dataCutoffAt) }}</text><text>生成时间：{{ time(detail.generatedAt) }}</text>
        <text class="heading">分类统计</text>
        <text v-if="!detail.categories.length">暂无分类统计</text>
        <text v-for="row in detail.categories" :key="row.categoryCode">{{ row.categoryCode === 'GENERAL' ? '通用' : row.categoryCode }}：{{ row.taskCount }} 项，完成 {{ row.completedCount }} 项</text>
        <text class="heading">每日趋势</text><view v-if="detail.dailyTrends.length" class="trend-chart"><view v-for="day in detail.dailyTrends" :key="day.trendDate" class="trend-item"><text>{{day.completedCount}}</text><view class="trend-track"><view class="trend-fill" :style="{height:(day.taskTotalCount?Math.min(100,day.completedCount/day.taskTotalCount*100):0)+'%'}"/></view><text>{{ weekday(day.trendDate) }}</text></view></view>
        <text v-if="!detail.dailyTrends.length">暂无每日趋势</text>
        <text v-for="row in detail.dailyTrends" :key="row.trendDate">{{ row.trendDate }}：完成 {{ row.completedCount }}/{{ row.taskTotalCount }} 项，完成率 {{ rate(row.completionRate) }}，积分 {{ row.earnedPoints }}，待优化 {{ row.pendingOptimizationCount }} 项，暂停 {{ row.pauseCount }} 次</text>
        <text v-for="row in detail.supplements" :key="row.id" class="supplement">{{ row.supplementedAt }} {{ row.content }}</text>
        <button :disabled="busy" @tap="loadPage(1)">返回周报列表</button>
      </view>
      <template v-else-if="loaded && !busy">
        <view v-if="overview" class="card overview-card"><text class="heading">成长概览</text><text>{{ overview.periodStart }} — {{ overview.periodEnd }}</text><view class="report-stats"><view><text class="stat-number">{{ overview.completedCount }}</text><text>完成任务</text></view><view><text class="stat-number">{{ overview.earnedPoints }}</text><text>成长积分</text></view></view><view v-if="overview.dailyTrends.length" class="trend-chart"><view v-for="day in overview.dailyTrends" :key="day.trendDate" class="trend-item"><text>{{ day.completedCount }}</text><view class="trend-track"><view class="trend-fill" :style="{height:trendHeight(day.completedCount)+'%'}"/></view><text>{{ weekday(day.trendDate) }}</text></view></view><text v-else>暂无每日趋势</text><button @tap="open(overview.reviewId)">查看完整周报 ›</button></view><view v-else class="card overview-card empty-overview"><text class="heading">成长概览</text><text>暂无已生成周报</text><view class="report-stats"><view><text class="stat-number">—</text><text>完成任务</text></view><view><text class="stat-number">—</text><text>成长积分</text></view></view><view class="empty-trend"><PrototypeIcon name="chart" tone="muted" :size="36"/><text>生成周报后查看成长趋势</text></view></view>
        <text class="recent-heading">近期成长回顾</text><text v-if="total">共 {{ total }} 份周报</text><view v-else class="card empty-recent"><PrototypeIcon name="growth" tone="muted" :size="32"/><view><text>暂无成长回顾</text><text class="empty-copy">孩子的成长记录将在这里呈现</text></view></view>
        <view v-for="row in rows" :key="row.reviewId" class="card"><view class="recent-row"><view><text class="recent-title">成长周报</text><text class="recent-date">{{ monthDay(row.periodEnd) }}</text></view><button class="recent-open" @tap="open(row.reviewId)">查看 ›</button></view></view>
        <view v-if="total > 0" class="paging"><button :disabled="page <= 1" @tap="loadPage(page - 1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page * 20 >= total" @tap="loadPage(page + 1)">下一页</button></view>
        <view v-if="rewardAccount" class="card reward-band"><view class="reward-head"><text class="heading">成长奖励</text><button class="reward-link" @tap="gotoRewards">查看全部 ›</button></view><text class="reward-points">已累计获得 {{ rewardAccount.totalPoints }} 积分</text><text class="reward-copy">坚持努力，积累更丰富的奖励吧！</text></view>
      </template>
    </template>
  <AppTabBar :items="PARENT_TABS" :active="2" />
  </view>
</template>
<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { PARENT_TABS } from '@/config/tabbar';
import { ref } from 'vue';
import { latestWeekly, weeklyOverview, trendHeight as chartHeight } from './overview-model';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { weeklyApi, canReadWeekly, type WeeklyStudent, type WeeklySummary, type WeeklyDetail } from '@/api/parent-weekly-review';
import { getChildGrowthPointAccount, type GrowthPointAccount } from '@/api/growth-point';
import { getParentSession } from '@/session/parent-session';
const students=ref<WeeklyStudent[]>([]), selectedIndex=ref(0), rows=ref<WeeklySummary[]>([]), detail=ref<WeeklyDetail|null>(null);
const overview=ref<WeeklyDetail|null>(null);
const rewardAccount=ref<GrowthPointAccount|null>(null);
function trendHeight(count:number){return chartHeight(overview.value,count);}
const busy=ref(false), allowed=ref(false), loaded=ref(false), error=ref(''), total=ref(0), page=ref(1);
let revision=0, linkedStudent='', linkedReview='';
function clearReport(){ overview.value=null; rows.value=[]; detail.value=null; total.value=0; loaded.value=false; rewardAccount.value=null; }
function clear(){ clearReport(); students.value=[]; allowed.value=false; }
function valid(n:number,token:string){return n===revision && getParentSession()?.accessToken===token;}
async function access(n:number,token:string){const user=await weeklyApi.me(token);if(!valid(n,token))return false;if(!canReadWeekly(user))throw new Error('无家长周报读取权限');allowed.value=true;return true;}
async function initialize(){
  const n=++revision,token=getParentSession()?.accessToken;clear();error.value='';busy.value=true;
  try{
    if(!token)throw new Error('请先登录家长账号');
    if((linkedStudent||linkedReview)&&(!/^[1-9][0-9]{18}$/.test(linkedStudent)||!/^[1-9][0-9]{18}$/.test(linkedReview)))throw new Error('周报链接不合法');
    if(!await access(n,token))return;
    const options=await weeklyApi.students(token);if(!valid(n,token))return;students.value=options;
    if(linkedStudent){selectedIndex.value=options.findIndex(item=>item.studentId===linkedStudent);if(selectedIndex.value<0)throw new Error('周报不可访问');}
    else selectedIndex.value=0;
    if(!options.length){loaded.value=true;return;}
    if(linkedReview)await open(linkedReview);else await loadPage(1);
  }catch(cause){if(n===revision){clear();error.value=cause instanceof Error?cause.message:'周报加载失败';}}
  finally{if(n===revision)busy.value=false;}
}
async function query(target:number,reviewId?:string){
  const n=++revision,token=getParentSession()?.accessToken,studentId=students.value[selectedIndex.value]?.studentId;
  clearReport();error.value='';busy.value=true;
  try{
    if(!token||!studentId)throw new Error('请选择可访问的孩子');
    if(!await access(n,token))return;
    if(reviewId){const result=await weeklyApi.detail(token,studentId,reviewId);if(valid(n,token))detail.value=result;}
    else{const result=await weeklyApi.list(token,studentId,target);if(!valid(n,token))return;const latest=latestWeekly(result.items);const report=latest?await weeklyApi.detail(token,studentId,latest.reviewId):null;if(valid(n,token)){overview.value=weeklyOverview(report);rows.value=result.items;total.value=result.total;page.value=result.page;loaded.value=true;void loadRewardAccount(n,token,studentId);}}
  }catch(cause){if(valid(n,token||'')){clearReport();allowed.value=false;error.value=cause instanceof Error?cause.message:'周报加载失败';}}
  finally{if(n===revision)busy.value=false;}
}
function loadPage(target:number){return query(target);}
async function loadRewardAccount(n:number,token:string,studentId:string){try{const account=await getChildGrowthPointAccount(token,studentId);if(valid(n,token))rewardAccount.value=account;}catch{if(valid(n,token))rewardAccount.value=null;}}
function gotoRewards(){void uni.navigateTo({url:'/pages/parent-rewards/parent-rewards'});}
function weekday(value:string){const d=new Date(value+'T00:00:00');return '周'+'一二三四五六日'[d.getDay()];}
function monthDay(value:string){const parts=value.split('-');return `${Number(parts[1])}月${Number(parts[2])}日`;}
function open(id:string){return query(1,id);}
function changeStudent(event:Event){selectedIndex.value=Number((event as unknown as {detail:{value:string}}).detail.value);linkedStudent='';linkedReview='';void loadPage(1);}
function rate(value:number){return `${(value*100).toFixed(1)}%`;}
function time(value:string){return value.replace('T',' ');}
function hide(){revision++;clear();busy.value=false;}
onLoad(options=>{linkedStudent=typeof options?.studentId==='string'?options.studentId:'';linkedReview=typeof options?.reviewId==='string'?options.reviewId:'';});
onShow(initialize);onHide(hide);onUnload(hide);
</script>
<style scoped>
.page{padding:0 28rpx 160rpx;color:#193e32;background:#f5f8f4;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box}.prototype-header{display:flex;align-items:center;margin-bottom:26rpx}.prototype-header>.prototype-icon{display:none}.prototype-header-title{font-size:48rpx;font-weight:700}.selector{display:inline-flex;padding:18rpx 28rpx;background:#fff;border:1rpx solid #e7eee7;border-radius:44rpx;font-size:30rpx}.card{background:linear-gradient(135deg,#eaf3e9,#fff);padding:28rpx;margin-top:28rpx;border-radius:28rpx;box-shadow:0 8rpx 22rpx #193e3208}.card>text{display:block;font-size:24rpx;line-height:1.7;color:#768178;margin-top:8rpx}.card>.heading{font-size:30rpx;font-weight:650;color:#193e32}.report-stats{display:flex;padding:24rpx 0;text-align:center}.report-stats>view{flex:1}.report-stats>view+view{border-left:1rpx solid #dde7df}.report-stats text{display:block;font-size:24rpx}.report-stats .stat-number{font-size:54rpx;font-weight:700;color:#152c22}.report-stats .prototype-icon{display:none}.trend-chart{display:flex;gap:28rpx;margin:24rpx 0}.trend-item{flex:1;text-align:center}.trend-item text{display:block;font-size:20rpx;color:#768178}.trend-track{height:130rpx;display:flex;align-items:flex-end;border-bottom:1rpx solid #dfe7df}.trend-fill{width:100%;background:linear-gradient(#245f45,#75ae85);border-radius:16rpx 16rpx 0 0;min-height:2rpx}.error{display:block;color:#a33c2e;margin:24rpx 0}.paging{display:flex;align-items:center;justify-content:space-between;gap:14rpx}button{font-size:26rpx;color:#245f45;background:#e8f0e7;border-radius:20rpx;margin-top:22rpx}button::after{border:0}.supplement{white-space:pre-wrap}
.selector-row{display:flex;align-items:center;justify-content:space-between;gap:18rpx;margin-top:4rpx}.week-pill{margin:0;flex:0 0 auto;height:64rpx;line-height:64rpx;padding:0 36rpx;border-radius:36rpx;background:#176c51;color:#fff;font-size:26rpx}.recent-row{display:flex;align-items:center;justify-content:space-between;gap:18rpx}.recent-title{font-size:28rpx;font-weight:650;color:#193e32;margin:0}.recent-date{color:#768178;font-size:23rpx;margin-top:6rpx}.recent-open{margin:0;background:transparent;color:#176c51;font-size:24rpx;padding:0}.reward-band{margin-top:20rpx}.reward-head{display:flex;align-items:center;justify-content:space-between;gap:18rpx}.reward-head .heading{font-size:30rpx;font-weight:650;color:#193e32}.reward-link{margin:0;background:transparent;color:#176c51;font-size:24rpx;padding:0}.reward-points{display:block;margin-top:14rpx;font-size:27rpx;color:#41544a;font-weight:600}.reward-copy{display:block;margin-top:6rpx;font-size:23rpx;color:#849087}
</style>

<style scoped>
.overview-card{background:linear-gradient(125deg,#edf5ec,#f8fbf7);border:1rpx solid #e2ece0;padding:20rpx 24rpx;}.overview-card .report-stats{padding:16rpx 0;}.overview-card .stat-number{font-size:50rpx;}.overview-card .trend-chart{gap:16rpx;margin:16rpx 0 0;}.overview-card .trend-track{height:110rpx;}.overview-card .trend-fill{width:26rpx;margin:auto auto 0;border-radius:12rpx 12rpx 0 0;min-height:0;}.overview-card button{font-size:22rpx;margin-top:14rpx;background:transparent;text-align:right;}.recent-heading{display:block;font-size:28rpx;font-weight:650;margin:32rpx 0 14rpx;}.selector{font-size:25rpx;padding:14rpx 24rpx;}.card{margin-top:20rpx;}
</style>

<style scoped>
.selector-empty{color:#768178;min-width:210rpx;justify-content:space-between;}.selector-empty>text{margin-left:28rpx;}.empty-overview .stat-number{color:#7b8b7e;}.empty-trend{height:154rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16rpx;border-top:1rpx solid #e2ece0;color:#87968a;font-size:23rpx;}.empty-recent{display:flex;align-items:center;gap:20rpx;background:white;padding:26rpx;}.empty-recent text{display:block;font-size:26rpx;color:#304738;}.empty-recent .empty-copy{font-size:22rpx;color:#849087;margin-top:8rpx;}
</style>
