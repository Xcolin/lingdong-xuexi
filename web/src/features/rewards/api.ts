import { apiClient } from '../../api/http';
import type {
  GrowthReward,
  GrowthRewardExchange,
  GrowthRewardExchangePage,
  GrowthRewardPage,
  RewardStudent,
  RewardStudentPage,
  SaveGrowthRewardInput
} from './types';

const STUDENT_PAGE_SIZE = 100;
const BUSINESS_PAGE_SIZE = 100;

/** 奖励管理接口只处理家长主关系孩子，雪花标识全程使用字符串。 */
export const rewardApi = {
  async listStudents(): Promise<RewardStudent[]> {
    const students: RewardStudent[] = [];
    let page = 1;
    let total = 0;
    do {
      const result = await apiClient.get<RewardStudentPage>(
        `/students?page=${page}&pageSize=${STUDENT_PAGE_SIZE}`
      );
      students.push(...result.items);
      total = result.total;
      page += 1;
    } while (students.length < total);
    return students;
  },
  async listRewards(studentId: string): Promise<GrowthReward[]> {
    return collectPages<GrowthReward>(async (page) => apiClient.get<GrowthRewardPage>(
      `/rewards/students/${studentId}?page=${page}&pageSize=${BUSINESS_PAGE_SIZE}`
    ));
  },
  createReward(studentId: string, input: SaveGrowthRewardInput): Promise<GrowthReward> {
    return apiClient.post<GrowthReward>(`/rewards/students/${studentId}`, input);
  },
  updateReward(rewardId: string, input: SaveGrowthRewardInput): Promise<GrowthReward> {
    return apiClient.patch<GrowthReward>(`/rewards/${rewardId}`, input);
  },
  deleteReward(rewardId: string): Promise<void> {
    return apiClient.delete(`/rewards/${rewardId}`);
  },
  async listExchanges(studentId: string): Promise<GrowthRewardExchange[]> {
    return collectPages<GrowthRewardExchange>(async (page) => apiClient.get<GrowthRewardExchangePage>(
      `/reward-exchanges/students/${studentId}?page=${page}&pageSize=${BUSINESS_PAGE_SIZE}`
    ));
  },
  approveExchange(exchangeId: string): Promise<GrowthRewardExchange> {
    return apiClient.post<GrowthRewardExchange>(`/reward-exchanges/${exchangeId}/approve`, {});
  },
  rejectExchange(exchangeId: string, rejectReason: string): Promise<GrowthRewardExchange> {
    return apiClient.post<GrowthRewardExchange>(
      `/reward-exchanges/${exchangeId}/reject`,
      { rejectReason }
    );
  },
  verifyExchange(exchangeId: string): Promise<GrowthRewardExchange> {
    return apiClient.post<GrowthRewardExchange>(`/reward-exchanges/${exchangeId}/verify`, {});
  }
};

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
