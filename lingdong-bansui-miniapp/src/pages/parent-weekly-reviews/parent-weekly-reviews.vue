<template>
  <view class="page has-tabbar">
    <text class="title">孩子成长周报</text>
    <button :disabled="busy" @tap="initialize">刷新周报</button>
    <text v-if="busy">正在加载周报</text>
    <text v-if="error" class="error">{{ error }}</text>
    <template v-if="allowed">
      <picker v-if="students.length" :range="students" range-key="studentName" :value="selectedIndex" :disabled="busy" @change="changeStudent">
        <view class="selector">孩子：{{ students[selectedIndex]?.studentName }} ▾</view>
      </picker>
      <text v-else-if="!busy">暂无可查看的孩子</text>
      <view v-if="detail" class="card">
        <text class="heading">{{ detail.studentName }}的周报</text>
        <text>{{ detail.periodStart }} 至 {{ detail.periodEnd }}</text>
        <text>任务：{{ detail.taskTotalCount }} 项，完成：{{ detail.completedCount }} 项</text>
        <text>进行中：{{ detail.inProgressCount }}，待优化：{{ detail.pendingOptimizationCount }}，免执行：{{ detail.exemptedCount }}</text>
        <text>完成率：{{ rate(detail.completionRate) }}</text>
        <text>获得积分：{{ detail.earnedPoints }}，暂停次数：{{ detail.pauseCount }}</text>
        <text>数据截至：{{ time(detail.dataCutoffAt) }}</text><text>生成时间：{{ time(detail.generatedAt) }}</text>
        <text class="heading">分类统计</text>
        <text v-if="!detail.categories.length">暂无分类统计</text>
        <text v-for="row in detail.categories" :key="row.categoryCode">{{ row.categoryCode === 'GENERAL' ? '通用' : row.categoryCode }}：{{ row.taskCount }} 项，完成 {{ row.completedCount }} 项</text>
        <text class="heading">每日趋势</text>
        <text v-if="!detail.dailyTrends.length">暂无每日趋势</text>
        <text v-for="row in detail.dailyTrends" :key="row.trendDate">{{ row.trendDate }}：完成 {{ row.completedCount }}/{{ row.taskTotalCount }} 项，完成率 {{ rate(row.completionRate) }}，积分 {{ row.earnedPoints }}，待优化 {{ row.pendingOptimizationCount }} 项，暂停 {{ row.pauseCount }} 次</text>
        <text v-for="row in detail.supplements" :key="row.id" class="supplement">{{ row.supplementedAt }} {{ row.content }}</text>
        <button :disabled="busy" @tap="loadPage(1)">返回周报列表</button>
      </view>
      <template v-else-if="loaded && !busy">
        <text>共 {{ total }} 份周报</text><text v-if="total === 0">暂无已生成周报</text>
        <view v-for="row in rows" :key="row.reviewId" class="card"><text>{{ row.periodStart }} 至 {{ row.periodEnd }}</text><button @tap="open(row.reviewId)">查看周报</button></view>
        <view v-if="total > 0" class="paging"><button :disabled="page <= 1" @tap="loadPage(page - 1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page * 20 >= total" @tap="loadPage(page + 1)">下一页</button></view>
      </template>
    </template>
  <AppTabBar :items="PARENT_TABS" :active="2" />
  </view>
</template>
<script setup lang="ts">
import AppTabBar from '@/components/AppTabBar.vue';
import { PARENT_TABS } from '@/config/tabbar';
import { ref } from 'vue';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { weeklyApi, canReadWeekly, type WeeklyStudent, type WeeklySummary, type WeeklyDetail } from '@/api/parent-weekly-review';
import { getParentSession } from '@/session/parent-session';
const students=ref<WeeklyStudent[]>([]), selectedIndex=ref(0), rows=ref<WeeklySummary[]>([]), detail=ref<WeeklyDetail|null>(null);
const busy=ref(false), allowed=ref(false), loaded=ref(false), error=ref(''), total=ref(0), page=ref(1);
let revision=0, linkedStudent='', linkedReview='';
function clearReport(){ rows.value=[]; detail.value=null; total.value=0; loaded.value=false; }
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
    if(!options.length)return;
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
    else{const result=await weeklyApi.list(token,studentId,target);if(valid(n,token)){rows.value=result.items;total.value=result.total;page.value=result.page;loaded.value=true;}}
  }catch(cause){if(valid(n,token||'')){clearReport();allowed.value=false;error.value=cause instanceof Error?cause.message:'周报加载失败';}}
  finally{if(n===revision)busy.value=false;}
}
function loadPage(target:number){return query(target);}
function open(id:string){return query(1,id);}
function changeStudent(event:Event){selectedIndex.value=Number((event as unknown as {detail:{value:string}}).detail.value);linkedStudent='';linkedReview='';void loadPage(1);}
function rate(value:number){return `${(value*100).toFixed(1)}%`;}
function time(value:string){return value.replace('T',' ');}
function hide(){revision++;clear();busy.value=false;}
onLoad(options=>{linkedStudent=typeof options?.studentId==='string'?options.studentId:'';linkedReview=typeof options?.reviewId==='string'?options.reviewId:'';});
onShow(initialize);onHide(hide);onUnload(hide);
</script>
<style scoped>
.page{padding:32rpx;color:#1c2b28;background:#f4f7f5;min-height:100vh;box-sizing:border-box;font-family:system-ui,"Microsoft YaHei",sans-serif}.page text{display:block;overflow-wrap:anywhere;margin:14rpx 0}.title{font-size:38rpx;font-weight:700}.card{background:#fff;padding:24rpx;margin-top:24rpx;border-radius:12rpx}.heading{font-weight:600}.selector{padding:24rpx;background:#fff}.error{color:#a33c2e}.paging{display:flex;gap:12rpx;align-items:center;justify-content:space-between}button{font-size:28rpx;margin-top:16rpx}.supplement{white-space:pre-wrap}
</style>
