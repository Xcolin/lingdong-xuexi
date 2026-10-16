<template>
 <view class="ld-page access-page registration-page">
  <text class="ld-heading-title">注册成长伙伴账号</text>
  <view class="registration-steps"><view v-for="(label,index) in ['填写信息','验证身份','确认注册']" :key="label" :class="['registration-step',{active:step===index+1,complete:step>index+1}]"><text class="step-number">{{index+1}}</text><text>{{label}}</text></view></view>
  <view class="access-card">
   <view v-if="step===1">
    <view class="registration-field"><text class="field-label">姓名</text><input v-model="displayName" placeholder="请输入真实姓名" :disabled="busy"/></view>
    <view class="registration-field"><text class="field-label">账号</text><input v-model="username" placeholder="设置登录账号" :disabled="busy"/></view>
    <view class="registration-field"><text class="field-label">设置密码</text><input v-model="password" password placeholder="8至20位字母和数字" :disabled="busy"/></view>
    <view class="registration-field"><text class="field-label">确认密码</text><input v-model="confirmation" password placeholder="再次输入密码" :disabled="busy"/></view>
    <button class="access-primary" :disabled="busy||!context" @tap="nextStep">下一步</button>
    <view class="registration-footer"><text>已有账号？</text><text class="registration-link" @tap="openLogin">返回登录</text></view>
   </view>
   <view v-else-if="step===2">
    <view class="access-tabs"><button :class="{active:channel==='EMAIL'}" :disabled="busy" @tap="selectChannel('EMAIL')">邮箱验证</button><button v-if="context?.smsEnabled" :class="{active:channel==='SMS'}" :disabled="busy" @tap="selectChannel('SMS')">手机验证</button></view>
    <text class="access-tip">验证码将在创建账号时由服务核验，请使用本人联系方式。</text>
    <view class="registration-field"><text class="field-label">{{channel==='EMAIL'?'邮箱':'手机号'}}</text><input v-model="destination" :placeholder="channel==='EMAIL'?'请输入邮箱地址':'请输入手机号'" :disabled="busy"/></view>
    <view class="registration-field"><text class="field-label">验证码</text><view class="registration-code-row"><input v-model="code" type="number" maxlength="6" placeholder="6位验证码" :disabled="busy"/><button class="registration-code-button" :disabled="busy||remaining>0||!context||(channel==='EMAIL'&&!context.emailEnabled)" @tap="send">{{remaining>0?remaining+'秒':'发送验证码'}}</button></view></view>
    <button class="access-primary" :disabled="busy||!context" @tap="nextStep">下一步</button><button class="registration-back" :disabled="busy" @tap="back">上一步</button>
   </view>
   <view v-else>
    <view class="review-row"><text>姓名</text><text>{{displayName}}</text></view><view class="review-row"><text>账号</text><text>{{username}}</text></view><view class="review-row"><text>{{channel==='EMAIL'?'邮箱':'手机号'}}</text><text>{{destination}}</text></view>
    <text class="verification-pending">提交后核验验证码并创建账号。</text><LoginConsent v-model="accepted"/>
    <button class="access-primary" :disabled="busy||!context||!accepted" :loading="busy" @tap="submit">确认注册</button><button class="registration-back" :disabled="busy" @tap="back">上一步</button>
   </view>
   <text v-if="error" class="ld-error-text registration-error">{{error}}</text>
  </view>
 </view>
</template>
<script setup lang="ts">
import {accountError,contactError,registrationPayload,advanceRegistrationStep} from './registration-flow';
import LoginConsent from '@/components/LoginConsent.vue';
import {ref,watch} from 'vue';
import {onLoad,onUnload} from '@dcloudio/uni-app';
import {request} from '@/api/http';
import type {StudentSession} from '@/api/auth';
import {saveRegistrationSession} from '@/session/registration-session';
import {getOrCreateOrganizationDeviceId,getOrganizationDeviceName} from '@/session/organization-session';
const context=ref<{emailEnabled:boolean;smsEnabled:boolean;agreementVersion:string}|null>(null),login=ref(false),channel=ref<'EMAIL'|'SMS'>('EMAIL'),destination=ref(''),code=ref(''),displayName=ref(''),username=ref(''),password=ref(''),confirmation=ref(''),accepted=ref(false),busy=ref(false),error=ref(''),remaining=ref(0);
watch(destination,()=>{code.value='';error.value='';});
function selectChannel(value:'EMAIL'|'SMS'){if(channel.value===value)return;channel.value=value;destination.value='';code.value='';error.value='';}
const step=ref(1);
function fields(){return {username:username.value,displayName:displayName.value,password:password.value,confirmation:confirmation.value,channel:channel.value,destination:destination.value,code:code.value,accepted:accepted.value};}
function openLogin(){if(!busy.value)uni.navigateTo({url:'/pages/unified-login/unified-login'});}
function back(){if(busy.value)return;error.value='';step.value=Math.max(1,step.value-1);}
function nextStep(){const next=advanceRegistrationStep(step.value,fields(),busy.value,!!context.value);error.value=next.error;step.value=next.step;}
let timer:ReturnType<typeof setInterval>|undefined;
onLoad(async()=>{try{context.value=await request('/public/registration/context');}catch(e){error.value=e instanceof Error?e.message:'加载失败';}});
onUnload(()=>{if(timer)clearInterval(timer);});
async function readAgreement(){try{const agreement=await request<{version:string;content:string}>('/public/registration/agreement');await uni.showModal({title:'用户协议 '+agreement.version,content:agreement.content,showCancel:false});}catch(e){error.value=e instanceof Error?e.message:'协议加载失败';}}
async function send(){if(busy.value||remaining.value||!context.value)return;if(channel.value==='EMAIL'?!context.value.emailEnabled:!context.value.smsEnabled)return;busy.value=true;error.value='';try{const result=await request<{retryAfterSeconds:number}>('/auth/registration-codes',{method:'POST',timeoutMs:channel.value==='SMS'?40000:15000,data:{channel:channel.value,destination:destination.value.trim(),clientType:'MINIAPP'}});remaining.value=result.retryAfterSeconds;if(timer)clearInterval(timer);timer=setInterval(()=>{remaining.value=Math.max(0,remaining.value-1);if(!remaining.value&&timer)clearInterval(timer);},1000);}catch(e){error.value=e instanceof Error?e.message:'发送失败';}finally{busy.value=false;}}
async function submit(){if(busy.value||!context.value)return;error.value=accountError(fields())||contactError(fields())||(!accepted.value?'请接受用户协议':'');if(error.value)return;busy.value=true;error.value='';try{const result=await request<{session:StudentSession}>(login.value?'/auth/registration-sessions/password':'/auth/registrations',{method:'POST',data:registrationPayload(fields(),context.value.agreementVersion,getOrCreateOrganizationDeviceId(),getOrganizationDeviceName())});saveRegistrationSession(result.session);await uni.reLaunch({url:'/pages/my-organizations/my-organizations?identity=personal'});}catch(e){error.value=e instanceof Error?e.message:'操作失败';}finally{busy.value=false;}}
</script>

<style lang="scss" scoped>

@import "@/styles/account-access.scss";
.registration-page {background:#F6F8F5;color:#15251f;padding-inline:44rpx;}
.registration-steps {display:flex;align-items:flex-start;justify-content:center;margin:36rpx 0 40rpx;gap:20rpx;}
.registration-step {display:flex;flex-direction:column;align-items:center;gap:14rpx;color:#949b98;font-size:24rpx;}
.registration-step.active,.registration-step.complete {color:#184F40;}
.step-number {display:flex;align-items:center;justify-content:center;width:56rpx;height:56rpx;border-radius:50%;background:#e4e7e5;color:#737b76;font-size:28rpx;font-weight:600;}
.active .step-number,.complete .step-number {background:#184F40;color:#fff;}
.step-line {width:170rpx;height:2rpx;background:#c9d1cc;margin-top:28rpx;}
.registration-field {margin:28rpx 0;}
.field-label {display:block;color:#15251f;font-size:28rpx;font-weight:600;margin-bottom:14rpx;}
.registration-page .registration-field input {box-sizing:border-box;width:100%;height:96rpx;margin:0;background:#fcfdfc;padding:0 24rpx;border:1rpx solid #d8dcdb;border-radius:16rpx;}
.registration-code-row {display:flex;align-items:center;gap:20rpx;}
.registration-code-row input {flex:1;min-width:0;}
.registration-code-button {flex:0 0 232rpx;height:96rpx;line-height:96rpx;margin:0;padding:0;background:#e8eeea;color:#184F40;border:1rpx solid #d8e2dc;border-radius:16rpx;font-size:26rpx;font-weight:600;}
.registration-code-button::after {border:0;}
.registration-page .access-primary {height:104rpx;line-height:104rpx;margin-top:38rpx;background:#184F40;color:white;border-radius:18rpx;font-size:32rpx;}
.registration-footer {display:flex;justify-content:center;gap:12rpx;margin-top:32rpx;color:#59605c;font-size:28rpx;}
.registration-link {color:#184F40;font-weight:600;}
.registration-agreement {display:flex;align-items:center;gap:8rpx;color:#747b77;font-size:24rpx;margin-top:24rpx;flex-wrap:wrap;}
.registration-back {height:96rpx;line-height:96rpx;margin-top:20rpx;border:1rpx solid #bac8c0;color:#184F40;border-radius:16rpx;background:transparent;}
.registration-back::after {border:0;}
.verification-pending {display:block;color:#687b70;font-size:24rpx;text-align:center;margin-bottom:20rpx;}
.registration-error {display:block;margin-top:24rpx;}

.registration-page{background:#f5f9f3;padding:32rpx 28rpx 60rpx;}.ld-heading-title{font-size:34rpx;text-align:center;display:block;margin-bottom:35rpx;}.registration-steps{padding:0 20rpx;margin-bottom:30rpx;}.step-number{width:42rpx;height:42rpx;font-size:24rpx;}.registration-step{font-size:23rpx;}.access-card{border:0;border-radius:26rpx;background:white;padding:26rpx;box-shadow:none;}.registration-field{display:flex;align-items:center;gap:20rpx;margin-top:20rpx;}.field-label{width:130rpx;flex-shrink:0;font-size:26rpx;}.registration-field>input,.registration-code-row{flex:1;min-width:0;}.registration-field input{background:#fafcfb;border:1rpx solid #dfe7e1;border-radius:16rpx;height:84rpx;padding:0 20rpx;box-sizing:border-box;}.registration-code-button{padding:0 10rpx;font-size:22rpx;}.access-primary{height:96rpx;line-height:96rpx;border-radius:18rpx;background:#14664c;}.access-tip{font-size:23rpx;line-height:1.6;}
.registration-steps{justify-content:space-between;gap:10rpx;}.review-row{display:flex;justify-content:space-between;gap:24rpx;padding:24rpx 0;border-bottom:1rpx solid #edf0e8;font-size:27rpx;}.review-row text:last-child{overflow-wrap:anywhere;text-align:right;}
</style>
