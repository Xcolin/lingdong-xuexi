import { apiClient } from '../../api/http';
import type { OrganizationOption, StudentOption, TeacherOption } from '../learning-tasks/types';

export interface StudentClassAssignment {
  studentId: string;
  classOrganizationId: string;
  status: string;
}

export interface StudentOrganizationRelationshipSummary {
  studentId: string;
  studentName: string;
  gradeCode: string | null;
  enrollmentOrganizationId: string;
  enrollmentOrganizationName: string;
  currentClassOrganizationId: string | null;
  currentClassOrganizationName: string | null;
}

export interface StudentOrganizationClassOption {
  id: string;
  name: string;
}

export interface StudentOrganizationChange {
  id: string;
  changeType: 'CLASS_ASSIGN' | 'CLASS_TRANSFER' | 'ENROLLMENT_DEACTIVATE';
  fromOrganizationId: string | null;
  toOrganizationId: string | null;
  reason: string;
  operatorUserId: string;
  occurredAt: string;
}

export interface StudentOrganizationRelationship {
  studentId: string;
  enrollmentOrganizationId: string;
  currentClassOrganizationId: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  changes: StudentOrganizationChange[];
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
  listRelationshipClasses(): Promise<StudentOrganizationClassOption[]> {
    return apiClient.get<StudentOrganizationClassOption[]>(
      '/students/organization-relationship-classes'
    );
  },
  listStudents(): Promise<StudentOption[]> {
    return apiClient.get<StudentOption[]>(
      '/learning-task-options/students?sourceType=ORGANIZATION'
    );
  },
  listRelationships(): Promise<StudentOrganizationRelationshipSummary[]> {
    return apiClient.get<StudentOrganizationRelationshipSummary[]>(
      '/students/organization-relationships'
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
  transferStudent(
    studentId: string,
    classOrganizationId: string,
    reason: string
  ): Promise<StudentOrganizationRelationship> {
    return apiClient.post<StudentOrganizationRelationship>(
      `/students/${studentId}/class-transfers`,
      { classOrganizationId, reason }
    );
  },
  deactivateStudent(
    studentId: string,
    organizationId: string,
    reason: string
  ): Promise<StudentOrganizationRelationship> {
    return apiClient.post<StudentOrganizationRelationship>(
      `/students/${studentId}/organization-deactivations`,
      { organizationId, reason }
    );
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
