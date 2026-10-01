<template>
 <view v-if="enabled||loading||error" class="summary">
  <view class="summary-info">
    <text class="summary-title">异常报备</text>
    <text class="summary-count">
      <template v-if="loading">正在加载待处理异常…</template>
      <template v-else-if="error">{{ error }}</template>
      <template v-else>{{ total?`待处理异常：${total} 项`:'暂无待处理异常' }}</template>
    </text>
  </view>
  <button v-if="error" class="summary-button ghost" @tap="refresh">重试</button>
  <button v-if="enabled&&!loading" class="summary-button" @tap="open">查看</button>
 </view>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import {getOrganizationSession} from '@/session/organization-session';
import {requireExceptionAccess} from '@/api/exception-access';
import {listExceptionReports} from '@/api/exception-report';
import {ApiError} from '@/api/http';
const enabled=ref(false),loading=ref(false),error=ref(''),total=ref(0);let epoch=0;
function reset(){epoch++;enabled.value=false;loading.value=false;error.value='';total.value=0;}
async function refresh(){
 reset();const n=epoch,token=getOrganizationSession()?.accessToken;if(!token)return;
 const current=()=>n===epoch&&getOrganizationSession()?.accessToken===token;
 loading.value=true;
 try{await requireExceptionAccess(token,'organization');if(!current())return;
 enabled.value=true;const result=await listExceptionReports(token,1,1,'SUBMITTED');if(current())total.value=result.total;
 }catch(cause){if(!current())return;if(cause instanceof ApiError&&[401,403,404].includes(cause.statusCode))enabled.value=false;else error.value='待处理异常加载失败，请重试';}
 finally{if(current())loading.value=false;}
}
function open(){if(enabled.value)return uni.navigateTo({url:'/pages/exception-reports/exception-reports?identity=organization&status=SUBMITTED'});}
onShow(refresh);onHide(reset);onUnload(reset);
</script>
<style scoped>
.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 32rpx;
  padding: 30rpx;
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(21, 54, 43, 0.06);
}
.summary-info { min-width: 0; flex: 1; }
.summary-title, .summary-count { display: block; }
.summary-title { color: #1c2b28; font-size: 34rpx; font-weight: 700; }
.summary-count { margin-top: 10rpx; color: #8a9992; font-size: 24rpx; overflow-wrap: anywhere; }
.summary-button {
  width: 132rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  border-radius: 999rpx;
  background: #e26d4f;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 600;
  flex-shrink: 0;
}
.summary-button::after { border: 0; }
.summary-button.ghost { background: transparent; border: 2rpx solid #cfdcd6; color: #4b5c55; }
</style>
