<template>
  <view class="prototype-workspace page-shell">
    <PrototypePageHeader title="微信绑定管理" icon="shield"/>

    <view v-if="loading" class="state-text">正在加载</view>
    <view v-else-if="items.length === 0" class="state-text">暂无可管理学生</view>
    <view v-else class="student-list">
      <view v-for="item in items" :key="item.studentId" class="student-row">
        <view class="student-info">
          <text class="student-name">{{ item.studentName }}</text>
          <text class="student-account">{{ item.studentAccountMasked }}</text><text v-if="item.boundAt" class="binding-time">绑定时间 {{ item.boundAt }}</text>
        </view>
        <view class="binding-actions">
          <text :class="['binding-status', { bound: item.bound }]">
            {{ item.bound ? '已绑定' : '未绑定' }}
          </text>
          <button v-if="item.bound" class="unbind-button" :disabled="submittingId === item.studentId"
                  @tap="confirmUnbind(item)">
            解除绑定
          </button>
        </view>
      </view>
      <view class="binding-notice">解除绑定后，将无法使用微信快捷登录，不影响已记录的成长数据。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import PrototypePageHeader from '@/components/PrototypePageHeader.vue';
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

@import "@/styles/workspace-prototype.scss";
.prototype-workspace{background:#f6f8f5;padding:28rpx;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;}.student-row,.invitation-form{background:#fffefa;border:1rpx solid #e3eadf;border-radius:24rpx;padding:26rpx;}.title-rule,.heading-rule{display:none;}.field-input{box-sizing:border-box;height:88rpx;border:1rpx solid #d9e4d4;background:#fcfdfb;border-radius:14rpx;font-size:26rpx;}
.secondary-page,.prototype-workspace{background:#f5f9f5!important;padding:24rpx 28rpx 70rpx!important;color:#1a382a;}
.student-row{display:block;background:white;border:1rpx solid #e3ebe5;border-radius:24rpx;padding:28rpx;margin-bottom:24rpx;}.student-info{background:#e9f8ed;border-radius:20rpx;padding:24rpx;}.student-name{font-size:30rpx;color:#143f30;}.student-account,.binding-time{display:block;margin-top:14rpx;color:#7a877e;font-size:24rpx;}.binding-actions{display:flex;align-items:center;justify-content:space-between;margin-top:24rpx;}.binding-status.bound{background:#ddf9df;color:#287b39;border-radius:12rpx;}.unbind-button{background:#fadddd;color:#c7564d;border-radius:18rpx;padding:18rpx 28rpx;font-size:27rpx;}.binding-notice{background:#fff2df;color:#ad6841;border-radius:20rpx;padding:26rpx;font-size:25rpx;line-height:1.7;}
</style>
