import { apiClient } from '../../api/http';
import type {
  GrowthPointAccount,
  GrowthPointCorrectionResult,
  GrowthPointLedgerPage,
  GrowthPointStudentOption
} from './types';

/** 家长积分页面只调用主关系学生范围内的只读接口。 */
export const growthPointApi = {
  listStudents(): Promise<GrowthPointStudentOption[]> {
    return apiClient.get<GrowthPointStudentOption[]>('/growth-points/students');
  },
  account(studentId: string): Promise<GrowthPointAccount> {
    return apiClient.get<GrowthPointAccount>(`/growth-points/students/${studentId}/account`);
  },
  ledgers(studentId: string, page: number, pageSize: number): Promise<GrowthPointLedgerPage> {
    return apiClient.get<GrowthPointLedgerPage>(
      `/growth-points/students/${studentId}/ledgers?page=${page}&pageSize=${pageSize}`
    );
  },
  correct(
    studentId: string,
    originalLedgerId: string,
    reason: string
  ): Promise<GrowthPointCorrectionResult> {
    return apiClient.post<GrowthPointCorrectionResult>(
      `/growth-points/students/${studentId}/corrections`,
      { originalLedgerId, reason }
    );
  }
};
