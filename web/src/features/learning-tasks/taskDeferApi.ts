import { apiClient } from '../../api/http';
import type { ManagedDeferCandidatePage, TaskDeferResult } from './types';

export const taskDeferApi = {
  list(page = 1, pageSize = 20): Promise<ManagedDeferCandidatePage> {
    return apiClient.get<ManagedDeferCandidatePage>(
      `/managed-task-assignments?page=${page}&pageSize=${pageSize}`
    );
  },
  defer(assignmentId: string, targetDate: string): Promise<TaskDeferResult> {
    return apiClient.post<TaskDeferResult>(
      `/managed-task-assignments/${encodeURIComponent(assignmentId)}/defer`,
      { targetDate }
    );
  }
};
