import { apiClient } from './http';

export interface ClassOrganization {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  typeCode: 'SCHOOL' | 'CLASS';
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
  effectiveStatus: 'ENABLED' | 'DISABLED';
  versionNo: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateClassInput {
  schoolOrganizationId: string;
  name: string;
  sortOrder: number;
}

export interface UpdateClassInput {
  name: string;
  sortOrder: number;
  versionNo: number;
}

export const classApi = {
  listSchools(): Promise<ClassOrganization[]> {
    return apiClient.get<ClassOrganization[]>('/classes/schools');
  },
  listClasses(): Promise<ClassOrganization[]> {
    return apiClient.get<ClassOrganization[]>('/classes');
  },
  createClass(input: CreateClassInput): Promise<ClassOrganization> {
    return apiClient.post<ClassOrganization>('/classes', input);
  },
  updateClass(classId: string, input: UpdateClassInput): Promise<ClassOrganization> {
    return apiClient.put<ClassOrganization>(`/classes/${classId}`, input);
  },
  disableClass(classId: string, versionNo: number): Promise<ClassOrganization> {
    return apiClient.post<ClassOrganization>(`/classes/${classId}/disable`, { versionNo });
  },
  enableClass(classId: string, versionNo: number): Promise<ClassOrganization> {
    return apiClient.post<ClassOrganization>(`/classes/${classId}/enable`, { versionNo });
  }
};
