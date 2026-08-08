import { apiClient } from '../../api/http';
import type {
  AddGrowthReviewSupplementInput,
  GrowthReviewDetail,
  GrowthReviewPageData,
  GrowthReviewPeriodType,
  GrowthReviewStudentOption,
  GrowthReviewSupplement
} from './types';

/** 家长端仅调用主关系孩子范围内的成长复盘接口。 */
export const growthReviewApi = {
  listStudents(): Promise<GrowthReviewStudentOption[]> {
    return apiClient.get<GrowthReviewStudentOption[]>('/growth-points/students');
  },
  list(
    studentId: string,
    periodType: GrowthReviewPeriodType,
    page: number,
    pageSize: number
  ): Promise<GrowthReviewPageData> {
    return apiClient.get<GrowthReviewPageData>(
      `/growth-reviews/students/${studentId}?periodType=${periodType}&page=${page}&pageSize=${pageSize}`
    );
  },
  detail(studentId: string, reviewId: string): Promise<GrowthReviewDetail> {
    return apiClient.get<GrowthReviewDetail>(
      `/growth-reviews/students/${studentId}/${reviewId}`
    );
  },
  supplement(
    studentId: string,
    reviewId: string,
    input: AddGrowthReviewSupplementInput
  ): Promise<GrowthReviewSupplement> {
    return apiClient.post<GrowthReviewSupplement>(
      `/growth-reviews/students/${studentId}/${reviewId}/supplements`, input
    );
  }
};
