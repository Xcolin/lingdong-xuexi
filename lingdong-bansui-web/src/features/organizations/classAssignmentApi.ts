import {classApi} from '../../api/classes';
import {teachersApi} from '../../api/teachers';
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
  institutionTypeCode?:string;
  currentClassOrganizationIds?:string[];
  currentClassOrganizationNames?:string[];
  enrollmentOrganizationId: string;
  enrollmentOrganizationName: string;
  currentClassOrganizationId: string | null;
  currentClassOrganizationName: string | null;
}

export interface StudentOrganizationClassOption {
  schoolOrganizationId?:string|null;
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
  currentClassOrganizationIds?:string[];
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

interface TeacherOptionResponse {
  organizationNames?: string[];
  userId: string;
  displayName: string;
  classOrganizationIds: string[];
}

export const classAssignmentApi = {
  listClassMembers(classId: string): Promise<StudentOption[]> { return apiClient.get(`/students/class-members?classOrganizationId=${encodeURIComponent(classId)}`); },
  async listClasses(schoolId?:string,permission='STUDENT_CLASS_ASSIGN'): Promise<OrganizationOption[]> {
    const classes=await classApi.lookup(permission,'CLASS',{schoolOrganizationId:schoolId});
    return classes.filter(item=>item.status==='ENABLED'&&item.effectiveStatus==='ENABLED'&&(!schoolId||item.schoolOrganizationId===schoolId)).map(item=>({id:item.id,name:item.namePath??item.name,organizationType:'CLASS',parentId:item.parentId,organizationPath:'',schoolOrganizationId:item.schoolOrganizationId,schoolName:item.schoolName}));
  },
  listRelationshipClasses(schoolId?:string): Promise<StudentOrganizationClassOption[]> {
    return apiClient.get('/students/organization-relationship-classes'+(schoolId?`?schoolOrganizationId=${encodeURIComponent(schoolId)}`:''));
  },
  listStudents(classOrganizationId?: string): Promise<StudentOption[]> {
    return apiClient.get<StudentOption[]>(
      '/students/class-assignment-options' + (classOrganizationId ? `?classOrganizationId=${encodeURIComponent(classOrganizationId)}` : '')
    );
  },
  listRelationships(): Promise<StudentOrganizationRelationshipSummary[]> {
    return apiClient.get<StudentOrganizationRelationshipSummary[]>(
      '/students/organization-relationships'
    );
  },
  async listTeachers(classId: string): Promise<TeacherOption[]> {
    const teachers: TeacherOption[]=[];
    let page=1,total=0;
    do {
      const result=await teachersApi.lookup('TEACHER_CLASS_ASSIGN',{classOrganizationId:classId,page,pageSize:100});
      teachers.push(...result.items.map(item=>({userId:item.id,displayName:item.displayName,classIds:item.classOrganizationIds,organizationNames:[item.schoolName]})));
      total=result.total;page++;
    }while(teachers.length<total);
    return teachers;
  },
  assignStudent(studentId: string, classOrganizationId: string): Promise<StudentClassAssignment> {
    return apiClient.put<StudentClassAssignment>(`/students/${studentId}/class`, { classOrganizationId });
  },
  deactivateCourse(studentId:string,classOrganizationId:string,reason:string){return apiClient.post<StudentOrganizationRelationship>(`/students/${studentId}/class-deactivations`,{classOrganizationId,reason});},
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
