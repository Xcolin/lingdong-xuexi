import { apiClient } from './http';

export interface ActivityTrendPoint {
  date: string;
  activeStudents: number;
}

export interface ActivityTrends {
  items: ActivityTrendPoint[];
}

/** 机构管理员看板统计（R-002 活跃度趋势）；口径见 docs/design/09 第 2.7 节。 */
export const dashboardApi = {
  activityTrends(start?: string, end?: string): Promise<ActivityTrends> {
    const query = [
      start ? `start=${start}` : '',
      end ? `end=${end}` : ''
    ].filter(Boolean).join('&');
    return apiClient.get<ActivityTrends>(`/dashboard/activity-trends${query ? `?${query}` : ''}`);
  }
};
