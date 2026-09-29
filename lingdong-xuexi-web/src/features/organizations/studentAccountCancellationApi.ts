import { apiClient } from '../../api/http';

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

export const studentAccountCancellationApi = {
  listCandidates(): Promise<StudentAccountCancellationCandidate[]> {
    return apiClient.get<StudentAccountCancellationCandidate[]>(
      '/student-account-cancellations/candidates'
    );
  },
  cancel(studentId: string, input: StudentAccountCancellationInput): Promise<void> {
    return apiClient.post<void>(`/students/${studentId}/cancellations`, input);
  }
};
