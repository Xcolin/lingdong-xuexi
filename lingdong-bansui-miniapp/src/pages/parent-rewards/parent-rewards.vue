<template>
 <view class="prototype-workspace page">
    <view class="prototype-header"><PrototypeIcon name="gift" tone="white" :size="26" /><text class="prototype-header-title">{{verification?'奖励核销确认':editing?(editingId?'编辑奖励':'新增奖励'):tab==='rewards'?'奖励库管理':'兑换申请审批'}}</text></view>
  
  <view v-if="verification" class="verification-screen"><text class="verification-title">奖励核销确认</text><view class="verification-card"><PrototypeIcon name="gift" tone="amber" :size="60"/><text class="heading">{{verification.rewardName}}</text><text class="verification-price">{{verification.requiredPoints}} 分</text><view class="verification-row"><text>兑换对象</text><text>{{child?.studentName}}</text></view><view class="verification-row"><text>申请时间</text><text>{{verification.requestedAt.replace('T',' ').slice(0,16)}}</text></view><view v-if="verification.reviewedAt" class="verification-row"><text>审批时间</text><text>{{verification.reviewedAt.replace('T',' ').slice(0,16)}}</text></view><view class="verification-row"><text>审批状态</text><text>{{statusName(verification.status)}}</text></view><text v-if="verification.description">{{verification.description}}</text></view><checkbox-group @change="verifiedDelivery=$event.detail.value.includes('delivered')"><label class="delivery-check"><checkbox value="delivered" :checked="verifiedDelivery" color="#176c51"/>我已确认奖励已经兑现给 {{child?.studentName}}</label></checkbox-group><text v-if="error" class="error">{{error}}</text><button class="verification-submit" :disabled="busy||!verifiedDelivery" @tap="completeVerification">确认核销</button><button :disabled="busy" @tap="verification=null;verifiedDelivery=false">返回</button></view>
  <template v-else>
  <text v-if="busy">正在加载或处理</text><text v-if="error" class="error">{{ error }}</text>
  <picker v-if="children.length" :range="children" range-key="studentName" :value="childIndex" :disabled="busy" @change="changeChild"><view class="field">当前孩子：{{ child?.studentName }}（{{ primary?'主家长':'副家长，只读' }}）</view></picker>
  <text v-if="ready&&!children.length">暂无可查看的孩子</text>
  <view v-if="child">
   <view class="tabs"><button v-if="manage" :disabled="busy" @tap="switchTab('rewards')">奖励库</button><button v-if="review" :disabled="busy" @tap="switchTab('exchanges')">兑换处理</button></view>
   <button v-if="tab==='rewards'&&manage&&primary" :disabled="busy" @tap="edit()">新增奖励</button>
   <view v-if="editing" class="card reward-editor">
    <text class="editor-title">{{ editingId?'编辑奖励':'新增奖励' }}</text>
    <view class="form-row"><text class="form-label">奖励名称<text class="req">*</text></text><input v-model="form.rewardName" :disabled="busy" maxlength="30" placeholder="请输入奖励名称" /></view>
    <view class="form-row"><text class="form-label">奖励分类<text class="req">*</text></text><picker :range="categoryLabels" :value="categoryIndex" :disabled="busy" @change="changeCategory"><view class="field picker-field"><text>{{ categoryLabels[categoryIndex] }}</text><text class="picker-caret">∨</text></view></picker></view>
    <view class="form-row"><text class="form-label">所需积分<text class="req">*</text></text><view class="unit-field"><input v-model.number="form.requiredPoints" :disabled="busy" type="number" /><text class="unit">分</text></view></view>
    <view class="form-row"><text class="form-label">库存数量<text class="req">*</text></text><view class="unit-field"><input v-model.number="form.stockQuantity" :disabled="busy" type="number" /><text class="unit">个</text></view></view>
    <view class="form-row"><text class="form-label">奖励图片<text class="req">*</text></text><view class="image-row"><view v-if="imagePreviewPath" class="image-thumb"><image :src="imagePreviewPath" mode="aspectFill" /><text class="image-remove" @tap="removeImage">×</text></view><view class="image-add" :class="{ uploading: uploadingImage }" @tap="chooseRewardImage"><text class="image-plus">+</text><text class="image-add-label">{{ uploadingImage ? '上传中' : '添加图片' }}</text></view></view><text v-if="imageError" class="upload-error">{{ imageError }}</text></view>
    <view class="form-row"><text class="form-label">奖励描述</text><textarea v-model="form.description" :disabled="busy" maxlength="200" placeholder="请输入奖励描述" /><text class="desc-count">{{ (form.description||'').length }}/200</text></view>
    <view class="form-row"><text class="form-label">有效期</text><view class="expiry-controls"><picker mode="date" :value="form.expiresAt.slice(0,10)" :disabled="busy" @change="expiryDate"><view class="field">{{form.expiresAt?form.expiresAt.slice(0,10):'选择日期（可选）'}}</view></picker><picker v-if="form.expiresAt" mode="time" :value="form.expiresAt.slice(11,16)" :disabled="busy" @change="expiryTime"><view class="field">{{form.expiresAt.slice(11,19)}}</view></picker></view><button v-if="form.expiresAt" :disabled="busy" @tap="form.expiresAt=''">不限有效期</button></view>
    <view class="form-row status-row"><text class="form-label">上架状态</text><view class="switch-block"><switch :checked="form.status==='ONLINE'" color="#13674e" :disabled="busy" @change="toggleStatus" /><text class="status-label">{{ form.status==='ONLINE'?'上架中':'未上架' }}</text></view></view>
    <view class="reward-editor-actions"><button :disabled="busy" @tap="editing=false">取消</button><button class="reward-save" :disabled="busy" @tap="save">保 存</button></view>
   </view>
   <template v-else-if="ready&&!busy">
    <text>共 {{ total }} 项{{ tab==='rewards'?'奖励':'兑换记录' }}</text>
    <text v-if="!total">暂无{{ tab==='rewards'?'奖励':'兑换记录' }}</text>
    <template v-if="tab==='rewards'"><view class="tabs"><button @tap="rewardFilter='ALL'">全部</button><button @tap="rewardFilter='ONLINE'">上架中</button><button @tap="rewardFilter='OFFLINE'">已下架</button></view>
     <view v-for="reward in visibleRewards" :key="reward.id" class="card">
      <text class="heading">{{ reward.rewardName }}</text><text>{{ reward.requiredPoints }} 积分 · {{ reward.status==='ONLINE'?'上架':'下架' }}</text><text>{{ reward.description||'暂无说明' }}</text><text>有效期：{{ reward.expiresAt?.replace('T',' ').slice(0,19)||'不限' }}</text>
      <template v-if="primary&&manage"><button class="reward-edit" :disabled="busy" @tap="edit(reward)">编辑奖励</button><button class="reward-delete" :disabled="busy" @tap="remove(reward)">删除奖励</button></template>
     </view>
    </template>
    <template v-else><view class="tabs"><button @tap="exchangeFilter='PENDING_APPROVAL'">待审批</button><button @tap="exchangeFilter='PENDING_VERIFICATION'">待兑现</button><button @tap="exchangeFilter='VERIFIED'">已核销</button><button @tap="exchangeFilter='REJECTED'">已拒绝</button><button @tap="exchangeFilter='ALL'">全部</button></view>
     <view v-for="exchange in visibleExchanges" :key="exchange.id" class="card">
      <text class="heading">{{ exchange.rewardName }}</text><text>{{ exchange.requiredPoints }} 积分 · {{ statusName(exchange.status) }}</text><text>{{ exchange.description||'暂无说明' }}</text><text>审批截止：{{ exchange.approvalDeadline.replace('T',' ').slice(0,19) }}</text><text v-if="exchange.rejectReason">驳回原因：{{ exchange.rejectReason }}</text>
      <template v-if="primary&&review&&exchange.status==='PENDING_APPROVAL'"><button class="exchange-approve" :disabled="busy" @tap="process(exchange,'approve')">同意兑换</button><textarea :disabled="busy" v-model="reasons[exchange.id]" maxlength="500" placeholder="驳回原因（必填）" /><button class="exchange-reject" :disabled="busy||!reasons[exchange.id]?.trim()" @tap="process(exchange,'reject')">驳回兑换</button></template>
      <button class="exchange-verify" :disabled="busy" v-if="primary&&review&&exchange.status==='PENDING_VERIFICATION'" @tap="process(exchange,'verify')">确认已兑现</button>
     </view>
    </template>
    <view v-if="total" class="tabs"><button :disabled="page<=1" @tap="loadPage(page-1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page*20>=total" @tap="loadPage(page+1)">下一页</button></view>
   </template>
  </view>
 </template>
 </view>
</template>
<script setup lang="ts">
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {ref,reactive,computed} from 'vue';
import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import {parentRewardApi,canUseParentRewards,MANAGE_REWARDS,REVIEW_EXCHANGES,REWARD_CATEGORY_LABELS,type RewardIdentity,type RewardChild,type ParentReward,type RewardInput,type RewardCategoryValue} from '@/api/parent-reward';
import {uploadRewardImage,downloadTaskAttachmentAs} from '@/api/attachment';
import type {StudentRewardExchange} from '@/api/reward';
import {getMiniappCapabilities} from '@/api/capability';
import {getParentSession} from '@/session/parent-session';
import {ApiError} from '@/api/http';
const rewardFilter=ref('ALL'),exchangeFilter=ref('ALL');const visibleRewards=computed(()=>rewards.value.filter(r=>rewardFilter.value==='ALL'||r.status===rewardFilter.value));const visibleExchanges=computed(()=>exchanges.value.filter(r=>exchangeFilter.value==='ALL'||r.status===exchangeFilter.value));
const verification=ref<StudentRewardExchange|null>(null),verifiedDelivery=ref(false);
const children=ref<RewardChild[]>([]),childIndex=ref(0),user=ref<RewardIdentity|null>(null);
const child=computed(()=>children.value[childIndex.value]);
const primary=computed(()=>child.value?.relationshipRole==='PRIMARY_GUARDIAN');
const manage=computed(()=>!!user.value&&canUseParentRewards(user.value,MANAGE_REWARDS));
const review=computed(()=>!!user.value&&canUseParentRewards(user.value,REVIEW_EXCHANGES));
const rewards=ref<ParentReward[]>([]),exchanges=ref<StudentRewardExchange[]>([]),reasons=reactive<Record<string,string>>({});
const tab=ref<'rewards'|'exchanges'>('rewards'),page=ref(1),total=ref(0),busy=ref(false),ready=ref(false),error=ref(''),editing=ref(false),editingId=ref<string|null>(null);
const form=reactive({rewardName:'',requiredPoints:1,category:'STUDY_SUPPLIES' as RewardCategoryValue,stockQuantity:1,imageUrl:null as string|null,description:'',expiresAt:'',status:'OFFLINE' as 'ONLINE'|'OFFLINE'});
const categoryValues:RewardCategoryValue[]=['STUDY_SUPPLIES','LIFE_GOODS','EXPERIENCE'];
const categoryLabels=categoryValues.map(value=>REWARD_CATEGORY_LABELS[value]);
const categoryIndex=ref(0);
const imagePreviewPath=ref(''),imageError=ref(''),uploadingImage=ref(false);
let epoch=0;
function current(n:number,token:string){return n===epoch&&getParentSession()?.accessToken===token;}
function clearRows(){verification.value=null;verifiedDelivery.value=false;rewards.value=[];exchanges.value=[];total.value=0;ready.value=false;editing.value=false;for(const key of Object.keys(reasons))delete reasons[key];}
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
function expiryDate(event:Event){const d=(event as unknown as {detail:{value:string}}).detail.value;form.expiresAt=d+'T'+(form.expiresAt.slice(11)||'23:59:59');}
function expiryTime(event:Event){const t=(event as unknown as {detail:{value:string}}).detail.value;form.expiresAt=form.expiresAt.slice(0,10)+'T'+t+':00';}
function changeCategory(event:Event){categoryIndex.value=Number((event as unknown as {detail:{value:string}}).detail.value);form.category=categoryValues[categoryIndex.value];}
function toggleStatus(event:Event){form.status=(event as unknown as {detail:{value:boolean}}).detail.value?'ONLINE':'OFFLINE';}
function switchTab(value:'rewards'|'exchanges'){tab.value=value;void loadPage(1);}
function edit(reward?:ParentReward){if(busy.value||!primary.value||!manage.value)return;editingId.value=reward?.id||null;imagePreviewPath.value='';imageError.value='';uploadingImage.value=false;Object.assign(form,{rewardName:reward?.rewardName||'',requiredPoints:reward?.requiredPoints||1,category:reward?.category||'STUDY_SUPPLIES',stockQuantity:reward?.stockQuantity??1,imageUrl:reward?.imageUrl||null,description:reward?.description||'',expiresAt:reward?.expiresAt||'',status:reward?.status||'OFFLINE'});categoryIndex.value=Math.max(0,categoryValues.indexOf(form.category));if(form.imageUrl)void loadImagePreview(form.imageUrl);editing.value=true;error.value='';}
async function loadImagePreview(imageUrl:string){const token=getParentSession()?.accessToken;if(!token)return;try{imagePreviewPath.value=await downloadTaskAttachmentAs(token,imageUrl);}catch{imageError.value='已保存图片预览读取失败，不影响保存';}}
function removeImage(){form.imageUrl=null;imagePreviewPath.value='';imageError.value='';}
async function chooseRewardImage(){if(busy.value||uploadingImage.value)return;uni.chooseImage({count:1,sizeType:['compressed'],success:selection=>{const path=selection.tempFilePaths?.[0];if(path)void uploadSelectedImage(path);},fail:()=>{imageError.value='未选择图片';}});}
async function uploadSelectedImage(filePath:string){const token=getParentSession()?.accessToken;if(!token){error.value='请先登录家长账号';return;}uploadingImage.value=true;imageError.value='';try{const attachment=await uploadRewardImage(token,filePath);form.imageUrl=attachment.contentUrl;imagePreviewPath.value=filePath;}catch(cause){imageError.value=cause instanceof ApiError&&cause.statusCode===403?'图片上传通道未对家长开放，暂时无法上传图片':cause instanceof Error?cause.message:'图片上传未能完成';}finally{uploadingImage.value=false;}}
async function confirm(content:string){return new Promise<boolean>(resolve=>uni.showModal({title:'确认操作',content,success:result=>resolve(result.confirm),fail:()=>resolve(false)}));}
async function mutate(permission:string,content:string,work:(token:string)=>Promise<unknown>,confirmed=false){
 if(busy.value||!primary.value)return;const n=++epoch,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';
 try{if((!confirmed&&!await confirm(content))||!current(n,token))return;if(!await access(n,token,permission))return;
 await work(token);if(current(n,token))await loadPage(page.value);
 }catch(cause){if(current(n,token))failure(cause);}finally{if(n===epoch)busy.value=false;}
}
async function save(){
 if(!child.value)return;if(!form.rewardName.trim()||!Number.isSafeInteger(form.requiredPoints)||form.requiredPoints<=0){error.value='请填写名称和有效的正整数积分';return;}
 if(!Number.isSafeInteger(form.stockQuantity)||form.stockQuantity<0){error.value='库存数量应为不小于 0 的整数';return;}
 if(form.expiresAt&&!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$/.test(form.expiresAt)){error.value='有效期格式应为年-月-日T时:分:秒';return;}
 const input:RewardInput={...form,rewardName:form.rewardName.trim(),description:form.description||null,expiresAt:form.expiresAt||null};const studentId=child.value.studentId,rewardId=editingId.value;
 const confirmation=input.status==='ONLINE'?'确认保存并上架？上架后孩子可申请兑换，审批同意后扣除积分。':'确认保存为下架状态？下架后孩子不能新申请，已有兑换记录保留。';
 await mutate(MANAGE_REWARDS,confirmation,token=>parentRewardApi.save(token,studentId,rewardId,input));
}
async function remove(reward:ParentReward){await mutate(MANAGE_REWARDS,`确认删除“${reward.rewardName}”？已有兑换记录仍保留。`,token=>parentRewardApi.remove(token,reward.id));}
async function process(exchange:StudentRewardExchange,action:'approve'|'reject'|'verify'){
 if(action==='verify'){if(busy.value||!primary.value||!review.value||exchange.status!=='PENDING_VERIFICATION')return;verification.value=exchange;verifiedDelivery.value=false;error.value='';return;}
 const reason=(reasons[exchange.id]||'').trim();if(action==='reject'&&!reason){error.value='请填写驳回原因';return;}
 const message=action==='approve'?`确认同意兑换并扣除 ${exchange.requiredPoints} 积分？`:'确认驳回该兑换申请？';
 await mutate(REVIEW_EXCHANGES,message,token=>parentRewardApi.process(token,exchange.id,action,reason));
}
async function completeVerification(){
 const exchange=verification.value;if(!exchange||!verifiedDelivery.value||busy.value||!primary.value||!review.value||exchange.status!=='PENDING_VERIFICATION')return;
 await mutate(REVIEW_EXCHANGES,'',token=>parentRewardApi.process(token,exchange.id,'verify',''),true);
}
function statusName(status:string){return ({PENDING_APPROVAL:'待审批',PENDING_VERIFICATION:'待核销',REJECTED:'已驳回',AUTO_REJECTED:'自动驳回',EXPIRED:'已过期',VERIFIED:'已核销'} as Record<string,string>)[status]||status;}
function hide(){epoch++;clear();busy.value=false;}
onShow(reload);onHide(hide);onUnload(hide);
</script>










<style lang="scss" scoped>

.page{padding:32rpx;background:#f4f7f5;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;color:#20352e;font-family:system-ui,"Microsoft YaHei",sans-serif}.page text{display:block;margin:16rpx 0;overflow-wrap:anywhere}.title{font-size:36rpx;font-weight:700}.card{background:white;padding:24rpx;margin-top:24rpx;border-radius:12rpx}.heading{font-weight:600}.error{color:#a33c2e}.tabs{display:flex;gap:12rpx;align-items:center;justify-content:space-between}.field,input,textarea{box-sizing:border-box;width:100%;padding:16rpx;border:1px solid #d8e3dd;margin:12rpx 0}input{height:80rpx}button{font-size:28rpx;margin:12rpx 0}

@import "@/styles/workspace-prototype.scss";

.reward-editor{position:fixed;inset:var(--window-top,0px) 0 0;z-index:110;overflow-y:auto;padding:20rpx 28rpx calc(140rpx + env(safe-area-inset-bottom))!important;margin:0!important;background:#F6F8F5!important;border-radius:0!important;}
.reward-editor input{min-height:60rpx!important;}
.reward-editor .form-label{margin:10rpx 0 6rpx;font-size:25rpx;}
.reward-editor-actions{position:fixed;bottom:0;left:0;right:0;padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));background:#fffefa;display:flex;gap:20rpx;border-top:1rpx solid #e0e8e1;}
.reward-editor-actions button{flex:1;border-radius:18rpx;font-size:28rpx;margin:0!important;}
.reward-editor-actions .reward-save{background:#184F40;color:white;}
.expiry-controls{display:flex;gap:16rpx;}.expiry-controls picker{flex:1;min-width:0;}
.prototype-workspace .card .heading{font-size:30rpx;color:#193e32;}
.prototype-workspace .tabs{display:flex;gap:16rpx;margin:24rpx 0;}
.prototype-workspace .tabs button{flex:1;margin:0;border:1rpx solid #dce7de;border-radius:16rpx;background:#fffefa;color:#184F40;}


.prototype-workspace{background:#f4faf7!important;padding:0 28rpx 48rpx!important;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;color:#172d27;}
.prototype-workspace.has-tabbar{padding-bottom:160rpx!important;}
.prototype-header{margin:0 -28rpx 28rpx;padding:32rpx 32rpx 48rpx;display:flex;align-items:center;gap:18rpx;background:linear-gradient(115deg,#226d5c,#07573f);color:#fff;border-radius:0 0 30rpx 30rpx;}
.prototype-header-title{font-size:34rpx;font-weight:700;letter-spacing:1rpx;}
.card,.section-band,.summary-band,.action-band,.heading-band{background:#fff!important;border:1rpx solid #e2eee7!important;border-radius:24rpx!important;box-shadow:none!important;margin-bottom:22rpx;padding:24rpx!important;}
input,textarea,.field{background:#fbfcfb!important;border:1rpx solid #dce5df!important;border-radius:14rpx!important;padding:18rpx!important;box-sizing:border-box;font-size:26rpx!important;}
input{min-height:76rpx;}textarea{width:100%;}
button{border-radius:18rpx!important;font-size:26rpx!important;}button::after{border:0;}
.heading,.section-title,.row-title,.task-title{color:#162b24;font-weight:650;font-size:28rpx;}
.row-meta,.task-meta,.hint{color:#78847e;font-size:24rpx;line-height:1.65;}
.list-row,.relationship-row,.task-row{border-bottom:1rpx solid #edf1ee;padding:24rpx 0;}
.current-tag,.read-tag,.source-badge{background:#e7f7e9;color:#168450;border-radius:10rpx;padding:6rpx 12rpx;font-size:22rpx;}
 .reward-editor-actions{position:sticky;bottom:24rpx;background:white;padding:18rpx 0;}.card>.heading{font-size:29rpx;}.card>.heading+text{color:#ea920c;font-weight:650;}.tabs{background:#fff;border-radius:18rpx;padding:8rpx;margin-bottom:22rpx;}

.card>text{display:block;line-height:1.65;margin-bottom:12rpx;overflow-wrap:anywhere;}.reward-edit{background:#edf4ee!important;color:#184f40!important;}.reward-delete,.exchange-reject{background:#fff3f0!important;color:#b65a4d!important;border:1rpx solid #f0d4cd;}.exchange-approve,.exchange-verify,.reward-save{background:#184f40!important;color:#fff!important;}.card>button{margin-top:16rpx;}.card>textarea{margin:22rpx 0 12rpx;min-height:150rpx;}.reward-editor-actions{display:flex;gap:16rpx;}.reward-editor-actions button{flex:1;min-width:0;}.expiry-controls{display:flex;gap:14rpx;}.expiry-controls picker{flex:1;min-width:0;}.tabs{align-items:center;}.tabs button{min-width:0;}.field{overflow-wrap:anywhere;}button[disabled]{opacity:.45;}

.verification-screen{padding:24rpx 0;}.verification-title{font-size:34rpx;text-align:center;font-weight:600;}.verification-card{background:white;border-radius:24rpx;padding:28rpx;margin:26rpx 0;}.verification-price{color:#f7940b;font-size:36rpx;font-weight:600;}.verification-row{display:flex;justify-content:space-between;gap:24rpx;border-bottom:1rpx solid #edf0e8;font-size:26rpx;}.delivery-check{display:flex;align-items:center;font-size:25rpx;line-height:1.6;margin:28rpx 0;}.verification-submit{background:#176c51;color:white;border-radius:20rpx;font-size:30rpx;}
.editor-title{display:block;font-size:32rpx;font-weight:650;color:#162b24;margin-bottom:10rpx;}
.form-row{margin-bottom:0;}
.form-label{display:block;font-size:26rpx;color:#3c4a44;font-weight:550;margin:12rpx 0 6rpx;}
.form-label .req{display:inline;margin:0;}
.req{color:#d4553f;margin-left:4rpx;}
.picker-field{display:flex;justify-content:space-between;align-items:center;}
.picker-caret{color:#9aa7a0;font-size:24rpx;}
.unit-field{display:flex;align-items:center;gap:12rpx;background:#fbfcfb!important;border:1rpx solid #dce5df!important;border-radius:14rpx!important;padding:2rpx 18rpx 2rpx 0;}
.unit-field input{flex:1;border:0!important;margin:0!important;background:transparent!important;min-width:0;min-height:64rpx;}
.unit{color:#8a968d;font-size:26rpx;}
.image-row{display:flex;gap:18rpx;margin:8rpx 0;}
.image-add{width:132rpx;height:132rpx;border:2rpx dashed #c8d6cd;border-radius:16rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx;background:#fbfcfb;color:#8a968d;font-size:24rpx;}
.image-add.uploading{opacity:.6;}
.image-plus{font-size:44rpx;line-height:1;color:#a9b6ae;}
.image-add-label{font-size:22rpx;}
.image-thumb{position:relative;width:132rpx;height:132rpx;border-radius:16rpx;overflow:hidden;border:1rpx solid #dce5df;}
.image-thumb image{width:100%;height:100%;display:block;}
.image-remove{position:absolute;top:0;right:0;background:rgba(0,0,0,.45);color:#fff;font-size:26rpx;padding:0 14rpx;line-height:1.7;border-radius:0 0 0 12rpx;}
.upload-error{color:#a33c2e;font-size:24rpx;margin:6rpx 0;}
.desc-count{display:block;text-align:right;color:#9aa7a0;font-size:22rpx;margin:0 0 4rpx;}
.status-row{display:flex;align-items:center;justify-content:space-between;}
.status-row .form-label{margin:12rpx 0;}
.reward-editor .card>textarea,.reward-editor textarea{margin:6rpx 0!important;min-height:96rpx!important;height:96rpx!important;}
.switch-block{display:flex;align-items:center;gap:14rpx;}
.status-label{font-size:26rpx;color:#13674e;font-weight:550;}
</style>
