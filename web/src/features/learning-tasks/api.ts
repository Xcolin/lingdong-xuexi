import { apiClient } from '../../api/http';
import type {
  BatchPublishResult,
  LearningTaskDetails,
  LearningTaskFilters,
  LearningTaskInput,
  LearningTaskPage,
  LearningTaskSourceType,
  OrganizationOption,
  PublishLearningTaskResult,
  StudentOption,
  StopRecurringTaskResult,
  TeacherOption
} from './types';

export const learningTaskApi = {
  list(filters: LearningTaskFilters): Promise<LearningTaskPage> {
    return apiClient.get<LearningTaskPage>(`/learning-tasks?${queryString(filters)}`);
  },
  findById(id: string): Promise<LearningTaskDetails> {
    return apiClient.get<LearningTaskDetails>(`/learning-tasks/${id}`);
  },
  create(input: LearningTaskInput): Promise<LearningTaskDetails> {
    return apiClient.post<LearningTaskDetails>('/learning-tasks', input);
  },
  update(id: string, input: LearningTaskInput): Promise<LearningTaskDetails> {
    return apiClient.patch<LearningTaskDetails>(`/learning-tasks/${id}`, input);
  },
  publish(id: string): Promise<PublishLearningTaskResult> {
    return apiClient.post<PublishLearningTaskResult>(`/learning-tasks/${id}/publish`, {});
  },
  batchPublish(taskIds: string[]): Promise<BatchPublishResult> {
    return apiClient.post<BatchPublishResult>('/learning-tasks/batch-publish', { taskIds });
  },
  stopRecurrence(id: string): Promise<StopRecurringTaskResult> {
    return apiClient.post<StopRecurringTaskResult>(`/learning-tasks/${id}/recurrence/stop`, {});
  },
  listOrganizations(
    sourceType: LearningTaskSourceType,
    organizationType?: string
  ): Promise<OrganizationOption[]> {
    return apiClient.get<OrganizationOption[]>(
      `/learning-task-options/organizations?${queryString({ sourceType, organizationType })}`
    );
  },
  listStudents(
    sourceType: LearningTaskSourceType,
    organizationId?: string,
    keyword?: string
  ): Promise<StudentOption[]> {
    return apiClient.get<StudentOption[]>(
      `/learning-task-options/students?${queryString({ sourceType, organizationId, keyword })}`
    );
  },
  listTeachers(classId?: string, keyword?: string): Promise<TeacherOption[]> {
    return apiClient.get<TeacherOption[]>(
      `/learning-task-options/teachers?${queryString({ classId, keyword })}`
    );
  }
};

function queryString(values: object): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  });
  return params.toString();
}
