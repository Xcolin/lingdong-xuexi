<template>
 <view class="page">
  <text class="title">家庭奖励与兑换</text>
  <button :disabled="busy" @tap="reload">刷新</button>
  <text v-if="busy">正在加载或处理</text><text v-if="error" class="error">{{ error }}</text>
  <picker v-if="children.length" :range="children" range-key="studentName" :value="childIndex" :disabled="busy" @change="changeChild"><view class="field">当前孩子：{{ child?.studentName }}（{{ primary?'主家长':'副家长，只读' }}）</view></picker>
  <text v-if="ready&&!children.length">暂无可查看的孩子</text>
  <view v-if="child">
   <view class="tabs"><button v-if="manage" :disabled="busy" @tap="switchTab('rewards')">奖励库</button><button v-if="review" :disabled="busy" @tap="switchTab('exchanges')">兑换处理</button></view>
   <button v-if="tab==='rewards'&&manage&&primary" :disabled="busy" @tap="edit()">新增奖励</button>
   <view v-if="editing" class="card">
    <text>{{ editingId?'编辑奖励':'新增奖励' }}</text>
    <text>奖励名称</text><input v-model="form.rewardName" :disabled="busy" maxlength="30" />
    <text>所需积分</text><input v-model.number="form.requiredPoints" :disabled="busy" type="number" />
    <text>说明</text><textarea v-model="form.description" :disabled="busy" maxlength="200" />
    <text>有效期（留空为不限，格式：2026-12-31T23:59:59）</text><input v-model="form.expiresAt" :disabled="busy" />
    <picker :range="['下架','上架']" :value="form.status==='ONLINE'?1:0" :disabled="busy" @change="changeStatus"><view class="field">{{ form.status==='ONLINE'?'上架':'下架' }}</view></picker>
    <button :disabled="busy" @tap="save">保存奖励</button><button :disabled="busy" @tap="editing=false">取消编辑</button>
   </view>
   <template v-else-if="ready&&!busy">
    <text>共 {{ total }} 项{{ tab==='rewards'?'奖励':'兑换记录' }}</text>
    <text v-if="!total">暂无{{ tab==='rewards'?'奖励':'兑换记录' }}</text>
    <template v-if="tab==='rewards'">
     <view v-for="reward in rewards" :key="reward.id" class="card">
      <text class="heading">{{ reward.rewardName }}</text><text>{{ reward.requiredPoints }} 积分 · {{ reward.status==='ONLINE'?'上架':'下架' }}</text><text>{{ reward.description||'暂无说明' }}</text><text>有效期：{{ reward.expiresAt?.replace('T',' ')||'不限' }}</text>
      <template v-if="primary&&manage"><button @tap="edit(reward)">编辑奖励</button><button @tap="remove(reward)">删除奖励</button></template>
     </view>
    </template>
    <template v-else>
     <view v-for="exchange in exchanges" :key="exchange.id" class="card">
      <text class="heading">{{ exchange.rewardName }}</text><text>{{ exchange.requiredPoints }} 积分 · {{ statusName(exchange.status) }}</text><text>{{ exchange.description||'暂无说明' }}</text><text>审批截止：{{ exchange.approvalDeadline.replace('T',' ') }}</text><text v-if="exchange.rejectReason">驳回原因：{{ exchange.rejectReason }}</text>
      <template v-if="primary&&review&&exchange.status==='PENDING_APPROVAL'"><button @tap="process(exchange,'approve')">同意兑换</button><textarea v-model="reasons[exchange.id]" maxlength="500" placeholder="驳回原因（必填）" /><button @tap="process(exchange,'reject')">驳回兑换</button></template>
      <button v-if="primary&&review&&exchange.status==='PENDING_VERIFICATION'" @tap="process(exchange,'verify')">确认已兑现</button>
     </view>
    </template>
    <view v-if="total" class="tabs"><button :disabled="page<=1" @tap="loadPage(page-1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page*20>=total" @tap="loadPage(page+1)">下一页</button></view>
   </template>
  </view>
 </view>
</template>
<script setup lang="ts">
import {ref,reactive,computed} from 'vue';
import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import {parentRewardApi,canUseParentRewards,MANAGE_REWARDS,REVIEW_EXCHANGES,type RewardIdentity,type RewardChild,type ParentReward,type RewardInput} from '@/api/parent-reward';
import type {StudentRewardExchange} from '@/api/reward';
import {getMiniappCapabilities} from '@/api/capability';
import {getParentSession} from '@/session/parent-session';
import {ApiError} from '@/api/http';
const children=ref<RewardChild[]>([]),childIndex=ref(0),user=ref<RewardIdentity|null>(null);
const child=computed(()=>children.value[childIndex.value]);
const primary=computed(()=>child.value?.relationshipRole==='PRIMARY_GUARDIAN');
const manage=computed(()=>!!user.value&&canUseParentRewards(user.value,MANAGE_REWARDS));
const review=computed(()=>!!user.value&&canUseParentRewards(user.value,REVIEW_EXCHANGES));
const rewards=ref<ParentReward[]>([]),exchanges=ref<StudentRewardExchange[]>([]),reasons=reactive<Record<string,string>>({});
const tab=ref<'rewards'|'exchanges'>('rewards'),page=ref(1),total=ref(0),busy=ref(false),ready=ref(false),error=ref(''),editing=ref(false),editingId=ref<string|null>(null);
const form=reactive({rewardName:'',requiredPoints:1,description:'',expiresAt:'',status:'OFFLINE' as 'ONLINE'|'OFFLINE'});
let epoch=0;
function current(n:number,token:string){return n===epoch&&getParentSession()?.accessToken===token;}
function clearRows(){rewards.value=[];exchanges.value=[];total.value=0;ready.value=false;editing.value=false;for(const key of Object.keys(reasons))delete reasons[key];}
function clear(){clearRows();children.value=[];user.value=null;}
function failure(cause:unknown){if(cause instanceof ApiError&&([401,403,404].includes(cause.statusCode)||cause.code==='FEATURE_DISABLED'))clear();error.value=cause instanceof Error?cause.message:'操作未完成，请刷新核对结果';}
async function access(n:number,token:string,permission?:string){
 const [identity,capability]=await Promise.all([parentRewardApi.me(token),getMiniappCapabilities()]);
 if(!current(n,token))return false;
 if(!capability.rewardExchangeEnabled||!canUseParentRewards(identity,permission)){clear();throw new Error('奖励功能未开启或无访问权限');}
 user.value=identity;return true;
}
async function reload(){
 const n=++epoch,token=getParentSession()?.accessToken;clear();busy.value=true;error.value='';
 try{if(!token)throw new Error('请先登录家长账号');if(!await access(n,token))return;
 const options=await parentRewardApi.children(token);if(!current(n,token))return;children.value=options;childIndex.value=0;tab.value=manage.value?'rewards':'exchanges';
 if(options.length)await loadPage(1);else ready.value=true;
 }catch(cause){if(n===epoch)failure(cause);}finally{if(n===epoch)busy.value=false;}
}
async function loadPage(target=1){
 const selected=child.value;if(!selected)return;const n=++epoch,token=getParentSession()?.accessToken;clearRows();busy.value=true;error.value='';
 try{if(!token){clear();return;}if(!await access(n,token,tab.value==='rewards'?MANAGE_REWARDS:REVIEW_EXCHANGES))return;
 const result=tab.value==='rewards'?await parentRewardApi.rewards(token,selected.studentId,target):await parentRewardApi.exchanges(token,selected.studentId,target);
 if(!current(n,token))return;if(tab.value==='rewards')rewards.value=result.items as ParentReward[];else exchanges.value=result.items as StudentRewardExchange[];
 page.value=result.page;total.value=result.total;ready.value=true;
 }catch(cause){if(n===epoch)failure(cause);}finally{if(n===epoch)busy.value=false;}
}
function changeChild(event:Event){childIndex.value=Number((event as unknown as {detail:{value:string}}).detail.value);void loadPage(1);}
function changeStatus(event:Event){form.status=Number((event as unknown as {detail:{value:string}}).detail.value)===1?'ONLINE':'OFFLINE';}
function switchTab(value:'rewards'|'exchanges'){tab.value=value;void loadPage(1);}
function edit(reward?:ParentReward){if(busy.value||!primary.value||!manage.value)return;editingId.value=reward?.id||null;Object.assign(form,{rewardName:reward?.rewardName||'',requiredPoints:reward?.requiredPoints||1,description:reward?.description||'',expiresAt:reward?.expiresAt||'',status:reward?.status||'OFFLINE'});editing.value=true;error.value='';}
async function confirm(content:string){return new Promise<boolean>(resolve=>uni.showModal({title:'确认操作',content,success:result=>resolve(result.confirm),fail:()=>resolve(false)}));}
async function mutate(permission:string,content:string,work:(token:string)=>Promise<unknown>){
 if(busy.value||!primary.value)return;const n=++epoch,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';
 try{if(!await confirm(content)||!current(n,token))return;if(!await access(n,token,permission))return;
 await work(token);if(current(n,token))await loadPage(page.value);
 }catch(cause){if(current(n,token))failure(cause);}finally{if(n===epoch)busy.value=false;}
}
async function save(){
 if(!child.value)return;if(!form.rewardName.trim()||!Number.isSafeInteger(form.requiredPoints)||form.requiredPoints<=0){error.value='请填写名称和有效的正整数积分';return;}
 if(form.expiresAt&&!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(form.expiresAt)){error.value='有效期格式应为年-月-日T时:分:秒';return;}
 const input:RewardInput={...form,rewardName:form.rewardName.trim(),description:form.description||null,expiresAt:form.expiresAt||null};const studentId=child.value.studentId,rewardId=editingId.value;
 const confirmation=input.status==='ONLINE'?'确认保存并上架？上架后孩子可申请兑换，审批同意后扣除积分。':'确认保存为下架状态？下架后孩子不能新申请，已有兑换记录保留。';
 await mutate(MANAGE_REWARDS,confirmation,token=>parentRewardApi.save(token,studentId,rewardId,input));
}
async function remove(reward:ParentReward){await mutate(MANAGE_REWARDS,`确认删除“${reward.rewardName}”？已有兑换记录仍保留。`,token=>parentRewardApi.remove(token,reward.id));}
async function process(exchange:StudentRewardExchange,action:'approve'|'reject'|'verify'){
 const reason=(reasons[exchange.id]||'').trim();if(action==='reject'&&!reason){error.value='请填写驳回原因';return;}
 const message=action==='approve'?`确认同意兑换并扣除 ${exchange.requiredPoints} 积分？`:action==='verify'?'请确认奖励已经兑现，再执行核销。':'确认驳回该兑换申请？';
 await mutate(REVIEW_EXCHANGES,message,token=>parentRewardApi.process(token,exchange.id,action,reason));
}
function statusName(status:string){return ({PENDING_APPROVAL:'待审批',PENDING_VERIFICATION:'待核销',REJECTED:'已驳回',AUTO_REJECTED:'自动驳回',EXPIRED:'已过期',VERIFIED:'已核销'} as Record<string,string>)[status]||status;}
function hide(){epoch++;clear();busy.value=false;}
onShow(reload);onHide(hide);onUnload(hide);
</script>
<style scoped>
.page{padding:32rpx;background:#f4f7f5;min-height:100vh;box-sizing:border-box;color:#20352e;font-family:system-ui,"Microsoft YaHei",sans-serif}.page text{display:block;margin:16rpx 0;overflow-wrap:anywhere}.title{font-size:36rpx;font-weight:700}.card{background:white;padding:24rpx;margin-top:24rpx;border-radius:12rpx}.heading{font-weight:600}.error{color:#a33c2e}.tabs{display:flex;gap:12rpx;align-items:center;justify-content:space-between}.field,input,textarea{box-sizing:border-box;width:100%;padding:16rpx;border:1px solid #d8e3dd;margin:12rpx 0}input{height:80rpx}button{font-size:28rpx;margin:12rpx 0}
</style>
