import { request } from './http';

export interface StudentWechatBindingSummary {
  studentId: string;
  studentName: string;
  studentAccountMasked: string;
  bound: boolean;
  boundAt: string | null;
}

/** 读取当前家长作为主监护人的学生微信绑定状态。 */
export function listStudentWechatBindings(accessToken: string): Promise<StudentWechatBindingSummary[]> {
  return request<StudentWechatBindingSummary[]>('/student-wechat-bindings', {
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

/** 使用固定二次确认语句解绑当前主监护学生的微信。 */
export function unbindStudentWechat(accessToken: string, studentId: string): Promise<void> {
  return request<void>(`/students/${studentId}/wechat-unbindings`, {
    method: 'POST',
    data: { confirmation: '确认解绑学生微信' },
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
