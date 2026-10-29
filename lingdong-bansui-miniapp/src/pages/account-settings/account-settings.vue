<template>
 <view class="secondary-page">
  <PixelPageHeader back fallback="/pages/index/index" :title="panel==='contact'?'邮箱手机号维护验证':panel==='wechat'?'微信绑定管理':panel==='members'?'组织账号管理':'修改密码'" />
  <text v-if="error" class="error">{{error}}</text>
  <view v-if="!settings" class="section empty"><PrototypeIcon name="shield" tone="muted" :size="64"/><text>账号信息暂未加载</text><button class="secondary" :disabled="busy" @tap="load">重新加载</button></view>
  <template v-else>
   <view class="section account-summary"><view class="person"><PrototypeIcon name="shield" :size="38"/></view><view class="person-copy"><text class="name">账号与登录方式</text><text class="meta">{{settings.passwordChangeRequired?'请先修改初始密码':'管理当前账号的登录信息'}}</text></view></view>
   <view v-if="!settings.passwordChangeRequired&&settings.hasPassword" class="section settings-navigation">
    <button :class="{selected:panel==='password'}" :disabled="busy" @tap="panel='password'"><PrototypeIcon name="lock" :size="30"/><text>密码</text></button>
    <button :class="{selected:panel==='contact'}" :disabled="busy" @tap="panel='contact'"><PrototypeIcon name="mail" :size="30"/><text>联系方式</text></button>
    <!-- #ifdef MP-WEIXIN --><button :class="{selected:panel==='wechat'}" :disabled="busy" @tap="panel='wechat'"><PrototypeIcon name="shield" :size="30"/><text>微信</text></button><!-- #endif -->
    <button v-if="settings.canManageUsers" :class="{selected:panel==='members'}" :disabled="busy" @tap="panel='members'"><PrototypeIcon name="people" :size="30"/><text>组织账号</text></button>
   </view>
   <view v-if="panel==='password'||settings.passwordChangeRequired||!settings.hasPassword" class="section">
    <view class="section-heading"><text class="section-title">{{settings.passwordChangeRequired?'修改初始密码':settings.hasPassword?'修改密码':'设置密码'}}</text></view>
    <template v-if="settings.hasPassword"><text class="field-label">当前密码</text><view class="pw-wrap"><input v-model="oldPassword" :password="!showOld" placeholder="输入当前密码" :disabled="busy"/><button class="pw-eye" :disabled="busy" @tap="showOld=!showOld"><PrototypeIcon :name="showOld?'eye-off':'eye'" tone="muted" :size="36"/></button></view></template>
    <template v-else><text class="small">先验证已绑定的联系方式，再设置密码。</text><view class="segmented"><button v-if="settings.mobile" :class="{active:channel==='SMS'}" :disabled="busy" @tap="select('SMS')">验证手机</button><button v-if="settings.email" :class="{active:channel==='EMAIL'}" :disabled="busy" @tap="select('EMAIL')">验证邮箱</button></view><view class="code-row"><input v-model="oldCode" placeholder="验证码" :disabled="busy"/><button class="secondary" :disabled="busy" @tap="send('CHANGE_OLD')">发送验证码</button></view></template>
    <text class="field-label">新密码</text><view class="pw-wrap"><input v-model="newPassword" :password="!showNew" placeholder="请输入 8-20 位新密码，包含字母和数字" :disabled="busy"/><button class="pw-eye" :disabled="busy" @tap="showNew=!showNew"><PrototypeIcon :name="showNew?'eye-off':'eye'" tone="muted" :size="36"/></button></view>
    <!-- 8.5 对照 07-08：密码强度条（规则实时反馈） -->
    <view class="strength-row"><view v-for="n in 4" :key="n" class="strength-seg" :class="strengthSegment(n)"/><text class="strength-label" :class="strengthClass">{{strengthLabel}}</text></view>
    <text class="field-label">确认新密码</text><view class="pw-wrap"><input v-model="repeatPassword" :password="!showRepeat" placeholder="请再次输入新密码" :disabled="busy"/><button class="pw-eye" :disabled="busy" @tap="showRepeat=!showRepeat"><PrototypeIcon :name="showRepeat?'eye-off':'eye'" tone="muted" :size="36"/></button></view><button class="primary block-action" :disabled="busy" @tap="password">保存新密码</button>
    <!-- 8.5 对照 07-08：安全提示条 -->
    <view class="safety-band"><PrototypeIcon name="shield" tone="amber" :size="34"/><text class="safety-text">为保障账号安全，建议定期更换密码，并避免使用与其他平台相同的密码。</text></view>
   </view>
   <template v-if="!settings.passwordChangeRequired&&settings.hasPassword">
    <template v-if="panel==='contact'">
     <template v-if="!contactEditing">
      <view v-for="item in contactCards" :key="item.channel" class="section contact-card"><view class="contact-heading"><PrototypeIcon :name="item.channel==='EMAIL'?'mail':'phone'" :size="34"/><text>{{item.channel==='EMAIL'?'邮箱验证':'手机号验证'}}</text></view><view class="contact-frame"><view class="contact-summary"><text>{{item.channel==='EMAIL'?'绑定邮箱':'绑定手机号'}}</text><text class="contact-value">{{maskedContact(item.value,item.channel)}}</text><text class="badge" v-if="item.value">已绑定</text></view><text class="contact-hint">可用于找回密码、接收重要安全通知</text><button class="secondary contact-action" :disabled="busy" @tap="editContact(item.channel)">{{item.value?'更换':'绑定'}}{{item.channel==='EMAIL'?'邮箱':'手机号'}}</button></view></view>
     </template>
     <view v-else class="section"><view class="section-heading"><text class="section-title">{{channel==='EMAIL'?'邮箱验证':'手机号验证'}}</text><button class="contact-cancel" :disabled="busy" @tap="cancelContact">取消</button></view>
     <text class="field-label">当前密码</text><input v-model="currentPassword" password placeholder="验证当前账号身份" :disabled="busy"/>
     <template v-if="oldContact"><text class="field-label">原{{channel==='SMS'?'手机':'邮箱'}}验证码</text><view class="code-row"><input v-model="oldCode" placeholder="原联系方式验证码" :disabled="busy"/><button class="secondary" :disabled="busy" @tap="send('CHANGE_OLD')">发送验证码</button></view></template>
     <text class="field-label">新{{channel==='SMS'?'手机号':'邮箱'}}</text><input v-model="destination" :placeholder="channel==='SMS'?'输入新手机号':'输入新邮箱'" :disabled="busy"/><text class="field-label">新联系方式验证码</text><view class="code-row"><input v-model="code" placeholder="验证码" :disabled="busy"/><button class="secondary" :disabled="busy" @tap="send(oldContact?'CHANGE_NEW':'BIND')">发送验证码</button></view><button class="primary block-action" :disabled="busy" @tap="contact">验证并保存</button>
     </view>
    </template>
    <!-- #ifdef MP-WEIXIN --><view v-if="panel==='wechat'" class="section"><view class="section-heading"><text class="section-title">微信登录</text><text class="badge">{{settings.wechatBound?'已绑定':'未绑定'}}</text></view><text class="small">验证当前密码和微信身份，完成后重新登录。</text><text class="field-label">当前密码</text><input v-model="currentPassword" password placeholder="验证当前账号身份" :disabled="busy"/><button class="primary block-action" :disabled="busy" @tap="wechat">{{settings.wechatBound?'验证并更换微信':'验证并绑定微信'}}</button></view><!-- #endif -->
    <view v-if="panel==='members'&&settings.canManageUsers" class="section"><view class="section-heading"><text class="section-title">组织账号密码重置</text></view><view class="search-row"><input v-model="keyword" placeholder="账号或姓名" :disabled="busy"/><button class="secondary" :disabled="busy" @tap="findUsers">查询</button></view><view v-if="usersLoaded&&!users.length" class="empty">暂无可管理成员</view><view v-for="user in users" :key="user.id" class="row"><view class="person"><PrototypeIcon name="user" :size="32"/></view><view class="person-copy"><text class="name">{{user.displayName}}</text><text class="meta">{{user.username}}</text></view><button class="secondary reset-button" :disabled="busy" @tap="resetUser(user)">临时密码</button></view></view>
    <view class="section organization-links"><button class="link-row" :disabled="busy" @tap="organizations"><PrototypeIcon name="school" :size="32"/><text>选择我的组织</text><PrototypeIcon name="arrow" tone="muted" :size="26"/></button><button v-if="identity==='organization'" class="link-row" :disabled="busy" @tap="teachers"><PrototypeIcon name="people" :size="32"/><text>教师账号管理</text><PrototypeIcon name="arrow" tone="muted" :size="26"/></button></view>
   </template>
  </template>
  <button v-if="studentSettings" class="secondary" :disabled="busy" @tap="logout">退出登录</button>
 </view>
</template>
<script setup lang="ts">
import PixelPageHeader from "@/components/pixel/PixelPageHeader.vue";
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {logoutStudent} from '@/api/auth';
import {getStudentSession,clearStudentSession} from '@/session/student-session';
import {ref,computed} from 'vue';
import {onLoad,onHide,onUnload} from '@dcloudio/uni-app';
import {onboardingIdentity,resolveOnboardingSession,type OnboardingIdentity} from '@/session/onboarding-session';
import {request} from '@/api/http';
import {accountSettingsRequest,changeAccountPassword,type AccountSettings,type ContactChannel} from '@/api/account-settings';
const panel=ref('password');
const studentSettings=ref(false);
const identity=ref<OnboardingIdentity|null>(null),settings=ref<AccountSettings|null>(null),busy=ref(false),error=ref('');
const channel=ref<ContactChannel>('EMAIL'),destination=ref(''),code=ref(''),oldCode=ref(''),currentPassword=ref(''),oldPassword=ref(''),newPassword=ref(''),repeatPassword=ref('');
const contactEditing=ref(false);
const contactCards=computed(()=>[{channel:'EMAIL' as ContactChannel,value:settings.value?.email},{channel:'SMS' as ContactChannel,value:settings.value?.mobile}]);
function maskedContact(value:string|null|undefined,type:ContactChannel){if(!value)return '未绑定';if(type==='SMS')return value.length>7?value.slice(0,3)+'****'+value.slice(-4):'****';const index=value.lastIndexOf('@');return index>0?value.slice(0,1)+'***'+value.slice(index):'****';}
function clearContactDraft(){destination.value='';code.value='';oldCode.value='';currentPassword.value='';}
function editContact(value:ContactChannel){if(busy.value)return;clearContactDraft();select(value);contactEditing.value=true;error.value='';}
function cancelContact(){if(busy.value)return;clearContactDraft();contactEditing.value=false;error.value='';}
interface ManagedUser{id:string;username:string;displayName:string;userType:string;}
const users=ref<ManagedUser[]>([]),usersLoaded=ref(false),keyword=ref('');
const oldContact=computed(()=>channel.value==='SMS'?settings.value?.mobile:settings.value?.email);
/** 8.5 对照 07-08：密码可见性 toggle 与强度条。 */
const showOld=ref(false),showNew=ref(false),showRepeat=ref(false);
function clearSensitiveDrafts(){clearContactDraft();contactEditing.value=false;oldPassword.value='';newPassword.value='';repeatPassword.value='';showOld.value=false;showNew.value=false;showRepeat.value=false;}
onHide(clearSensitiveDrafts);onUnload(clearSensitiveDrafts);
function passwordStrength(value:string){
 if(!value)return {score:0,label:'请输入新密码'};
 if(value.length>20||/[^A-Za-z0-9]/.test(value))return {score:0,label:'不符合规则'};
 if(value.length<8||!/[A-Za-z]/.test(value)||!/\d/.test(value))return {score:1,label:'弱'};
 if(value.length<12)return {score:2,label:'中'};
 if(value.length<16)return {score:3,label:'较强'};
 return {score:4,label:'强'};
}
const strengthScore=computed(()=>passwordStrength(newPassword.value).score);
const strengthClass=computed(()=>`s${strengthScore.value}`);
const strengthLabel=computed(()=>passwordStrength(newPassword.value).label);
function strengthSegment(n:number){return n<=strengthScore.value?strengthClass.value:'s0';}
onLoad(q=>{studentSettings.value=q?.identity==='student';if(['password','contact','wechat','members'].includes(String(q?.panel)))panel.value=String(q?.panel);identity.value=onboardingIdentity(q?.identity==='registration'?'personal':q?.identity);load();});
async function token(){if(studentSettings.value){const current=getStudentSession();if(!current)throw new Error('请重新登录');return current.accessToken;}const session=await resolveOnboardingSession(identity.value);if(!session)throw new Error('请从当前账号入口登录后进入');return session.accessToken;}
async function load(){try{const accessToken=await token();const selected=uni.getStorageSync('lingdong.selected-organization');const suffix=selected?.accessToken===accessToken&&selected.organizationId?'?organizationId='+encodeURIComponent(selected.organizationId):'';settings.value=await accountSettingsRequest<AccountSettings>(accessToken,suffix);if(!settings.value.hasPassword)channel.value=settings.value.email?'EMAIL':'SMS';error.value='';}catch(e){error.value=e instanceof Error?e.message:'加载失败';}}
async function run(action:()=>Promise<void>){if(busy.value)return;busy.value=true;error.value='';try{await action();}catch(e){error.value=e instanceof Error?e.message:'操作失败';}finally{busy.value=false;}}
function select(value:ContactChannel){channel.value=value;destination.value='';code.value='';oldCode.value='';}
function send(purpose:'BIND'|'CHANGE_OLD'|'CHANGE_NEW'){return run(async()=>{await accountSettingsRequest<void>(await token(),'/codes',{channel:channel.value,destination:purpose==='CHANGE_OLD'?oldContact.value:destination.value.trim(),purpose});uni.showToast({title:'验证码已发送',icon:'none'});});}
function contact(){return run(async()=>{await accountSettingsRequest<void>(await token(),'/contact',{channel:channel.value,destination:destination.value.trim(),code:code.value.trim(),oldCode:oldCode.value.trim(),currentPassword:currentPassword.value});clearContactDraft();contactEditing.value=false;await load();});}
function password(){return run(async()=>{if(! /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/.test(newPassword.value)||newPassword.value!==repeatPassword.value)throw new Error('新密码8 至 20 位字母和数字组合，两次输入需一致');const oldToken=await token();const pending=uni.getStorageSync('lingdong.wechat.pending-binding');let resumeUserId='';if(pending){const me=await request<{userId:string}>('/auth/me',{header:{Authorization:`Bearer ${oldToken}`}});if(String(me.userId)===pending.userId)resumeUserId=pending.userId;else uni.removeStorageSync('lingdong.wechat.pending-binding');}if(settings.value?.hasPassword)await changeAccountPassword(oldToken,oldPassword.value,newPassword.value);else await accountSettingsRequest<void>(oldToken,'/password',{channel:channel.value,code:oldCode.value.trim(),newPassword:newPassword.value});for(const key of ['lingdong.registration.session','lingdong.parent.session','lingdong.organization.session','lingdong.selected-organization']){if(uni.getStorageSync(key)?.accessToken===oldToken)uni.removeStorageSync(key);}oldPassword.value='';newPassword.value='';repeatPassword.value='';await uni.showModal({title:'密码已修改',content:'请使用新密码重新登录。',showCancel:false});await uni.reLaunch({url:resumeUserId?'/pages/unified-login/unified-login?resume=1&userId='+encodeURIComponent(resumeUserId):'/pages/index/index'});});}
function wechat(){return run(async()=>{const result=await uni.login({provider:'weixin'});if(!result.code)throw new Error('未取得微信登录凭证');const oldToken=await token();await accountSettingsRequest<void>(oldToken,'/wechat',{temporaryCode:result.code,currentPassword:currentPassword.value});currentPassword.value='';for(const key of ['lingdong.registration.session','lingdong.parent.session','lingdong.organization.session','lingdong.selected-organization']){if(uni.getStorageSync(key)?.accessToken===oldToken)uni.removeStorageSync(key);}await uni.showModal({title:'微信绑定已更新',content:'请使用当前账号或刚绑定的微信重新登录。',showCancel:false});await uni.reLaunch({url:'/pages/index/index'});});}
function logout(){return run(async()=>{const current=getStudentSession();if(!current)return;try{await logoutStudent(current.accessToken);}catch{}finally{if(getStudentSession()?.accessToken===current.accessToken){clearStudentSession();await uni.reLaunch({url:'/pages/index/index'});}}});}
function organizations(){if(identity.value)uni.navigateTo({url:'/pages/my-organizations/my-organizations?identity='+identity.value+'&choose=1'});}
async function selectedScope(){const accessToken=await token();const selected=uni.getStorageSync('lingdong.selected-organization');if(!selected?.organizationId||selected.accessToken!==accessToken)throw new Error('请先选择当前组织');return {accessToken,organizationId:selected.organizationId};}
function findUsers(){return run(async()=>{users.value=[];usersLoaded.value=false;const scope=await selectedScope();users.value=await request<ManagedUser[]>('/account-management/users?organizationId='+encodeURIComponent(scope.organizationId)+'&keyword='+encodeURIComponent(keyword.value.trim()),{header:{Authorization:`Bearer ${scope.accessToken}`}});usersLoaded.value=true;});}
function resetUser(user:ManagedUser){return run(async()=>{const scope=await selectedScope();const result=await uni.showModal({title:'设置 '+user.displayName+' 的临时密码',content:'用户下次登录必须修改此密码。',editable:true,placeholderText:'8 至 20 位字母和数字组合'});if(!result.confirm)return;const password=result.content||'';if(!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/.test(password))throw new Error('密码需为 8 至 20 位字母和数字组合');await request<void>('/account-management/users/'+encodeURIComponent(user.id)+'/password-reset',{method:'POST',data:{organizationId:scope.organizationId,password},header:{Authorization:`Bearer ${scope.accessToken}`}});uni.showToast({title:'临时密码已设置',icon:'none'});});}
function teachers(){uni.navigateTo({url:'/pages/organization-teachers/organization-teachers'});}
</script>


<style lang="scss" scoped>
.contact-card{padding:22rpx!important;}.contact-heading{display:flex;align-items:center;gap:12rpx;font-size:29rpx;font-weight:650;margin-bottom:16rpx;color:#173e32;}.contact-frame{border:1rpx solid #dfebe3;border-radius:20rpx;padding:20rpx;}.contact-summary{display:flex;align-items:center;gap:16rpx;font-size:25rpx;}.contact-value{flex:1;min-width:0;text-align:right;overflow-wrap:anywhere;color:#183a30;}.contact-summary .badge{flex-shrink:0;font-size:21rpx;}.contact-hint{display:block;margin:16rpx 0 20rpx;color:#84948a;font-size:23rpx;line-height:1.5;}.contact-action{width:100%;margin:0;border:1rpx solid #5b9c82!important;border-radius:14rpx!important;background:transparent!important;color:#17664e;font-size:27rpx!important;padding:14rpx!important;line-height:1.5;}.contact-cancel{margin:0;padding:8rpx 16rpx;background:transparent;color:#587567;font-size:24rpx;}

@import "@/styles/secondary-prototype.scss";
.account-summary{display:flex;align-items:center;gap:20rpx;}.settings-navigation{display:flex;gap:8rpx;padding:12rpx!important;}.settings-navigation button{display:flex;flex:1;min-width:0;flex-direction:column;align-items:center;gap:10rpx;background:transparent;font-size:22rpx;color:#809486;padding:18rpx 4rpx;}.settings-navigation button.selected{background:#edf4ee;color:#184f40;}.block-action{margin-top:28rpx;}.code-row,.search-row{display:flex;align-items:center;gap:14rpx;}.code-row input,.search-row input{flex:1;min-width:0;}.code-row button,.search-row button{flex-shrink:0;font-size:23rpx;padding:22rpx 14rpx;}.segmented{margin-top:24rpx;}.organization-links{padding-top:8rpx!important;padding-bottom:8rpx!important;}.link-row{display:flex;align-items:center;gap:18rpx;width:100%;text-align:left;background:transparent;padding:22rpx 0!important;}.link-row>text{flex:1;color:#193e32;}.link-row+.link-row{border-top:1rpx solid #edf1ed;}.reset-button{font-size:23rpx!important;padding:16rpx!important;}

.secondary-page,.prototype-workspace{background:#f5f9f5!important;padding:24rpx 28rpx 70rpx!important;color:#1a382a;}
/* 8.5 对照 07-08：可见性 toggle + 强度条 + 安全提示条 */
.pw-wrap{display:flex;align-items:center;gap:12rpx;}.pw-wrap input{flex:1;min-width:0;}.pw-eye{flex-shrink:0;width:72rpx;height:72rpx;margin:0;padding:0;display:flex;align-items:center;justify-content:center;background:transparent;}
.strength-row{display:flex;align-items:center;gap:8rpx;margin-top:14rpx;}.strength-seg{flex:1;height:10rpx;border-radius:6rpx;background:#e4ebe6;}.strength-label{flex-shrink:0;margin-left:12rpx;font-size:23rpx;color:#8b968f;}
.strength-seg.s1{background:#d96b57;}.strength-seg.s2{background:#de9829;}.strength-seg.s3{background:#5ba46a;}.strength-seg.s4{background:#176f50;}
.strength-label.s1{color:#d96b57;}.strength-label.s2,.strength-label.s3{color:#de9829;}.strength-label.s4{color:#176f50;}
.safety-band{display:flex;align-items:flex-start;gap:14rpx;margin-top:24rpx;padding:24rpx;background:#fdf3e4;border:1rpx solid #f3ddba;border-radius:18rpx;}.safety-text{flex:1;color:#9a6b3f;font-size:24rpx;line-height:1.7;}
.account-summary{display:none;}.settings-navigation{background:transparent;box-shadow:none;border:0;margin:0 0 24rpx;}.settings-navigation button{border-radius:28rpx;flex-direction:row;font-size:23rpx;padding:12rpx 4rpx;}.settings-navigation button .prototype-icon{display:none;}.section{background:white;border:0;border-radius:26rpx;padding:28rpx;margin-bottom:24rpx;}.field-label{font-size:27rpx;font-weight:600;color:#26332d;margin-top:24rpx;margin-bottom:12rpx;}input{min-height:84rpx;background:#fafcfb;border:1rpx solid #dce4de;border-radius:16rpx;padding:0 24rpx;font-size:26rpx;box-sizing:border-box;}.primary{background:linear-gradient(110deg,#176d55,#075d42);border-radius:18rpx;font-size:30rpx;line-height:1.5;}.detail-line{padding:24rpx 0;border-bottom:1rpx solid #e7eee8;}.code-row .secondary{color:#136648;background:#eaf5ed;}.section-title{font-size:29rpx;}
</style>

