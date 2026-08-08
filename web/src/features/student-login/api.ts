import { apiClient } from '../../api/http';

export interface StudentDirectoryItem {
  id: string;
  studentName: string;
  gradeCode: string | null;
  status: 'ENABLED' | 'DISABLED';
  createdAt: string;
  updatedAt: string;
}

export interface StudentDirectoryPage {
  items: StudentDirectoryItem[];
  page: number;
  pageSize: number;
  total: number;
}

export interface StudentLoginQrTicket {
  ticketId: string;
  qrContent: string;
  expiresAt: string;
}

export const studentLoginApi = {
  list(keyword?: string, page = 1, pageSize = 20): Promise<StudentDirectoryPage> {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    if (keyword) params.set('keyword', keyword);
    return apiClient.get<StudentDirectoryPage>(`/students?${params.toString()}`);
  },

  issueQrTicket(studentId: string): Promise<StudentLoginQrTicket> {
    return apiClient.post<StudentLoginQrTicket>(`/students/${studentId}/login-qr-tickets`, {});
  }
};
