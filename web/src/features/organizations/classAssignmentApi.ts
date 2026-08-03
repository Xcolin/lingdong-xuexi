import { apiClient } from '../../api/http';
import type { OrganizationOption, StudentOption, TeacherOption } from '../learning-tasks/types';

export interface StudentClassAssignment {
  studentId: string;
  classOrganizationId: string;
  status: string;
}

export interface TeacherClassAssignment {
  id: string;
  teacherUserId: string;
  classOrganizationId: string;
  status: 'ACTIVE' | 'INACTIVE';
  effectiveFrom: string;
  effectiveTo: string | null;
}

export const classAssignmentApi = {
  listClasses(): Promise<OrganizationOption[]> {
    return apiClient.get<OrganizationOption[]>(
      '/learning-task-options/organizations?sourceType=ORGANIZATION&organizationType=CLASS'
    );
  },
  listStudents(): Promise<StudentOption[]> {
    return apiClient.get<StudentOption[]>(
      '/learning-task-options/students?sourceType=ORGANIZATION'
    );
  },
  listTeachers(classId: string): Promise<TeacherOption[]> {
    return apiClient.get<TeacherOption[]>(
      `/learning-task-options/teachers?classId=${encodeURIComponent(classId)}`
    );
  },
  assignStudent(studentId: string, classOrganizationId: string): Promise<StudentClassAssignment> {
    return apiClient.put<StudentClassAssignment>(`/students/${studentId}/class`, { classOrganizationId });
  },
  assignTeacher(teacherUserId: string, classOrganizationId: string): Promise<TeacherClassAssignment> {
    return apiClient.put<TeacherClassAssignment>(
      `/teachers/${teacherUserId}/classes/${classOrganizationId}`, {}
    );
  },
  removeTeacher(teacherUserId: string, classOrganizationId: string): Promise<void> {
    return apiClient.delete(`/teachers/${teacherUserId}/classes/${classOrganizationId}`);
  }
};
