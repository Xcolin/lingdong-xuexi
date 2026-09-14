import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { getOrganizationSession } from '@/session/organization-session';
import { requireManagedAccess } from '@/api/managed-review-access';
import { listManagedTaskReviews } from '@/api/managed-learning-task';
import { ApiError } from '@/api/http';
import type { ManagedIdentity } from '@/models/managed-review-access';
/** 总数来自当前审核人查询，页面隐藏和会话变化使旧响应失效。 */
export function useManagedReviewSummary(identity: ManagedIdentity) {
  const reviewEnabled=ref(false), reviewLoading=ref(false), reviewTotal=ref(0), reviewError=ref('');
  let revision=0;
  function reset() { revision++; reviewEnabled.value=false; reviewLoading.value=false; reviewTotal.value=0; reviewError.value=''; }
  async function refreshReviews() {
    reset(); const current=revision, token=getOrganizationSession()?.accessToken;
    if (!token) return;
    const active=()=>current===revision && getOrganizationSession()?.accessToken===token;
    reviewLoading.value=true;
    try {
      await requireManagedAccess(token,identity,'TASK_ASSIGNMENT_REVIEW');
      if (!active()) return;
      reviewEnabled.value=true;
      const page=await listManagedTaskReviews(token);
      if (active()) reviewTotal.value=page.total;
    } catch(error) {
      if (!active()) return;
      if (error instanceof ApiError && ([401,403,404].includes(error.statusCode)||error.code==='FEATURE_DISABLED')) reviewEnabled.value=false;
      else reviewError.value='待审核任务加载失败，请重试';
    } finally { if (active()) reviewLoading.value=false; }
  }
  function openReviews() { if(reviewEnabled.value) return uni.navigateTo({url:`/pages/managed-tasks/managed-tasks?identity=${identity}&review=true`}); }
  onShow(refreshReviews); onHide(reset); onUnload(reset);
  return {reviewEnabled,reviewLoading,reviewTotal,reviewError,refreshReviews,openReviews};
}

