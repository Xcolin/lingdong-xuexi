export type GrowthPointChangeType = 'TASK_REWARD' | 'REDEMPTION' | 'DORMANCY_CLEAR' | 'CORRECTION';
export type GrowthPointSourceType = 'FAMILY' | 'ORGANIZATION' | 'TEACHER';

export interface GrowthPointStudentOption {
  studentId: string;
  studentName: string;
}

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

export interface GrowthPointCorrectionResult {
  studentId: string;
  assignmentId: string;
  originalLedgerId: string;
  correctionLedgerId: string;
  correctedPoints: number;
  totalPoints: number;
  availablePoints: number;
  currentStatus: 'PENDING_REVIEW';
  occurredAt: string;
}
