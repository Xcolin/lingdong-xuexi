<template>
  <view class="ld-page has-tabbar">
    <view class="ld-topbar">
      <view class="ld-topbar-brand">
        <view class="ld-topbar-logo">家</view>
        <view>
          <text class="ld-topbar-name">家长端</text>
          <text class="ld-topbar-meta">{{ mobile || '' }}</text>
        </view>
      </view>
      <button class="ld-logout" :disabled="loggingOut" @tap="logout">退出</button>
    </view>

    <view class="ld-heading">
      <text class="ld-heading-title">我的</text>
      <text class="ld-heading-sub">账号、关系与安全设置</text>
    </view>

    <view v-if="loading" class="ld-loading">正在加载</view>
    <view class="ld-group">
      <button v-if="relationshipEnabled" class="ld-cell" @tap="openRelationships">
        <view class="ld-cell-icon orange">亲</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">家长关系</text>
          <text class="ld-cell-sub">主家长与副家长的关系管理</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="accountSecurityEnabled" class="ld-cell" @tap="openAccountSecurity">
        <view class="ld-cell-icon soft">安</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">账号安全</text>
          <text class="ld-cell-sub">登录设备与安全事件</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="accountLifecycleEnabled" class="ld-cell" @tap="openAccountLifecycle">
        <view class="ld-cell-icon soft">号</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">账号与手机号</text>
          <text class="ld-cell-sub">换绑手机号与账号注销</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
      <button v-if="studentWechatAuthEnabled" class="ld-cell" @tap="openStudentWechat">
        <view class="ld-cell-icon teal">微</view>
        <view class="ld-cell-body">
          <text class="ld-cell-title">学生微信绑定</text>
          <text class="ld-cell-sub">管理孩子的微信快捷登录</text>
        </view>
        <text class="ld-cell-arrow">›</text>
      </button>
    </view>

    <AppTabBar :items="PARENT_TABS" :active="3" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { logoutParent } from '@/api/auth';
import AppTabBar from '@/components/AppTabBar.vue';
import { PARENT_TABS } from '@/config/tabbar';
import { clearParentSession, getParentSession } from '@/session/parent-session';

const mobile = ref('');
const loading = ref(true);
const loggingOut = ref(false);
const relationshipEnabled = ref(false);
const accountSecurityEnabled = ref(false);
const accountLifecycleEnabled = ref(false);
const studentWechatAuthEnabled = ref(false);

onShow(async () => {
  const session = getParentSession();
  if (!session) {
    await uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    relationshipEnabled.value = capabilities.parentRelationshipManagementEnabled;
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
    accountLifecycleEnabled.value = capabilities.parentAccountLifecycleEnabled;
    studentWechatAuthEnabled.value = capabilities.studentWechatAuthEnabled === true;
  } catch {
    relationshipEnabled.value = false;
    accountSecurityEnabled.value = false;
    accountLifecycleEnabled.value = false;
    studentWechatAuthEnabled.value = false;
  } finally {
    loading.value = false;
  }
});

function openRelationships(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/parent-relationships/parent-relationships' });
}

function openAccountSecurity(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/account-security/account-security?identity=parent' });
}

function openAccountLifecycle(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/parent-account-lifecycle/parent-account-lifecycle' });
}

function openStudentWechat(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/parent-student-wechat/parent-student-wechat' });
}

async function logout(): Promise<void> {
  const session = getParentSession();
  if (!session || loggingOut.value) return;
  loggingOut.value = true;
  try {
    await logoutParent(session.accessToken);
  } catch {
    // 服务端不可用时仍清除本地家长会话，不影响独立的学生会话。
  } finally {
    clearParentSession();
    await uni.reLaunch({ url: '/pages/index/index' });
  }
}
</script>
