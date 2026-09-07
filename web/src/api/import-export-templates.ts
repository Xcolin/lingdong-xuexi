import { apiClient } from './http';

export type ImportExportTemplateType = 'IMPORT' | 'EXPORT';
export type ImportExportTemplateStatus = 'ENABLED' | 'DISABLED';
export type ImportTemplateFieldDataType = 'TEXT' | 'INTEGER' | 'DECIMAL' | 'DATE' | 'DATETIME' | 'BOOLEAN';

export interface ImportTemplateFieldInput {
  fieldCode: string;
  columnName: string;
  dataType: ImportTemplateFieldDataType;
  required: boolean;
  maxLength?: number;
  dictionaryTypeCode?: string;
  sortOrder: number;
}

export interface ImportTemplateFieldRecord extends ImportTemplateFieldInput {
  id: string;
  templateId: string;
}

export interface ImportExportTemplateOption {
  code: string;
  name: string;
  defaultItem: boolean;
}

export interface ImportExportTemplateOptions {
  templateTypes: ImportExportTemplateOption[];
  modules: ImportExportTemplateOption[];
  statuses: ImportExportTemplateOption[];
}

export interface ImportExportTemplateRecord {
  id: string;
  templateName: string;
  templateType: ImportExportTemplateType;
  moduleCode: string;
  version: string;
  fileId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  defaultTemplate: boolean;
  status: ImportExportTemplateStatus;
  versionNo: number;
  createdAt: string;
  updatedAt: string;
}

export interface ImportExportTemplateQuery {
  templateName?: string;
  templateType?: ImportExportTemplateType;
  moduleCode?: string;
  status?: ImportExportTemplateStatus;
}

export interface CreateImportExportTemplateInput {
  templateName: string;
  templateType: ImportExportTemplateType;
  moduleCode: string;
  version: string;
  defaultTemplate: boolean;
  file: File;
  fields: ImportTemplateFieldInput[];
}

function queryString(query: ImportExportTemplateQuery): string {
  const parameters = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value) parameters.set(key, value);
  });
  const text = parameters.toString();
  return text ? `?${text}` : '';
}

export const importExportTemplateApi = {
  options(): Promise<ImportExportTemplateOptions> {
    return apiClient.get<ImportExportTemplateOptions>('/import-export-templates/options');
  },
  list(query: ImportExportTemplateQuery = {}): Promise<ImportExportTemplateRecord[]> {
    return apiClient.get<ImportExportTemplateRecord[]>(
      `/import-export-templates${queryString(query)}`
    );
  },
  create(input: CreateImportExportTemplateInput): Promise<ImportExportTemplateRecord> {
    const form = new FormData();
    form.set('templateName', input.templateName);
    form.set('templateType', input.templateType);
    form.set('moduleCode', input.moduleCode);
    form.set('version', input.version);
    form.set('defaultTemplate', String(input.defaultTemplate));
    form.set('fields', JSON.stringify(input.fields));
    form.set('file', input.file);
    return apiClient.postForm<ImportExportTemplateRecord>('/import-export-templates', form);
  },
  enable(id: string, versionNo: number): Promise<ImportExportTemplateRecord> {
    return apiClient.post<ImportExportTemplateRecord>(
      `/import-export-templates/${id}/enable`, { versionNo }
    );
  },
  disable(id: string, versionNo: number): Promise<ImportExportTemplateRecord> {
    return apiClient.post<ImportExportTemplateRecord>(
      `/import-export-templates/${id}/disable`, { versionNo }
    );
  },
  setDefault(id: string, versionNo: number): Promise<ImportExportTemplateRecord> {
    return apiClient.post<ImportExportTemplateRecord>(
      `/import-export-templates/${id}/default`, { versionNo }
    );
  },
  download(id: string): Promise<Blob> {
    return apiClient.getBlob(`/import-export-templates/${id}/download`);
  },
  fields(id: string): Promise<ImportTemplateFieldRecord[]> {
    return apiClient.get(`/import-export-templates/${id}/fields`);
  },
  replaceFields(id: string, versionNo: number, fields: ImportTemplateFieldInput[]): Promise<{ versionNo: number; fields: ImportTemplateFieldRecord[] }> {
    return apiClient.put(`/import-export-templates/${id}/fields`, { versionNo, fields });
  }
};
