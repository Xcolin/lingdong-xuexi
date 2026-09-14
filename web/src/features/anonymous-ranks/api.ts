import { apiClient } from '../../api/http';
export interface Preference { enabled: boolean; version: number }
export interface RankRow { rank: number; points: number }
export interface StudentOption { studentId: string; studentName: string }
export interface ClassOption { classId: string; className: string }
export interface WithdrawalOption { studentId: string; classId: string; version: number }
const base = '/anonymous-ranks/students';
const path = (student: string, classroom: string) => `${base}/${encodeURIComponent(student)}/classes/${encodeURIComponent(classroom)}`;
export const rankApi = {
  withdrawals: () => apiClient.get<WithdrawalOption[]>('/anonymous-ranks/preferences'),
  students: () => apiClient.get<StudentOption[]>(base),
  classes: (student: string) => apiClient.get<ClassOption[]>(`${base}/${encodeURIComponent(student)}/classes`),
  preference: (student: string, classroom: string) => apiClient.get<Preference>(`${path(student, classroom)}/preference`),
  set: (student: string, classroom: string, value: Preference) => apiClient.put<Preference>(`${path(student, classroom)}/preference`, { enabled: value.enabled, version: value.version }),
  ranking: (student: string, classroom: string) => apiClient.get<RankRow[]>(path(student, classroom))
};
