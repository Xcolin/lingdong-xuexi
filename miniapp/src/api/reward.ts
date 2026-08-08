import { request } from './http';
import { getStudentSession } from '@/session/student-session';

export interface RewardAccountSummary {
  studentId: string;
  availablePoints: number;
  updatedAt: string;
}

export interface StudentReward {
  id: string;
  studentId: string;
  rewardName: string;
  requiredPoints: number;
  description: string | null;
  expiresAt: string | null;
  status: 'ONLINE';
  createdAt: string;
  updatedAt: string;
}

export interface StudentRewardPage {
  items: StudentReward[];
  page: number;
  pageSize: number;
  total: number;
}

export type RewardExchangeStatus =
  | 'PENDING_APPROVAL'
  | 'PENDING_VERIFICATION'
  | 'REJECTED'
  | 'AUTO_REJECTED'
  | 'EXPIRED'
  | 'VERIFIED';

export interface StudentRewardExchange {
  id: string;
  rewardId: string;
  studentId: string;
  rewardName: string;
  requiredPoints: number;
  description: string | null;
  requestedAt: string;
  approvalDeadline: string;
  status: RewardExchangeStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectReason: string | null;
  verifiedBy: string | null;
  verifiedAt: string | null;
}

export interface StudentRewardExchangePage {
  items: StudentRewardExchange[];
  page: number;
  pageSize: number;
  total: number;
}

/** 奖励兑换接口只从当前小程序会话解析学生本人。 */
export function getMyRewardAccountSummary(): Promise<RewardAccountSummary> {
  return authenticatedRequest<RewardAccountSummary>('/rewards/me/summary');
}

export function listMyRewards(): Promise<StudentReward[]> {
  return collectPages<StudentReward>((page) => authenticatedRequest<StudentRewardPage>(
    `/rewards/me?page=${page}&pageSize=100`
  ));
}

export function applyRewardExchange(rewardId: string): Promise<StudentRewardExchange> {
  return authenticatedRequest<StudentRewardExchange>('/reward-exchanges', {
    method: 'POST',
    data: { rewardId }
  });
}

export function listMyRewardExchanges(): Promise<StudentRewardExchange[]> {
  return collectPages<StudentRewardExchange>((page) => authenticatedRequest<StudentRewardExchangePage>(
    `/reward-exchanges/me?page=${page}&pageSize=100`
  ));
}

async function collectPages<T>(loadPage: (page: number) => Promise<{
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}>): Promise<T[]> {
  const items: T[] = [];
  let page = 1;
  let total = 0;
  do {
    const result = await loadPage(page);
    items.push(...result.items);
    total = result.total;
    page += 1;
  } while (items.length < total);
  return items;
}

function authenticatedRequest<T>(
  path: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}
): Promise<T> {
  const session = getStudentSession();
  if (!session) {
    return Promise.reject(new Error('学生登录状态已失效'));
  }
  return request<T>(path, {
    ...options,
    header: { Authorization: `Bearer ${session.accessToken}` }
  });
}
