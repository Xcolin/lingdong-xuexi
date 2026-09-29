<template>
 <view v-if="enabled||loading||error" class="summary">
  <text v-if="loading">正在加载待处理异常</text>
  <template v-else-if="error"><text>{{ error }}</text><button @tap="refresh">重试异常查询</button></template>
  <text v-else>{{ total?`待处理异常：${total} 项`:'暂无待处理异常' }}</text>
  <button v-if="enabled&&!loading" @tap="open">查看待处理异常</button>
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
<style scoped>.summary{padding:24rpx 40rpx;background:#fff;border-bottom:1px solid #dce5e0}.summary text{display:block;color:#284238}button{font-size:28rpx;margin-top:16rpx}</style>
