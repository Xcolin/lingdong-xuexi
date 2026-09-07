<template>
  <view class="page-shell">
    <view class="page-heading">
      <text class="heading-title">学生微信绑定</text>
      <view class="heading-rule" />
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <view v-else-if="items.length === 0" class="state-text">暂无可管理学生</view>
    <view v-else class="student-list">
      <view v-for="item in items" :key="item.studentId" class="student-row">
        <view class="student-info">
          <text class="student-name">{{ item.studentName }}</text>
          <text class="student-account">{{ item.studentAccountMasked }}</text>
        </view>
        <view class="binding-actions">
          <text :class="['binding-status', { bound: item.bound }]">
            {{ item.bound ? '已绑定' : '未绑定' }}
          </text>
          <button v-if="item.bound" class="unbind-button" :disabled="submittingId === item.studentId"
                  @tap="confirmUnbind(item)">
            解绑
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  listStudentWechatBindings,
  unbindStudentWechat,
  type StudentWechatBindingSummary
} from '@/api/student-wechat-binding';
import { getParentSession } from '@/session/parent-session';

const loading = ref(true);
const items = ref<StudentWechatBindingSummary[]>([]);
const submittingId = ref('');

onLoad(async () => {
  const session = getParentSession();
  if (!session) return leavePage();
  try {
    const capabilities = await getMiniappCapabilities();
    if (capabilities.studentWechatAuthEnabled !== true) return leavePage();
    items.value = await listStudentWechatBindings(session.accessToken);
  } catch {
    await uni.showToast({ title: '学生微信绑定加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
});

async function confirmUnbind(item: StudentWechatBindingSummary): Promise<void> {
  const session = getParentSession();
  if (!session || submittingId.value) return;
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: `解绑${item.studentName}的微信？`,
      content: '解绑后不能继续使用微信快捷登录，学生数据和账号登录不受影响。',
      confirmText: '确认解绑',
      confirmColor: '#b34f3b',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
  if (!confirmed) return;
  submittingId.value = item.studentId;
  try {
    await unbindStudentWechat(session.accessToken, item.studentId);
    item.bound = false;
    item.boundAt = null;
    await uni.showToast({ title: '已解绑', icon: 'success' });
  } catch (error) {
    await uni.showToast({
      title: error instanceof Error ? error.message : '解绑失败',
      icon: 'none'
    });
  } finally {
    submittingId.value = '';
  }
}

function leavePage(): Promise<unknown> {
  return uni.redirectTo({ url: '/pages/parent-home/parent-home' });
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 56rpx 40rpx 72rpx; box-sizing: border-box; background: #f4f7f5; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.page-heading { margin-bottom: 48rpx; }
.heading-title { display: block; color: #1c2b28; font-size: 42rpx; font-weight: 700; }
.heading-rule { width: 72rpx; height: 8rpx; margin-top: 18rpx; border-radius: 4rpx; background: #e26d4f; }
.state-text { min-height: 280rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 28rpx; }
.student-list { border-top: 2rpx solid #d7e0dc; }
.student-row { min-height: 132rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 2rpx solid #d7e0dc; }
.student-info { min-width: 0; }
.student-name, .student-account { display: block; }
.student-name { color: #1c2b28; font-size: 30rpx; font-weight: 600; }
.student-account { margin-top: 8rpx; color: #708078; font-size: 24rpx; }
.binding-actions { display: flex; align-items: center; gap: 18rpx; flex-shrink: 0; }
.binding-status { color: #708078; font-size: 25rpx; }
.binding-status.bound { color: #167c5a; }
.unbind-button { width: 104rpx; height: 60rpx; margin: 0; padding: 0; background: transparent; color: #b34f3b; font-size: 25rpx; }
.unbind-button::after { border: 2rpx solid #d9a69a; border-radius: 8rpx; }
</style>
