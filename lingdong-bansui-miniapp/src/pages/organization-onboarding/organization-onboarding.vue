<template><view class="onboarding-page ld-page"><view class="onboarding-hero"><view class="hero-icon"><PrototypeIcon :name="family?'home':'school'" tone="white" :size="48"/></view><view><text class="hero-title">{{family?'创建家庭':'学校 / 培训机构入驻'}}</text><text class="hero-caption">填写信息，开启新的陪伴空间</text></view></view><view class="onboarding-content"><view class="step-track"><view class="active"><text>1</text><text>填写信息</text></view><view class="step-line"/><view><text>2</text><text>{{family?'创建完成':'提交审核'}}</text></view></view><view v-if="loading" class="loading-state">正在加载…</view><template v-else-if="context"><view class="white-group form-group"><text class="form-section">{{family?'家庭基本信息':'机构信息'}}</text><text class="form-label">{{family?'家庭名称':'学校 / 培训机构名称'}}<text class="required"> *</text></text><input v-model="name" :disabled="busy" maxlength="100" :placeholder="family?'为家庭起一个名字':'填写组织全称'"/><template v-if="!family"><text class="form-label">组织类型<text class="required"> *</text></text><picker :range="['学校','培训机构']" :value="typeIndex" :disabled="busy" @change="typeIndex=Number($event.detail.value)"><view class="picker-row"><text>{{typeIndex?'培训机构':'学校'}}</text><text>⌄</text></view></picker></template><text class="form-label">所属区域<text class="required"> *</text></text><picker :range="regionNames" :value="regionIndex" :disabled="busy" @change="regionIndex=Number($event.detail.value)"><view class="picker-row"><text>{{regions[regionIndex]?.name||'请选择所属区域'}}</text><text>⌄</text></view></picker></view>
 <view v-if="!family" class="white-group form-group"><text class="form-section">联系信息</text><text class="form-label">联系人姓名<text class="required"> *</text></text><input v-model="contactName" :disabled="busy" maxlength="100" placeholder="填写联系人姓名"/><view class="verified-contact"><PrototypeIcon :name="context.verifiedEmail?'mail':'phone'" :size="36"/><view><text>{{context.verifiedEmail?'已验证邮箱':'联系手机号'}}</text><text class="row-caption">{{context.verifiedEmail?context.email:context.mobile||'未绑定'}}</text></view><PrototypeIcon v-if="context.verifiedEmail||context.verifiedMobile" name="check" :size="32"/></view><view v-if="!context.verifiedEmail&&context.mobile&&!context.verifiedMobile"><view class="verification-row"><input v-model="smsCode" type="number" maxlength="6" placeholder="手机验证码" :disabled="busy||verifying"/><button :disabled="verifying||retryAt>Date.now()" @tap="sendCode">发送验证码</button></view><button class="outline-action" :disabled="verifying||!smsCode.trim()" :loading="verifying" @tap="verifyMobile">验证手机号</button></view></view>
 <view v-if="!family" class="process-note"><PrototypeIcon name="shield" :size="34"/><text>审核通过后创建组织；入驻不等于认证。</text></view><view class="bottom-actions"><button class="primary-action" :loading="busy" :disabled="busy||(!family&&!context.verifiedMobile&&!context.verifiedEmail)" @tap="submit">{{family?'创建家庭':'提交入驻申请'}}</button></view></template><text v-if="error" class="error-text">{{error}}</text></view></view></template>
<script setup lang="ts">
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { onboardingApi, type OnboardingContext, type Region, type OrganizationApplication, type ApplicationInput } from '@/api/organization-onboarding';
import { onboardingIdentity, resolveOnboardingSession, onboardingUrl, type OnboardingIdentity } from '@/session/onboarding-session';
const identity=ref<OnboardingIdentity|null>(null), family=ref(true), name=ref(''), contactName=ref(''), typeIndex=ref(0), regionIndex=ref(-1), regions=ref<Region[]>([]), context=ref<OnboardingContext|null>(null), editing=ref<OrganizationApplication|null>(null), loading=ref(true), busy=ref(false), error=ref('');
const smsCode=ref(''), verifying=ref(false), retryAt=ref(0);
const regionNames=computed(()=>regions.value.map(x=>x.name));
const idempotencyKey=`miniapp-${Date.now()}-${Math.random().toString(36).slice(2)}`;
onLoad(async options=>{
  identity.value=onboardingIdentity(options?.identity); family.value=options?.kind!=='institution';
  const session=await resolveOnboardingSession(identity.value);
  if(!session){ loading.value=false; error.value='请从当前账号的入驻进度入口进入'; return; }
  try{
    context.value=await onboardingApi.context(session.accessToken);
    regions.value=await onboardingApi.regions(session.accessToken);
    if(options?.applicationId){
      const item=await onboardingApi.application(session.accessToken,options.applicationId);
      if(!['REJECTED','WITHDRAWN'].includes(item.status)) throw new Error('该申请当前不可修改');
      editing.value=item; name.value=item.name; contactName.value=item.contactName; typeIndex.value=item.typeCode==='SCHOOL'?0:1; regionIndex.value=regions.value.findIndex(x=>x.id===item.regionId);
    }
  }catch(e){error.value=e instanceof Error?e.message:'加载失败';}finally{loading.value=false;}
});
async function sendCode(){
  const session=await resolveOnboardingSession(identity.value); if(!session || verifying.value || retryAt.value>Date.now())return;
  verifying.value=true;error.value='';
  try{const result=await onboardingApi.mobileCode(session.accessToken);retryAt.value=Date.now()+result.retryAfterSeconds*1000;setTimeout(()=>{retryAt.value=0;},result.retryAfterSeconds*1000);}
  catch(e){error.value=e instanceof Error?e.message:'验证码发送失败';}finally{verifying.value=false;}
}
async function verifyMobile(){
  const session=await resolveOnboardingSession(identity.value);if(!session||verifying.value)return;
  verifying.value=true;error.value='';
  try{await onboardingApi.verifyMobile(session.accessToken,smsCode.value.trim());context.value=await onboardingApi.context(session.accessToken);smsCode.value='';}
  catch(e){error.value=e instanceof Error?e.message:'验证失败';}finally{verifying.value=false;}
}
async function submit(){
  if(busy.value || !context.value || !identity.value) return;
  const session=await resolveOnboardingSession(identity.value); if(!session)return;
  if(!name.value.trim()){error.value='请填写名称';return;}
  if(!regions.value[regionIndex.value]){error.value='请选择所属区域';return;}
  if(!family.value && (!regions.value[regionIndex.value] || !contactName.value.trim())){error.value='请选择所属区域并填写联系人';return;}
  busy.value=true;error.value='';
  try{
    if(family.value){await onboardingApi.family(session.accessToken,name.value.trim(),idempotencyKey,regions.value[regionIndex.value].id);await uni.redirectTo({url:onboardingUrl('my-organizations',identity.value)});}
    else{
      const data:ApplicationInput={typeCode:typeIndex.value?'TRAINING_INSTITUTION':'SCHOOL',name:name.value.trim(),regionId:regions.value[regionIndex.value].id,contactName:contactName.value.trim()};
      const result=editing.value?await onboardingApi.resubmit(session.accessToken,editing.value,data):await onboardingApi.submit(session.accessToken,data);
      await uni.redirectTo({url:onboardingUrl('organization-application',identity.value,`&id=${encodeURIComponent(result.id)}`)});
    }
  }catch(e){error.value=e instanceof Error?e.message:'提交失败，请重试';}finally{busy.value=false;}
}
</script>

<style lang="scss" scoped>
@import '@/styles/onboarding-prototype.scss';
.onboarding-page{background:#f5f9f3;padding-bottom:calc(184rpx + env(safe-area-inset-bottom));}.onboarding-hero{background:transparent;color:#183d2d;min-height:100rpx;padding:24rpx 30rpx;margin:0;box-shadow:none;border-radius:0;justify-content:center;}.hero-icon,.hero-caption{display:none;}.hero-title{font-size:34rpx;color:#183d2d;}.hero-tool{position:absolute;right:28rpx;background:#176c51;border-radius:50%;}.onboarding-content{padding:20rpx 28rpx;}.white-group{background:#fff;border:0;border-radius:26rpx;box-shadow:none;margin-bottom:24rpx;}.form-group{padding:28rpx;}.form-section{font-size:28rpx;margin-bottom:22rpx;}.form-label{font-size:26rpx;margin:22rpx 0 12rpx;}.form-group input,.picker-row{background:#fafcfb;border:1rpx solid #e0e8e2;border-radius:16rpx;min-height:82rpx;padding:16rpx 20rpx;font-size:26rpx;box-sizing:border-box;}.primary-action{background:linear-gradient(110deg,#176b53,#0a5b40);border-radius:18rpx;font-size:30rpx;}.outline-action,.outline-small{border:1rpx solid #247b5b;color:#1a6c50;border-radius:16rpx;}.organization-row,.application-row{padding:24rpx;gap:20rpx;}.organization-icon{background:#e9f5ed;border-radius:16rpx;}.row-title{font-size:28rpx;}.row-caption{font-size:23rpx;}.row-tags text{background:#e8f2ec;color:#32705b;}.step-track{margin:0 0 28rpx;font-size:23rpx;}.timeline-card{padding:30rpx;}.timeline-step{padding:20rpx 0;}.timeline-dot{width:42rpx;height:42rpx;border-radius:50%;}.timeline-step.complete .timeline-dot{background:#167b54;color:white;}.rejection-card{background:#fff0ef;border-radius:24rpx;color:#974d49;padding:28rpx;}.process-note{background:#fff4df;border-radius:20rpx;padding:22rpx;}.section-title{font-size:29rpx;}
</style>
