<template>
 <view class="secondary-page" :class="{'has-footer':editing}">
  <PrototypePageHeader :title="created?'孩子已添加':editing?'添加孩子':detail?'孩子档案':'我的孩子'" icon="growth"/>
  <text v-if="error" class="error">{{error}}</text>
  <button v-if="me?.permissionCodes.includes('STUDENT_TRANSFER_VIEW')&&!created&&!editing" class="secondary refresh" :disabled="busy" @tap="openTransferRecords">转学申请记录</button>
  <view v-if="busy" class="empty">正在处理，请稍候</view>
  <view v-if="created" class="section success-card">
   <view class="success-symbol"><PrototypeIcon name="check" :size="64"/></view><text class="success-title">{{created.studentName}}，欢迎加入</text><text class="meta">成长账号已创建，家庭陪伴已开启</text>
   <view class="credentials"><text class="field-label">成长小主人账号</text><text selectable class="credential">{{created.studentAccount}}</text><text class="field-label">初始登录码</text><text selectable class="login-code">{{created.initialLoginCode}}</text></view>
   <text class="meta">请妥善记录登录码，离开后不再展示。你已成为主监护人，孩子已加入所选家庭。</text><button class="primary" @tap="finish">已记录，返回孩子列表</button>
  </view>
  <template v-else-if="editing">
   <view class="section"><view class="section-heading"><text class="section-title">基本信息</text></view>
    <text class="field-label">所属家庭<text class="required">*</text></text><picker :range="families" range-key="name" :value="familyIndex" :disabled="busy" @change="chooseFamily"><view class="picker-field"><text>{{family?.name||'暂无可进入的家庭'}}</text><text>⌄</text></view></picker>
    <text class="field-label">孩子姓名<text class="required">*</text></text><input v-model="studentName" maxlength="64" placeholder="填写孩子姓名" :disabled="busy"/>
    <text class="field-label">年级编码<text class="small">（选填）</text></text><input v-model="gradeCode" maxlength="64" placeholder="可暂不填写" :disabled="busy"/>
   </view>
   <view class="section"><text class="small">添加后生成成长账号和登录码，同时建立主监护关系。</text></view>
   <view class="bottom-actions"><view class="action-grid"><button class="secondary" :disabled="busy" @tap="editing=false">取消</button><button class="primary" :disabled="busy||!family||!studentName.trim()" :loading="busy" @tap="create">添加孩子</button></view></view>
  </template>
  <template v-else-if="detail">
   <view class="section"><view class="profile"><view class="person"><!-- 通用默认孩子插画，并非真实照片 --><image src="/static/pixel/default-child-avatar.png" mode="aspectFill" style="width:100%;height:100%;border-radius:50%"/></view><view class="person-copy"><text class="name">{{detail.studentName}}</text><text class="meta">成长小主人</text></view><text class="badge" :class="{'REJECTED':detail.status!=='ENABLED'}">{{detail.status==='ENABLED'?'正常':'停用'}}</text></view></view>
   <view class="section"><text class="section-title">档案信息</text><view class="detail-line"><text>年级编码</text><text>{{detail.gradeCode||'未填写'}}</text></view><view class="detail-line"><text>创建时间</text><text>{{detail.createdAt.replace('T',' ').slice(0,19)}}</text></view></view>
   <view v-if="resetCredential" class="section"><text class="section-title">新登录码</text><text selectable class="credential">{{resetCredential.studentAccount}}</text><text selectable class="login-code">{{resetCredential.loginCode}}</text><text class="meta">请妥善记录，离开后不再展示。原登录码已失效。</text></view><button v-if="canReset&&detail.status==='ENABLED'" class="primary" :disabled="busy" @tap="resetCode">重置登录码</button><button v-if="canRequestTransfer&&detail.status==='ENABLED'" class="secondary refresh" :disabled="busy" @tap="openTransfer">转学申请</button><button class="secondary refresh" :disabled="busy" @tap="resetCredential=null;detail=null">返回孩子列表</button>
  </template>
  <template v-else>
   <view class="section"><view class="section-heading"><text class="section-title">我的孩子</text><text class="small">共 {{total}} 位</text></view>
    <view v-for="child in children" :key="child.id" class="row child-row" @tap="openDetail(child)"><view class="person"><!-- 通用默认孩子插画，并非真实照片 --><image src="/static/pixel/default-child-avatar.png" mode="aspectFill" style="width:100%;height:100%;border-radius:50%"/></view><view class="person-copy"><view class="row-top"><text class="name">{{child.studentName}}</text><text class="badge" :class="{'REJECTED':child.status!=='ENABLED'}">{{child.status==='ENABLED'?'正常':'停用'}}</text></view><text class="meta">{{child.gradeCode||'年级未填写'}}</text></view><PrototypeIcon name="arrow" tone="muted" :size="28"/></view>
    <view v-if="ready&&!canRead" class="empty"><PrototypeIcon name="shield" tone="muted" :size="64"/><text>当前账号没有孩子档案查看权限</text></view>
    <view v-else-if="ready&&!children.length" class="empty"><PrototypeIcon name="growth" tone="muted" :size="64"/><text>暂无孩子</text></view>
    <view v-if="total>20" class="paging"><button :disabled="busy||page<=1" @tap="load(page-1)">上一页</button><text>第 {{page}} 页</text><button :disabled="busy||page*20>=total" @tap="load(page+1)">下一页</button></view>
   </view>
   <button v-if="canCreate" class="primary" :disabled="busy" @tap="begin">＋ 添加孩子</button><button class="secondary refresh" :disabled="busy" @tap="load(1)">刷新列表</button>
  </template>
 </view>
</template>
<script setup lang="ts">
import PrototypePageHeader from "@/components/PrototypePageHeader.vue";
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {computed,ref} from 'vue';
import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import {ApiError} from '@/api/http';
import {getParentSession} from '@/session/parent-session';
import {parentChildrenApi,canManageChildren,type ChildIdentity,type Child,type CreatedChild} from '@/api/parent-children';
import type {MyOrganization} from '@/api/organization-onboarding';
const me=ref<ChildIdentity|null>(null),families=ref<MyOrganization[]>([]),familyIndex=ref(0),children=ref<Child[]>([]),detail=ref<Child|null>(null),created=ref<CreatedChild|null>(null),editing=ref(false),busy=ref(false),ready=ref(false),error=ref(''),studentName=ref(''),gradeCode=ref(''),page=ref(1),total=ref(0);
const family=computed(()=>families.value[familyIndex.value]),canCreate=computed(()=>!!me.value&&canManageChildren(me.value,'STUDENT_CREATE')),canRead=computed(()=>!!me.value&&canManageChildren(me.value,'STUDENT_READ'));
const resetCredential=ref<{studentAccount:string;loginCode:string}|null>(null);
const canReset=computed(()=>!!me.value&&canManageChildren(me.value,'STUDENT_LOGIN_CODE_RESET'));
const canRequestTransfer=computed(()=>!!me.value&&canManageChildren(me.value,'STUDENT_TRANSFER_REQUEST_CREATE'));
function openTransferRecords(){if(!busy.value)uni.navigateTo({url:'/pages/transfer-records/transfer-records'});}
function openTransfer(){if(busy.value||!canRequestTransfer.value||!detail.value)return;const selected=uni.getStorageSync('lingdong.selected-organization');const token=getParentSession()?.accessToken;if(!token||selected?.accessToken!==token||selected?.typeCode!=='FAMILY'){error.value='请先切换到对应家庭';return;}uni.navigateTo({url:`/pages/transfer-proposal/transfer-proposal?studentId=${encodeURIComponent(detail.value.id)}&familyId=${encodeURIComponent(selected.organizationId)}`});}
let epoch=0;
function current(n:number,token:string){return n===epoch&&getParentSession()?.accessToken===token;}
function clear(){resetCredential.value=null;me.value=null;families.value=[];children.value=[];detail.value=null;created.value=null;editing.value=false;studentName.value='';gradeCode.value='';total.value=0;ready.value=false;}
async function access(n:number,token:string,permission?:string){const identity=await parentChildrenApi.me(token);if(!current(n,token))return false;if(!identity.parentProfile||identity.clientType!=='MINIAPP'||identity.userType==='STUDENT')throw new Error('请使用当前成长伙伴账号进入');if(permission&&!canManageChildren(identity,permission))throw new Error('当前账号没有操作权限');me.value=identity;return true;}
async function load(target=1){if(busy.value)return;const n=++epoch,token=getParentSession()?.accessToken;clear();busy.value=true;error.value='';try{if(!token)throw new Error('请先登录成长伙伴账号');if(!await access(n,token))return;if(canCreate.value){const rows=await parentChildrenApi.families(token);if(!current(n,token))return;families.value=rows;const selected=uni.getStorageSync('lingdong.selected-organization');const index=selected?.userId===me.value?.userId&&selected?.accessToken===token?rows.findIndex(x=>x.id===selected.organizationId):-1;familyIndex.value=index>=0?index:0;}if(canRead.value){const result=await parentChildrenApi.list(token,target);if(!current(n,token))return;children.value=result.items;page.value=result.page;total.value=result.total;}ready.value=true;}catch(e){if(n===epoch&&(!token||getParentSession()?.accessToken===token)){clear();error.value=e instanceof Error?e.message:'加载失败';}}finally{if(n===epoch)busy.value=false;}}
function begin(){if(busy.value||!canCreate.value)return;if(!family.value){error.value='请先创建或加入家庭，再添加孩子';return;}studentName.value='';gradeCode.value='';editing.value=true;}
function chooseFamily(e:Event){if(!busy.value)familyIndex.value=Number((e as unknown as {detail:{value:string}}).detail.value);}
async function create(){if(busy.value||!canCreate.value||!family.value||!studentName.value.trim())return;const token=getParentSession()?.accessToken;if(!token)return;const n=++epoch,id=family.value.id;busy.value=true;error.value='';try{if(!await access(n,token,'STUDENT_CREATE'))return;const rows=await parentChildrenApi.families(token);if(!current(n,token))return;if(!rows.some(row=>row.id===id))throw new Error('家庭成员身份已变化，请返回刷新');const result=await parentChildrenApi.create(token,{studentName:studentName.value.trim(),...(gradeCode.value.trim()?{gradeCode:gradeCode.value.trim()}:{}),familyOrganizationId:id});if(!current(n,token))return;created.value=result;editing.value=false;studentName.value='';gradeCode.value='';}catch(e){if(current(n,token)){if(e instanceof ApiError&&[401,403,404].includes(e.statusCode))clear();else editing.value=false;error.value=e instanceof Error?e.message:'未能确认添加结果，请刷新列表核对后再操作';}}finally{if(n===epoch)busy.value=false;}}
async function openDetail(child:Child){if(busy.value||!canRead.value)return;const token=getParentSession()?.accessToken;if(!token)return;const n=++epoch;busy.value=true;error.value='';try{if(!await access(n,token,'STUDENT_READ'))return;const result=await parentChildrenApi.detail(token,child.id);if(current(n,token))detail.value=result;}catch(e){if(current(n,token)){if(e instanceof ApiError&&[401,403,404].includes(e.statusCode))clear();error.value=e instanceof Error?e.message:'加载失败';}}finally{if(n===epoch)busy.value=false;}}
async function resetCode(){const child=detail.value,token=getParentSession()?.accessToken;if(!child||!token||busy.value||!canReset.value)return;const before=epoch;busy.value=true;const confirm=await uni.showModal({title:'重置登录码',content:'重置后原登录码失效，孩子已登录的会话将退出。是否继续？',confirmText:'重置'});if(!confirm.confirm||!current(before,token)){if(before===epoch)busy.value=false;return;}const n=++epoch;error.value='';resetCredential.value=null;try{if(!await access(n,token,'STUDENT_LOGIN_CODE_RESET'))return;const result=await parentChildrenApi.resetLoginCode(token,child.id);if(current(n,token))resetCredential.value=result;}catch(e){if(current(n,token))error.value=e instanceof Error?e.message:'重置结果未确认，请勿重复操作';}finally{if(n===epoch)busy.value=false;}}
function finish(){created.value=null;void load(1);}
function leave(){++epoch;clear();busy.value=false;error.value='';}
onShow(()=>{void load(1);});onHide(leave);onUnload(leave);
</script>
<style lang="scss" scoped>
@import "@/styles/secondary-prototype.scss";
.profile{display:flex;align-items:center;gap:20rpx;}.success-card{text-align:center;}.success-symbol{width:108rpx;height:108rpx;margin:10rpx auto 22rpx;background:#edf4ee;border-radius:50%;display:flex;align-items:center;justify-content:center;}.success-title{display:block;font-size:32rpx;font-weight:650;}.credentials{margin:28rpx 0;padding:24rpx;background:#f3f7f2;border-radius:18rpx;}.credential{display:block;overflow-wrap:anywhere;font-size:30rpx;font-weight:650;color:#184f40;}.login-code{font-size:48rpx;letter-spacing:12rpx;color:#184f40;font-weight:700;}.success-card button{margin-top:28rpx;}.refresh{margin-top:18rpx;}.child-row{cursor:pointer;}
</style>
