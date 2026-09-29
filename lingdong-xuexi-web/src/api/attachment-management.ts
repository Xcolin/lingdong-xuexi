import { apiClient } from './http';

export type AttachmentRuleStatus = 'ENABLED' | 'DISABLED';
export type AttachmentFileStatus = 'UPLOADING' | 'AVAILABLE' | 'RETIRED';
export type AttachmentRelationStatus = 'ACTIVE' | 'RELEASED';

export interface AttachmentRuleRecord {
  id: string;
  moduleCode: string;
  fileCategory: string;
  ruleName: string;
  allowedExtensions: string[];
  maxFileSizeBytes: number;
  maxBatchCount: number;
  previewEnabled: boolean;
  status: AttachmentRuleStatus;
  versionNo: number;
}

export interface AttachmentFileLedgerRecord {
  id: string;
  originalName: string;
  extension: string;
  contentType: string;
  sizeBytes: number;
  uploaderId: string;
  uploaderName: string;
  moduleCode: string;
  fileCategory: string;
  contentSha256Present: boolean;
  status: AttachmentFileStatus;
  uploadedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AttachmentRelationLedgerRecord {
  id: string;
  fileId: string;
  moduleCode: string;
  businessId: string;
  relationType: string;
  visibleScope: string;
  status: AttachmentRelationStatus;
  createdAt: string;
  releasedAt: string | null;
}

export interface AttachmentRuleQuery {
  ruleName?: string;
  moduleCode?: string;
  fileCategory?: string;
  status?: AttachmentRuleStatus;
}

export interface AttachmentFileQuery {
  originalName?: string;
  moduleCode?: string;
  fileCategory?: string;
  status?: AttachmentFileStatus;
  uploaderId?: string;
  createdFrom?: string;
  createdTo?: string;
}

export interface CreateAttachmentRuleInput {
  moduleCode: string;
  fileCategory: string;
  ruleName: string;
  allowedExtensions: string[];
  maxFileSizeBytes: number;
  maxBatchCount: number;
  previewEnabled: boolean;
}

export interface UpdateAttachmentRuleInput {
  ruleName: string;
  allowedExtensions: string[];
  maxFileSizeBytes: number;
  maxBatchCount: number;
  previewEnabled: boolean;
  versionNo: number;
}

function queryString(query: Record<string, string | undefined>): string {
  const parameters = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => { if (value) parameters.set(key, value); });
  const text = parameters.toString();
  return text ? `?${text}` : '';
}

export const attachmentManagementApi = {
  listRules(query: AttachmentRuleQuery = {}): Promise<AttachmentRuleRecord[]> {
    return apiClient.get<AttachmentRuleRecord[]>(`/attachment-management/rules${queryString({
      ruleName: query.ruleName, moduleCode: query.moduleCode,
      fileCategory: query.fileCategory, status: query.status
    })}`);
  },
  createRule(input: CreateAttachmentRuleInput): Promise<AttachmentRuleRecord> {
    return apiClient.post<AttachmentRuleRecord>('/attachment-management/rules', input);
  },
  updateRule(id: string, input: UpdateAttachmentRuleInput): Promise<AttachmentRuleRecord> {
    return apiClient.put<AttachmentRuleRecord>(`/attachment-management/rules/${id}`, input);
  },
  enableRule(id: string, versionNo: number): Promise<AttachmentRuleRecord> {
    return apiClient.post<AttachmentRuleRecord>(`/attachment-management/rules/${id}/enable`, { versionNo });
  },
  disableRule(id: string, versionNo: number): Promise<AttachmentRuleRecord> {
    return apiClient.post<AttachmentRuleRecord>(`/attachment-management/rules/${id}/disable`, { versionNo });
  },
  listFiles(query: AttachmentFileQuery = {}): Promise<AttachmentFileLedgerRecord[]> {
    return apiClient.get<AttachmentFileLedgerRecord[]>(`/attachment-management/files${queryString({
      originalName: query.originalName, moduleCode: query.moduleCode, fileCategory: query.fileCategory,
      status: query.status, uploaderId: query.uploaderId,
      createdFrom: query.createdFrom, createdTo: query.createdTo
    })}`);
  },
  listRelations(fileId: string): Promise<AttachmentRelationLedgerRecord[]> {
    return apiClient.get<AttachmentRelationLedgerRecord[]>(`/attachment-management/files/${fileId}/relations`);
  }
};
