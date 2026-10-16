<template><view class="my-page ld-page has-tabbar"><NativeStatusSpacer/>
 <view v-if="ready" class="my-profile" @tap="openAccountSettings"><view class="profile-avatar"><!-- 通用默认插画，并非账号真实照片 --><image src="/static/pixel/default-companion-avatar.png" mode="aspectFill" style="width:100%;height:100%;border-radius:50%"/></view><view class="profile-copy"><text class="profile-name">{{displayName||'成长伙伴'}}</text><text class="profile-caption">陪伴是最长情的成长</text></view><PrototypeIcon name="arrow" :size="32"/></view>
 <view v-if="loading" class="my-loading">正在加载账号信息…</view><view v-if="errorMessage" class="my-loading">{{errorMessage}}<button @tap="loadAccount">重试</button></view>
 <view v-if="organizationEnabled" class="my-group"><button class="my-row" @tap="openOrganizations"><PrototypeIcon name="people" :size="48"/><text>我的组织</text><PrototypeIcon name="arrow" tone="muted" :size="26"/></button></view>
 <view v-if="ready" class="my-group"><button v-if="accountSecurityEnabled" class="my-row" @tap="openAccountSettings"><PrototypeIcon name="shield" :size="48"/><text>账号与安全</text><PrototypeIcon name="arrow" tone="muted" :size="26"/></button><button v-if="accountSecurityEnabled" class="my-row" @tap="openAccountSecurity"><PrototypeIcon name="phone" :size="48"/><text>登录设备</text><PrototypeIcon name="arrow" tone="muted" :size="26"/></button><button class="my-row" @tap="openAccountSettings"><PrototypeIcon name="settings" :size="48"/><text>设置</text><PrototypeIcon name="arrow" tone="muted" :size="26"/></button></view>
 <button class="my-logout" :disabled="loggingOut" @tap="logout">退出登录</button><AppTabBar :items="PARENT_TABS" :active="3"/>
</view></template>

<script setup lang="ts">
import NativeStatusSpacer from "@/components/NativeStatusSpacer.vue";
import {ApiError} from '@/api/http';
import PrototypeIcon from '@/components/PrototypeIcon.vue';import AppTabBar from '@/components/AppTabBar.vue';import {PARENT_TABS} from '@/config/tabbar';import {ref} from 'vue';import {onShow,onHide,onUnload} from '@dcloudio/uni-app';import {parentChildrenApi} from '@/api/parent-children';import {isParentIdentity} from '@/models/parent-identity';import {getMiniappCapabilities} from '@/api/capability';import {logoutParent} from '@/api/auth';import {clearParentSession,getParentSession} from '@/session/parent-session';
const ready=ref(false),errorMessage=ref('');let verifiedToken='';
const displayName=ref(''),loading=ref(true),loggingOut=ref(false),organizationEnabled=ref(false),accountSecurityEnabled=ref(false);let epoch=0;
function reset(){epoch++;ready.value=false;verifiedToken='';errorMessage.value='';displayName.value='';organizationEnabled.value=false;accountSecurityEnabled.value=false;}onHide(reset);onUnload(reset);
async function loadAccount(){reset();const n=epoch,session=getParentSession();loading.value=true;if(!session){await uni.reLaunch({url:'/pages/index/index'});return;}try{const user=await parentChildrenApi.me(session.accessToken);if(n!==epoch||getParentSession()?.accessToken!==session.accessToken)return;if(!isParentIdentity(user)){clearParentSession();await uni.reLaunch({url:'/pages/index/index'});return;}const c=await getMiniappCapabilities();if(n!==epoch||getParentSession()?.accessToken!==session.accessToken)return;displayName.value=user.displayName||user.username||'';organizationEnabled.value=user.permissionCodes.includes('ORGANIZATION_ONBOARDING_READ');accountSecurityEnabled.value=c.accountSecurityManagementEnabled;verifiedToken=session.accessToken;ready.value=true;}catch(e){if(n!==epoch||getParentSession()?.accessToken!==session.accessToken)return;organizationEnabled.value=false;accountSecurityEnabled.value=false;if(e instanceof ApiError&&e.statusCode===401){clearParentSession();await uni.reLaunch({url:'/pages/index/index'});return;}errorMessage.value=e instanceof Error?e.message:'账号信息暂不可用，请重试';}finally{if(n===epoch)loading.value=false;}}
onShow(loadAccount);
function verified(){return ready.value&&getParentSession()?.accessToken===verifiedToken;}
function openAccountSettings(){if(!verified())return;return uni.navigateTo({url:'/pages/account-settings/account-settings?identity=parent'});}function openOrganizations(){if(verified()&&organizationEnabled.value)return uni.navigateTo({url:'/pages/organization-overview/organization-overview?identity=parent'});}function openAccountSecurity(){if(verified()&&accountSecurityEnabled.value)return uni.navigateTo({url:'/pages/account-security/account-security?identity=parent'});}
async function logout(): Promise<void> {
  const session = getParentSession();
  if (!session || loggingOut.value) return;
  loggingOut.value = true;
  try {
    await logoutParent(session.accessToken);
  } catch {
    // 服务端不可用时仍清除本地家长会话，不影响独立的学生会话。
  } finally {
    loggingOut.value=false;
    if(getParentSession()?.accessToken!==session.accessToken)return;
    clearParentSession();
    await uni.reLaunch({ url: '/pages/index/index' });
  }
}
</script>



<style scoped>
.my-page{min-height:calc(100vh - var(--window-top, 0px));background:#f5f8f4;box-sizing:border-box;padding:28rpx 32rpx calc(140rpx + env(safe-area-inset-bottom));color:#193e32;}.my-profile{display:flex;align-items:center;gap:36rpx;padding:90rpx 4rpx 40rpx;}.profile-avatar{width:190rpx;height:190rpx;border-radius:50%;background:linear-gradient(130deg,#dfeddd,#f8f0dd);border:5rpx solid white;display:flex;align-items:center;justify-content:center;}.profile-copy{flex:1;min-width:0;}.profile-name{display:block;font-size:42rpx;font-weight:700;}.profile-caption{display:block;font-size:34rpx;color:#88978b;margin-top:12rpx;}.my-group{background:#ffffff;border-radius:28rpx;margin-bottom:24rpx;overflow:hidden;border:1rpx solid #e7eee4;}.my-row{display:flex;align-items:center;gap:22rpx;width:100%;text-align:left;margin:0;background:transparent;color:#1f4033;padding:32rpx 26rpx;line-height:1.5;border-radius:0;}.my-row::after,.my-logout::after{border:0;}.my-row+.my-row{border-top:1rpx solid #eef2e9;}.my-row>text{flex:1;font-size:38rpx;font-weight:500;}.my-logout{margin:84rpx 0 0;border:1rpx solid #e1e9df;background:#eaf1e8;color:#b96655;border-radius:22rpx;font-size:36rpx;height:106rpx;line-height:106rpx;}.my-loading{font-size:24rpx;color:#8a978b;margin-bottom:20rpx;}
</style>
