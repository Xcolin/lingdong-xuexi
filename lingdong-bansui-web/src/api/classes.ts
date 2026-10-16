import { apiClient } from './http';

export interface ClassOrganization {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  typeCode: 'SCHOOL' | 'TRAINING_INSTITUTION' | 'CAMPUS' | 'GRADE' | 'CLASS';
  campusOrganizationId?: string | null;
  campusName?: string | null;
  namePath?: string;
  schoolOrganizationId?: string;
  schoolName?: string;
  gradeOrganizationId?: string | null;
  gradeName?: string | null;
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
  effectiveStatus: 'ENABLED' | 'DISABLED';
  versionNo: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateClassInput {
  schoolOrganizationId?: string;
  gradeOrganizationId: string;
  name: string;
  sortOrder: number;
}

export interface CreateGradeInput {
  schoolOrganizationId: string;
  campusOrganizationId?: string;
  name: string;
  sortOrder: number;
}

export interface UpdateClassInput {
  name: string;
  sortOrder: number;
  versionNo: number;
}

export const classApi = {
  lookup(permission: string, kind: 'SCHOOL' | 'CAMPUS' | 'GRADE' | 'CLASS', filters: {schoolOrganizationId?: string;campusOrganizationId?: string;gradeOrganizationId?: string} = {}): Promise<ClassOrganization[]> {
    const parameters = new URLSearchParams({permission,kind});
    Object.entries(filters).forEach(([key,value])=>{if(value)parameters.set(key,value);});
    return apiClient.get(`/classes/lookup?${parameters}`);
  },
  listCampuses(schoolOrganizationId?: string): Promise<ClassOrganization[]> { return apiClient.get('/classes/campuses' + (schoolOrganizationId ? `?schoolOrganizationId=${encodeURIComponent(schoolOrganizationId)}` : '')); },
  createCampus(input: Omit<CreateGradeInput, 'campusOrganizationId'>): Promise<ClassOrganization> { return apiClient.post('/classes/campuses', input); },
  updateCampus(id: string, input: UpdateClassInput): Promise<ClassOrganization> { return apiClient.put(`/classes/campuses/${id}`, input); },
  setCampusStatus(id: string, enabled: boolean, versionNo: number): Promise<ClassOrganization> { return apiClient.post(`/classes/campuses/${id}/${enabled ? 'enable' : 'disable'}`, {versionNo}); },
  listGrades(schoolOrganizationId?: string, campusOrganizationId?: string): Promise<ClassOrganization[]> {
    const parameters=new URLSearchParams();
    if(schoolOrganizationId)parameters.set('schoolOrganizationId',schoolOrganizationId);
    if(campusOrganizationId)parameters.set('campusOrganizationId',campusOrganizationId);
    return apiClient.get('/classes/grades' + (parameters.size ? `?${parameters}` : ''));
  },
  createGrade(input: CreateGradeInput): Promise<ClassOrganization> { return apiClient.post('/classes/grades', input); },
  updateGrade(id: string, input: UpdateClassInput): Promise<ClassOrganization> { return apiClient.put(`/classes/grades/${id}`, input); },
  setGradeStatus(id: string, enabled: boolean, versionNo: number): Promise<ClassOrganization> { return apiClient.post(`/classes/grades/${id}/${enabled ? 'enable' : 'disable'}`, { versionNo }); },
  assignGrade(id: string, gradeOrganizationId: string, versionNo: number): Promise<ClassOrganization> { return apiClient.post(`/classes/${id}/assign-grade`, { gradeOrganizationId, versionNo }); },
  listSchools(): Promise<ClassOrganization[]> {
    return apiClient.get<ClassOrganization[]>('/classes/schools');
  },
  listClasses(campusOrganizationId?: string): Promise<ClassOrganization[]> {
    return apiClient.get<ClassOrganization[]>('/classes' + (campusOrganizationId ? `?campusOrganizationId=${encodeURIComponent(campusOrganizationId)}` : ''));
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

export function isInstitutionRootType(code?:string):boolean{return code==='SCHOOL'||code==='TRAINING_INSTITUTION';}
