import { apiClient } from '../../api/http';
import type { PreviousDayTaskCopyPreview, TaskCopyBatchResult } from './types';

export const previousDayTaskCopyApi = {
  preview(studentId: string): Promise<PreviousDayTaskCopyPreview> {
    return apiClient.get<PreviousDayTaskCopyPreview>(
      `/students/${studentId}/previous-day-task-copy/preview`
    );
  },
  copy(studentId: string, confirmDuplicateTitles: boolean): Promise<TaskCopyBatchResult> {
    return apiClient.post<TaskCopyBatchResult>(
      `/students/${studentId}/previous-day-task-copy`,
      { confirmDuplicateTitles }
    );
  },
  retry(batchId: string, itemId: string): Promise<TaskCopyBatchResult> {
    return apiClient.post<TaskCopyBatchResult>(
      `/task-copy-batches/${batchId}/items/${itemId}/retry`,
      {}
    );
  }
};
