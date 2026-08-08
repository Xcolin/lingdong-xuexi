import { apiClient } from '../../api/http';
import type {
  LearningTaskTemplate,
  LearningTaskTemplateInput,
  LearningTaskTemplateOrderItem
} from './types';

export const taskTemplateApi = {
  list(): Promise<LearningTaskTemplate[]> {
    return apiClient.get<LearningTaskTemplate[]>('/task-templates');
  },
  create(input: LearningTaskTemplateInput): Promise<LearningTaskTemplate> {
    return apiClient.post<LearningTaskTemplate>('/task-templates', input);
  },
  update(
    templateId: string,
    versionNo: number,
    input: LearningTaskTemplateInput
  ): Promise<LearningTaskTemplate> {
    return apiClient.patch<LearningTaskTemplate>(`/task-templates/${templateId}`, {
      ...input,
      versionNo
    });
  },
  remove(templateId: string, versionNo: number): Promise<void> {
    return apiClient.delete(`/task-templates/${templateId}?versionNo=${versionNo}`);
  },
  reorder(items: LearningTaskTemplateOrderItem[]): Promise<LearningTaskTemplate[]> {
    return apiClient.put<LearningTaskTemplate[]>('/task-templates/personal-order', { items });
  }
};
