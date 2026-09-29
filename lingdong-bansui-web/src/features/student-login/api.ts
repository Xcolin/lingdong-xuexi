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

export interface StudentWechatBindingSummary {
  studentId: string;
  studentName: string;
  studentAccountMasked: string;
  bound: boolean;
  boundAt: string | null;
}

export const studentLoginApi = {
  list(keyword?: string, page = 1, pageSize = 20): Promise<StudentDirectoryPage> {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    if (keyword) params.set('keyword', keyword);
    return apiClient.get<StudentDirectoryPage>(`/students?${params.toString()}`);
  },

  issueQrTicket(studentId: string): Promise<StudentLoginQrTicket> {
    return apiClient.post<StudentLoginQrTicket>(`/students/${studentId}/login-qr-tickets`, {});
  },

  listWechatBindings(): Promise<StudentWechatBindingSummary[]> {
    return apiClient.get<StudentWechatBindingSummary[]>('/student-wechat-bindings');
  },

  unbindWechat(studentId: string): Promise<void> {
    return apiClient.post<void>(`/students/${studentId}/wechat-unbindings`, {
      confirmation: '确认解绑学生微信'
    });
  }
};
