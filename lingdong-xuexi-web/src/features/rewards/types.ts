export type GrowthRewardStatus = 'ONLINE' | 'OFFLINE';

export interface RewardStudent {
  id: string;
  studentName: string;
  gradeCode: string | null;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RewardStudentPage {
  items: RewardStudent[];
  page: number;
  pageSize: number;
  total: number;
}

export interface GrowthReward {
  id: string;
  studentId: string;
  rewardName: string;
  requiredPoints: number;
  description: string | null;
  expiresAt: string | null;
  status: GrowthRewardStatus;
  createdAt: string;
  updatedAt: string;
}

export interface GrowthRewardPage {
  items: GrowthReward[];
  page: number;
  pageSize: number;
  total: number;
}

export interface SaveGrowthRewardInput {
  rewardName: string;
  requiredPoints: number;
  description: string | null;
  expiresAt: string | null;
  status: GrowthRewardStatus;
}

export type GrowthRewardExchangeStatus =
  | 'PENDING_APPROVAL'
  | 'PENDING_VERIFICATION'
  | 'REJECTED'
  | 'AUTO_REJECTED'
  | 'EXPIRED'
  | 'VERIFIED';

export interface GrowthRewardExchange {
  id: string;
  rewardId: string;
  studentId: string;
  rewardName: string;
  requiredPoints: number;
  description: string | null;
  requestedAt: string;
  approvalDeadline: string;
  status: GrowthRewardExchangeStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectReason: string | null;
  verifiedBy: string | null;
  verifiedAt: string | null;
}

export interface GrowthRewardExchangePage {
  items: GrowthRewardExchange[];
  page: number;
  pageSize: number;
  total: number;
}
