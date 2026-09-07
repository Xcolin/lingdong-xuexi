import { request } from './http';

export interface StudentAccountCancellationCandidate {
  studentId: string;
  studentName: string;
  studentAccount: string;
  organizationId: string;
  organizationName: string;
}

export interface StudentAccountCancellationInput {
  reason: string;
  confirmation: string;
}

function authorization(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

/** 读取当前机构管理员最近停用入学范围内可注销的学生账号。 */
export function listStudentAccountCancellationCandidates(
  accessToken: string
): Promise<StudentAccountCancellationCandidate[]> {
  return request<StudentAccountCancellationCandidate[]>(
    '/student-account-cancellations/candidates',
    { header: authorization(accessToken) }
  );
}

/** 永久注销学生账号并使全部学生登录凭据失效。 */
export function cancelStudentAccount(
  accessToken: string,
  studentId: string,
  input: StudentAccountCancellationInput
): Promise<void> {
  return request<void>(`/students/${studentId}/cancellations`, {
    method: 'POST',
    header: authorization(accessToken),
    data: input
  });
}
