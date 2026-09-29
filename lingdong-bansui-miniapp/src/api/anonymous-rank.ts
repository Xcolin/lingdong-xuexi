import { request } from './http';
export interface RankStudent { studentId: string; studentName: string }
export interface RankClass { classId: string; className: string }
export interface RankPreference { enabled: boolean; version: number }
export interface RankRow { rank: number; points: number }
export interface RankWithdrawal { studentId: string; classId: string; version: number }
export interface RankUser { roleCodes: string[]; permissionCodes: string[]; clientType: string }
const header = (token: string) => ({ Authorization: `Bearer ${token}` });
const base = '/anonymous-ranks/students';
const path = (student: string, classroom: string) => `${base}/${encodeURIComponent(student)}/classes/${encodeURIComponent(classroom)}`;
/** 只传入独立家长会话，不回退到学生或机构身份。 */
export const rankApi = {
  me: (token: string) => request<RankUser>('/auth/me', { header: header(token) }),
  students: (token: string) => request<RankStudent[]>(base, { header: header(token) }),
  classes: (token: string, student: string) => request<RankClass[]>(`${base}/${encodeURIComponent(student)}/classes`, { header: header(token) }),
  preference: (token: string, student: string, classroom: string) => request<RankPreference>(`${path(student, classroom)}/preference`, { header: header(token) }),
  ranking: (token: string, student: string, classroom: string) => request<RankRow[]>(path(student, classroom), { header: header(token) }),
  set: (token: string, student: string, classroom: string, value: RankPreference) => request<RankPreference>(`${path(student, classroom)}/preference`, {
    header: header(token), method: 'PUT', data: { enabled: value.enabled, version: value.version }
  }),
  withdrawals: (token: string) => request<RankWithdrawal[]>('/anonymous-ranks/preferences', { header: header(token) })
};
