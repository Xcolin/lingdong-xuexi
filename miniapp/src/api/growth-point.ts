import { request } from './http';
import { getStudentSession } from '@/session/student-session';

export type GrowthPointChangeType = 'TASK_REWARD' | 'REDEMPTION' | 'DORMANCY_CLEAR' | 'CORRECTION';
export type GrowthPointSourceType = 'FAMILY' | 'ORGANIZATION' | 'TEACHER';

export interface GrowthPointAccount {
  studentId: string;
  studentName: string;
  totalPoints: number;
  availablePoints: number;
  updatedAt: string;
}

export interface GrowthPointLedger {
  id: string;
  changeType: GrowthPointChangeType;
  amount: number;
  availableDelta: number;
  sourceAssignmentId: string | null;
  sourceExchangeId: string | null;
  sourceTaskId?: string | null;
  basePointsSnapshot?: number | null;
  decayPercent?: number | null;
  streakDays?: number | null;
  decayRuleId?: string | null;
  sourceType: GrowthPointSourceType | null;
  sourceOrganizationId: string | null;
  sourceOrganizationName: string | null;
  taskTitle: string | null;
  reviewerUserId: string | null;
  reviewerDisplayName: string | null;
  occurredAt: string;
  remark: string | null;
  correctionOfId: string | null;
  correctionLedgerId: string | null;
  correctionDeadline: string | null;
  correctable: boolean;
}

export interface GrowthPointLedgerPage {
  items: GrowthPointLedger[];
  page: number;
  pageSize: number;
  total: number;
}

/** 学生积分接口只从当前小程序会话解析本人身份。 */
export function getMyGrowthPointAccount(): Promise<GrowthPointAccount> {
  return authenticatedRequest<GrowthPointAccount>('/growth-points/me/account');
}

export function listMyGrowthPointLedgers(
  page: number,
  pageSize: number
): Promise<GrowthPointLedgerPage> {
  return authenticatedRequest<GrowthPointLedgerPage>(
    `/growth-points/me/ledgers?page=${page}&pageSize=${pageSize}`
  );
}

function authenticatedRequest<T>(path: string): Promise<T> {
  const session = getStudentSession();
  if (!session) {
    return Promise.reject(new Error('学生登录状态已失效'));
  }
  return request<T>(path, {
    header: { Authorization: `Bearer ${session.accessToken}` }
  });
}
