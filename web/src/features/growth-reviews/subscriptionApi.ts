import { apiClient } from '../../api/http';

/** 本人订阅偏好不代表已获得微信消息授权，也不代表周报已送达。 */
export interface GrowthReviewSubscription {
  studentId: string;
  enabled: boolean;
  version: number;
}

export interface UpdateGrowthReviewSubscription {
  enabled: boolean;
  version: number;
}

export const growthReviewSubscriptionApi = {
  get(studentId: string): Promise<GrowthReviewSubscription> {
    return apiClient.get(`/growth-review-subscriptions/students/${encodeURIComponent(studentId)}`);
  },
  set(studentId: string, input: UpdateGrowthReviewSubscription): Promise<GrowthReviewSubscription> {
    // 申请人由服务端会话确定；仅发送允许修改的字段，冲突不在此重试。
    return apiClient.put(`/growth-review-subscriptions/students/${encodeURIComponent(studentId)}`, {
      enabled: input.enabled,
      version: input.version
    });
  }
};
