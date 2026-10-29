<template>
 <view class="ld-page access-page compact-login" :class="{'wechat-landing':mode==='WECHAT'&&!bindingTicket}">
  <AccessBrand compact/><view class="login-heading"><text class="ld-heading-title">一起陪伴，让成长发生</text><text class="brand-caption">记录每一步成长，连接每一份陪伴</text></view>
  <view class="login-panel"><view v-if="mode==='WECHAT'&&!bindingTicket" class="panel-heading"><text class="panel-title">账号登录</text><view class="panel-rule"/></view>
  <button v-if="mode==='WECHAT'&&!bindingTicket" class="access-link student-entry hero-student" @tap="openStudent"><view class="entry-copy"><text class="entry-title">成长小主人登录</text><text class="entry-subtitle">专属成长空间 · 登录码 / 扫码</text></view><text class="entry-arrow">›</text></button>
  <view v-if="bindingTicket&&!bindingChoice" class="binding-choice"><text class="binding-title">首次微信关联账号</text><text class="access-tip">关联已有成长伙伴账号，保留原来的成长记录。</text><button class="binding-option" :disabled="busy" @tap="chooseBinding('EMAIL')"><text>邮箱验证码绑定</text><text>使用已绑定邮箱验证身份 ›</text></button><button class="binding-option" :disabled="busy" @tap="chooseBinding('PASSWORD')"><text>已有账号密码绑定</text><text>使用账号与密码验证身份 ›</text></button><button class="access-link" :disabled="busy" @tap="restartWechat">重新微信授权 / 切换账号</button></view>
  <view v-else class="access-card"><view v-if="bindingTicket" class="binding-heading"><button :disabled="busy" @tap="bindingChoice=''">‹ 返回选择</button><text>{{mode==='PASSWORD'?'已有账号密码绑定':'邮箱验证码绑定'}}</text></view>
   <!-- #ifndef MP-WEIXIN -->
   <view class="access-tabs"><button :class="{active:mode==='PHONE'||mode==='EMAIL'}" @tap="selectMode('PHONE')">验证码登录</button><button :class="{active:mode==='PASSWORD'}" @tap="selectMode('PASSWORD')">密码登录</button></view>
   <!-- #endif -->
   <view v-if="!bindingTicket&&(mode==='PHONE'||mode==='EMAIL')" class="contact-switch"><button :class="{selected:mode==='PHONE'}" @tap="selectMode('PHONE')">手机验证</button><button :class="{selected:mode==='EMAIL'}" @tap="selectMode('EMAIL')">邮箱验证</button></view>

   <text v-if="mode!=='WECHAT'" class="access-tip">{{bindingTicket?'验证邮箱以绑定已有账号；新邮箱需明确注册。无邮箱的已有账号可使用账号密码绑定。':mode==='PHONE'?'使用已绑定的手机号登录，验证码不会创建新账号。':mode==='EMAIL'?'使用已绑定的邮箱登录，请留意收件箱和垃圾邮件。':'使用账号和密码登录，初始密码首次使用需修改。'}}</text>
   <template v-if="mode!=='WECHAT'"><text class="ld-field-label">{{mode==='PHONE'?'手机号':mode==='EMAIL'?'邮箱':'账号'}}</text><input :disabled="!!resumeUserId" v-model="destination" :placeholder="mode==='PHONE'?'请输入手机号':mode==='EMAIL'?'请输入邮箱地址':'请输入账号'" maxlength="120"/>
   <text v-if="mode==='PASSWORD'" class="ld-field-label">密码</text><input v-if="mode==='PASSWORD'" v-model="password" password placeholder="请输入密码" maxlength="64"/>
   <template v-else><text class="ld-field-label">验证码</text><view class="code-row"><input v-model="code" type="number" maxlength="6" placeholder="6位验证码"/><button :disabled="busy||remaining>0" @tap="send">{{remaining?remaining+'秒':'获取验证码'}}</button></view></template>
   </template>
   <template v-if="bindingTicket"><button class="access-link" @tap="restartWechat">重新微信授权 / 切换账号</button><button class="access-link"  :disabled="busy" @tap="chooseBinding(mode==='PASSWORD'?'EMAIL':'PASSWORD')">{{mode==='PASSWORD'?'返回邮箱验证':'已有账号没有邮箱？账号密码绑定'}}</button><template v-if="mode==='PASSWORD'"><input v-model="bindingEmail" placeholder="为已有账号绑定邮箱（已绑定可留空）"/><view class="code-row"><input v-model="bindingCode" placeholder="邮箱验证码" maxlength="6"/><button :disabled="busy||remaining>0" @tap="send">获取验证码</button></view></template><template v-if="registering"><input v-model="displayName" placeholder="姓名"/><input v-model="username" placeholder="设置账号"/><input v-model="registrationPassword" password placeholder="设置密码"/><input v-model="confirmation" password placeholder="确认密码"/><view @tap="accepted=!accepted">{{accepted?'☑':'□'}} 我同意用户协议 <text @tap.stop="readAgreement">查看协议</text></view></template></template><text v-if="error" class="ld-error-text">{{error}}</text><button class="access-primary" :disabled="busy" :loading="busy" @tap="submit"><template v-if="mode==='WECHAT'"><view class="entry-copy"><text class="entry-title">成长伙伴登录</text><text class="entry-subtitle">陪伴与管理 · 微信一键登录</text></view><text class="entry-arrow">›</text></template><template v-else>{{registering?'同意协议并注册':bindingTicket?'验证并绑定':'登录'}}</template></button>
  </view>
  <view class="login-footer"><button v-if="mode!=='WECHAT'||bindingTicket" class="access-link student-entry" @tap="openStudent"><view class="entry-copy"><text class="entry-title">成长小主人登录</text><text class="entry-subtitle">专属成长空间 · 登录码 / 扫码</text></view><text class="entry-arrow">›</text></button><button v-if="!bindingTicket" class="access-link register-entry" @tap="openRegistration"><view class="entry-copy"><text class="entry-title">注册伙伴账号</text><text class="entry-subtitle">首次使用，创建账号</text></view><text class="entry-arrow">›</text></button></view>
  <view class="login-consent"><checkbox-group @change="loginAgreed=$event.detail.value.includes('agree')"><label class="consent-check"><checkbox value="agree" :checked="loginAgreed" color="#184F40"/></label></checkbox-group><view class="consent-text">我已阅读并同意<text @tap="readAgreement">《用户协议》</text><text @tap="readPrivacy">《隐私条款》</text></view></view></view>
  <view class="login-signature"><view class="signature-line"/><text>陪伴有温度，成长有记录</text><view class="signature-line"/></view>
 </view>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue';
import AccessBrand from '@/components/AccessBrand.vue';
import {onLoad} from '@dcloudio/uni-app';
import {request,ApiError} from '@/api/http';
import {routeRequiredPasswordChange,routeTeacherAccount} from '@/session/account-login-routing';
import type {StudentSession} from '@/api/auth';
import {saveRegistrationSession} from '@/session/registration-session';
import {getOrCreateOrganizationDeviceId,getOrganizationDeviceName} from '@/session/organization-session';
const mode=ref<'PHONE'|'EMAIL'|'PASSWORD'|'WECHAT'>('PHONE'),destination=ref(''),password=ref(''),code=ref(''),busy=ref(false),error=ref(''),remaining=ref(0);
const loginAgreed=ref(false);
function requireConsent(){if(loginAgreed.value)return true;uni.showToast({title:'请先阅读并同意用户协议和隐私条款',icon:'none'});return false;}
async function readPrivacy(){await uni.showModal({title:'隐私条款',content:'登录时使用微信身份信息识别账号；绑定邮箱用于身份验证和账号找回，手机号为选填。学生账号用于关联对应的学习服务。账号、组织关系及操作记录用于提供服务和权限校验。请勿将密码、验证码或学生登录码提供给他人。你可在账号设置中维护联系方式、管理登录设备；涉及学生信息的操作应由监护人或经授权人员完成。',showCancel:false});}
const bindingChoice=ref('');
function chooseBinding(value:'EMAIL'|'PASSWORD'){if(busy.value)return;mode.value=value;bindingChoice.value=value;registering.value=false;error.value='';}
const bindingTicket=ref(''), bindingEmail=ref(''), bindingCode=ref(''), registering=ref(false), accepted=ref(false), displayName=ref(''), confirmation=ref(''), agreementVersion=ref('');
// #ifdef MP-WEIXIN
mode.value='WECHAT';
// #endif
const pendingKey='lingdong.wechat.pending-binding';
const resumeUserId=ref('');
function restartWechat(){bindingChoice.value='';uni.removeStorageSync(pendingKey);resumeUserId.value='';bindingTicket.value='';registering.value=false;mode.value='WECHAT';}
async function rememberPending(accessToken:string){const me=await request<{userId:string;username:string}>('/auth/me',{header:{Authorization:`Bearer ${accessToken}`}});if(resumeUserId.value&&String(me.userId)!==resumeUserId.value){restartWechat();throw new Error('当前账号与待绑定账号不一致，请重新微信授权');}uni.setStorageSync(pendingKey,{userId:String(me.userId),bindingTicket:bindingTicket.value,username:mode.value==='PASSWORD'?destination.value.trim():me.username,email:mode.value==='PASSWORD'?bindingEmail.value.trim():destination.value.trim(),mode:mode.value,createdAt:Date.now()});}
const wechatAvailable=ref(false),username=ref(''),registrationPassword=ref('');
async function readAgreement(){const r=await request<{version:string;content:string}>('/public/registration/agreement');agreementVersion.value=r.version;await uni.showModal({title:'用户协议',content:r.content,showCancel:false});}
onLoad(async(options)=>{if(options?.resume==='1'){const pending=uni.getStorageSync(pendingKey);if(pending&&pending.userId===options.userId&&Date.now()-pending.createdAt<5*60*1000){mode.value=pending.mode;bindingChoice.value=pending.mode;bindingTicket.value=pending.bindingTicket;resumeUserId.value=pending.userId;await Promise.resolve();destination.value=pending.mode==='PASSWORD'?pending.username:pending.email;bindingEmail.value=pending.email;error.value=pending.mode==='PASSWORD'?'请输入修改后的新密码；如需邮箱验证请重新获取验证码':'密码已修改，请重新获取邮箱验证码完成微信绑定';}else{restartWechat();error.value='绑定信息已过期，请重新微信授权';}}else{uni.removeStorageSync(pendingKey);if(options?.mode==='PASSWORD')mode.value='PASSWORD';}try{const ctx=await request<{wechatEnabled:boolean}>('/public/parent-auth-context');wechatAvailable.value=ctx.wechatEnabled;}catch{wechatAvailable.value=false;}});
async function wechat(){if(busy.value)return;busy.value=true;error.value='';try{const temporaryCode=await new Promise<string>((resolve,reject)=>uni.login({provider:'weixin',success:r=>r.code?resolve(r.code):reject(new Error('微信授权未完成')),fail:()=>reject(new Error('微信授权未完成'))}));const r=await request<{bindingRequired:boolean;bindingTicket?:string;session?:StudentSession}>('/auth/account-wechat-sessions',{method:'POST',data:{temporaryCode,deviceId:getOrCreateOrganizationDeviceId(),deviceName:getOrganizationDeviceName()}});if(r.bindingRequired){mode.value='EMAIL';bindingChoice.value='';bindingTicket.value=r.bindingTicket??'';}else if(r.session){saveRegistrationSession(r.session);if(await routeRequiredPasswordChange(r.session.accessToken,'personal'))return;if(await routeTeacherAccount(r.session.accessToken))return;await uni.reLaunch({url:'/pages/my-organizations/my-organizations?identity=personal'});}}catch(e){error.value=e instanceof Error?e.message:'微信登录失败';}finally{busy.value=false;}}
function selectMode(value:'PHONE'|'EMAIL'|'PASSWORD'|'WECHAT'){if(busy.value)return;uni.removeStorageSync(pendingKey);resumeUserId.value='';bindingTicket.value='';mode.value=value;}
let timer:ReturnType<typeof setInterval>|undefined;
watch(destination,()=>{code.value='';registering.value=false;});
watch(bindingEmail,()=>{bindingCode.value='';});
watch(bindingEmail,()=>{bindingCode.value='';});
watch(mode,()=>{destination.value='';password.value='';code.value='';error.value='';});
onBeforeUnmount(()=>{if(timer)clearInterval(timer);});
function openRegistration(){uni.navigateTo({url:'/pages/public-registration/public-registration'});}
function openParent(){uni.navigateTo({url:'/pages/parent-login/parent-login'});}
function openOrganization(){uni.navigateTo({url:'/pages/organization-login/organization-login'});}
function openStudent(){if(!requireConsent())return;uni.navigateTo({url:'/pages/student-login/student-login'});}
async function send(){if(busy.value||remaining.value)return;error.value='';busy.value=true;try{const result=await request<{retryAfterSeconds:number}>(bindingTicket.value?'/auth/account-wechat-email-codes':mode.value==='EMAIL'?'/auth/login-email-codes':'/auth/login-sms-codes',{method:'POST',timeoutMs:40000,data:{email:mode.value==='PASSWORD'?bindingEmail.value.trim():destination.value.trim(),mobile:destination.value.trim(),clientType:'MINIAPP'}});remaining.value=result?.retryAfterSeconds ?? 60;if(timer)clearInterval(timer);timer=setInterval(()=>{remaining.value=Math.max(0,remaining.value-1);if(!remaining.value&&timer)clearInterval(timer);},1000);}catch(e){error.value=e instanceof Error?e.message:'发送失败';}finally{busy.value=false;}}
async function submit(){if(busy.value||!requireConsent())return;if(mode.value==='WECHAT'){if(!wechatAvailable.value){uni.showToast({title:'微信登录暂未启用',icon:'none'});return;}await wechat();return;}error.value='';if(!destination.value.trim()||(mode.value==='PASSWORD'?!password.value:!/^\d{6}$/.test(code.value))){error.value='请填写账号信息与登录凭据';return;}busy.value=true;try{if(registering.value){if(!accepted.value||registrationPassword.value!==confirmation.value)throw new Error('请同意协议并确认密码一致');if(!agreementVersion.value){const ctx=await request<{agreementVersion:string}>('/public/registration/context');agreementVersion.value=ctx.agreementVersion;}}const result=await request<{session:StudentSession;bindingRequired?:boolean;registrationRequired?:boolean}|StudentSession>(registering.value?'/auth/account-wechat-registrations':bindingTicket.value?mode.value==='PASSWORD'?'/auth/account-wechat-bindings':'/auth/account-wechat-email-bindings':mode.value==='PASSWORD'?'/auth/registration-sessions/password':mode.value==='EMAIL'?'/auth/sessions/email':'/auth/sessions/sms',{method:'POST',data:{...(registering.value?{registration:{channel:'EMAIL',destination:destination.value.trim(),code:code.value,username:username.value.trim(),displayName:displayName.value.trim(),password:registrationPassword.value,passwordConfirmation:confirmation.value,agreementAccepted:accepted.value,agreementVersion:agreementVersion.value,clientType:'MINIAPP',deviceId:getOrCreateOrganizationDeviceId(),deviceName:getOrganizationDeviceName()}}:{}),bindingTicket:bindingTicket.value,username:destination.value.trim(),email:mode.value==='PASSWORD'?bindingEmail.value.trim():destination.value.trim(),mobile:destination.value.trim(),password:password.value,code:mode.value==='PASSWORD'?bindingCode.value:code.value,clientType:'MINIAPP',deviceId:getOrCreateOrganizationDeviceId(),deviceName:getOrganizationDeviceName()}});if('registrationRequired' in result&&result.registrationRequired){registering.value=true;return;}const session='session' in result ? result.session : result;if(bindingTicket.value)await rememberPending(session.accessToken);saveRegistrationSession(session);if(await routeRequiredPasswordChange(session.accessToken,'personal'))return;uni.removeStorageSync(pendingKey);if(await routeTeacherAccount(session.accessToken))return;await uni.reLaunch({url:'/pages/my-organizations/my-organizations?identity=personal'});}catch(e){if(bindingTicket.value&&e instanceof ApiError&&(/TICKET|BINDING.*EXPIRED/.test(e.code)||/票据|绑定.*过期/.test(e.message))){restartWechat();error.value='微信授权已过期，请重新授权后继续绑定';}else error.value=e instanceof Error?e.message:'登录失败';}finally{busy.value=false;}}
</script>
















<style lang="scss" scoped>
@import '@/styles/account-access.scss'; .code-row{display:flex;align-items:center;gap:16rpx}.code-row input{flex:1;min-width:0}.code-row button{background:#e7efe9;color:#145448;padding:0 20rpx;font-size:25rpx}.contact-switch{display:flex;justify-content:center;margin:20rpx 0;gap:28rpx}.contact-switch button{margin:0;background:transparent;font-size:24rpx;color:#8a9390;padding:0}.contact-switch button::after{border:0}.contact-switch .selected{color:#125747;font-weight:600;border-bottom:2rpx solid #125747}

.compact-login {display:flex;flex-direction:column;min-height:calc(100vh - var(--window-top, 0px));padding-bottom:max(20rpx,env(safe-area-inset-bottom));}
.compact-login .login-heading {flex:none;margin-top:24rpx;}
.compact-login .login-footer {flex:none;padding-top:24rpx;}
.compact-login::before {flex:none;padding-top:28rpx;padding-bottom:36rpx;margin-bottom:0;}
.compact-login::after {top:70rpx;}
.compact-login .ld-heading-title {font-size:40rpx;line-height:1.25;}
.compact-login .ld-heading-sub {margin:8rpx 0 12rpx;font-size:26rpx;}
.compact-login .access-card {flex:none;margin-top:28rpx;margin-bottom:0;padding:16rpx 12rpx;}
.compact-login .access-tabs button {height:88rpx;line-height:88rpx;}
.compact-login .contact-switch {margin:10rpx 0;}
.compact-login .access-tip {margin:12rpx 0;padding:12rpx 16rpx;font-size:24rpx;line-height:1.5;}
.compact-login .ld-field-label {font-size:26rpx;line-height:1.4;}
.compact-login input {height:88rpx;margin:14rpx 0;}
.compact-login .code-row input,.compact-login .code-row button {height:88rpx;line-height:88rpx;margin:0;}
.compact-login .access-primary {height:88rpx;line-height:88rpx;margin-top:20rpx;}
.compact-login .wechat-method {display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:260rpx;gap:16rpx;}
.compact-login .wechat-method-symbol {display:flex;align-items:center;justify-content:center;width:96rpx;height:96rpx;border-radius:28rpx;background:#184F40;color:white;font-size:48rpx;}
.compact-login .wechat-method-title {font-size:30rpx;font-weight:600;color:#184F40;}
.compact-login .wechat-method-caption {font-size:24rpx;color:#77877d;}
.compact-login .access-link {height:64rpx;line-height:64rpx;margin-top:4rpx;}


.student-entry {background:transparent!important;color:#184F40!important;border:1px solid #184F40!important;border-radius:16rpx;font-weight:600;height:96rpx!important;line-height:94rpx!important;margin-top:0!important;}
.entry-arrow {margin-left:12rpx;font-weight:400;}
.register-entry {margin-top:28rpx!important;font-size:26rpx!important;}
.wechat-landing .login-heading {margin-top:60rpx;margin-bottom:40rpx;}
.wechat-landing .access-card {padding:0 12rpx;margin-top:0;}
.wechat-landing .access-primary {height:96rpx;line-height:96rpx;margin-top:0;border-radius:16rpx;}
.wechat-landing .login-footer {padding:24rpx 12rpx 0;}


.compact-login .login-heading {margin-top:36rpx;margin-bottom:36rpx;}
.login-panel {background:#fffefa;border:1rpx solid #e0e8e1;border-radius:32rpx;padding:36rpx 28rpx;box-shadow:0 16rpx 48rpx rgba(24,79,64,.05);}
.panel-heading {display:flex;align-items:center;gap:24rpx;margin-bottom:32rpx;}
.panel-title {font-size:26rpx;color:#63796c;white-space:nowrap;}
.panel-rule {height:1rpx;background:#e0e8e1;flex:1;}
.login-panel .access-card {padding:0;margin:0;}
.login-panel .login-footer {padding:24rpx 0 0;}
.login-consent {display:flex;align-items:flex-start;gap:8rpx;margin-top:32rpx;}
.consent-check {display:flex;align-items:center;}
.consent-check checkbox {transform:scale(.72);transform-origin:top center;}
.consent-text {font-size:23rpx;line-height:40rpx;color:#819087;flex:1;}
.consent-text text {color:#184F40;}
.login-bottom-rule {width:64rpx;height:6rpx;background:#cad9cf;border-radius:3rpx;margin:48rpx auto 0;}
.wechat-landing .login-heading {margin-top:40rpx;margin-bottom:40rpx;}


.wechat-landing .access-primary,.compact-login .student-entry,.compact-login .register-entry {display:flex;align-items:center;justify-content:space-between;box-sizing:border-box;padding:24rpx 28rpx;height:140rpx!important;line-height:1.4!important;text-align:left;border-radius:20rpx;}
.entry-copy {display:flex;flex-direction:column;gap:8rpx;}
.entry-title {font-size:32rpx;font-weight:600;}
.entry-subtitle {font-size:24rpx;font-weight:400;opacity:.7;}
.entry-arrow {font-size:44rpx;line-height:1;}
.compact-login .student-entry {background:#f0f5f1!important;border-color:#c9dcd0!important;}
.compact-login .register-entry {height:116rpx!important;margin-top:24rpx!important;border:1rpx solid #dde6de;background:#fffefa;color:#184F40;}
.compact-login .register-entry .entry-title {font-size:28rpx;}
.login-consent {align-items:center;gap:0;margin-top:28rpx;white-space:nowrap;}
.consent-check checkbox {transform:scale(.62);transform-origin:center;width:40rpx;}
.consent-text {font-size:21rpx;line-height:36rpx;white-space:nowrap;flex:1;}
.login-panel {padding:28rpx 20rpx;}
.panel-heading {display:none;}
.compact-login .ld-heading-title {font-size:36rpx;}


.compact-login {padding-left:28rpx;padding-right:28rpx;padding-bottom:max(24rpx,env(safe-area-inset-bottom));}
.compact-login::before {padding-top:36rpx;padding-bottom:40rpx;margin-left:-28rpx;margin-right:-28rpx;font-size:38rpx;}
.compact-login::after {top:70rpx;}
.compact-login .login-heading,.wechat-landing .login-heading {margin-top:36rpx;margin-bottom:32rpx;}
.compact-login .ld-heading-title {font-size:38rpx;letter-spacing:1rpx;}
.brand-caption {display:block;text-align:center;font-size:24rpx;color:#849389;margin-top:16rpx;line-height:1.6;}
.login-panel {padding:32rpx 24rpx;border-radius:32rpx;}
.panel-heading {display:flex;margin-bottom:28rpx;}
.panel-title {font-size:25rpx;color:#708678;}
.wechat-landing .access-primary,.compact-login .student-entry {height:156rpx!important;padding:28rpx 30rpx;}
.entry-title {font-size:34rpx;letter-spacing:1rpx;}
.entry-subtitle {font-size:23rpx;}
.compact-login .register-entry {height:124rpx!important;}
.login-signature {display:flex;justify-content:center;align-items:center;gap:20rpx;margin:32rpx 0 0;font-size:22rpx;color:#95a69a;}
.signature-line {height:1rpx;width:36rpx;background:#bdcec1;}
@media (max-height:650px) {
.compact-login::before {padding-top:24rpx;padding-bottom:28rpx;}
.compact-login::after {top:25rpx;}
.compact-login .login-heading,.wechat-landing .login-heading {margin-top:22rpx;margin-bottom:24rpx;}
.login-panel {padding-top:24rpx;padding-bottom:24rpx;}
.wechat-landing .access-primary,.compact-login .student-entry {height:132rpx!important;}
.compact-login .register-entry {height:112rpx!important;}
.login-signature {margin-top:22rpx;}
}


/* Keep the brand position; balance the login block within available screen height. */
.wechat-landing .login-heading {margin-top:clamp(36rpx,5vh,84rpx);}
@media (max-height:650px) {
.wechat-landing .login-heading {margin-top:24rpx;}
}


.compact-login .hero-student {background:#184F40!important;color:#fff!important;border-color:#184F40!important;margin-bottom:24rpx!important;box-shadow:0 8rpx 24rpx rgba(24,79,64,.12);}
.wechat-landing .access-primary {background:#f0f5f1!important;color:#184F40!important;border:1rpx solid #c9dcd0;box-shadow:none;}

.compact-login{background:#f3f9f2;padding:70rpx 40rpx 48rpx;}.login-heading{padding:30rpx 0 55rpx;margin:0;}.login-heading .ld-heading-title{font-size:44rpx;color:#144d39;}.brand-caption{font-size:25rpx;color:#748276;}.login-panel{border-radius:28rpx;background:#fff;padding:30rpx;}.panel-title{font-size:36rpx;}.panel-rule{display:none;}.compact-login .hero-student{background:#184f40!important;color:#fff!important;border:1rpx solid #184f40!important;box-shadow:0 10rpx 24rpx #184f4018;}.compact-login .hero-student .entry-title,.compact-login .hero-student .entry-arrow{color:#fff!important;}.compact-login .hero-student .entry-subtitle{color:#c1d9ce!important;}.access-card{box-shadow:none;border:0;padding:0;background:transparent;}.access-primary{background:#14664c;border-radius:20rpx;min-height:100rpx;}.entry-title{font-size:30rpx;}.entry-subtitle{font-size:23rpx;}.login-consent{margin-top:30rpx;}.login-signature{margin-top:40rpx;}
.binding-choice{padding:10rpx 0;}.binding-title{display:block;font-size:36rpx;font-weight:600;color:#164d39;margin-bottom:18rpx;}.binding-option{display:flex;flex-direction:column;align-items:flex-start;gap:12rpx;width:100%;background:#eef7ee;color:#184f40;border:1rpx solid #dcebdc;border-radius:20rpx;padding:24rpx;margin:22rpx 0;text-align:left;}.binding-option text:first-child{font-size:30rpx;font-weight:600;}.binding-option text:last-child{font-size:23rpx;color:#728376;}.binding-heading{display:flex;align-items:center;gap:16rpx;margin-bottom:24rpx;}.binding-heading button{background:transparent;font-size:24rpx;padding:0;color:#176c51;margin:0;}.binding-heading>text{font-size:28rpx;font-weight:600;}
.compact-login::before,.compact-login::after{display:none!important;}
</style>
