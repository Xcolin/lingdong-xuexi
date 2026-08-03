import { apiClient } from './http';

export interface OrganizationType {
  id: string;
  code: string;
  name: string;
  builtIn: boolean;
  status: 'ENABLED' | 'DISABLED';
  sortOrder: number;
}

export interface OrganizationNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  typeCode: string;
  path: string;
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
  children: OrganizationNode[];
}

export interface CreateOrganizationTypeInput {
  code: string;
  name: string;
  sortOrder: number;
}

export interface CreateOrganizationInput {
  code: string;
  name: string;
  typeCode: string;
  parentId?: string;
  sortOrder: number;
}

export const organizationApi = {
  listTypes(): Promise<OrganizationType[]> {
    return apiClient.get<OrganizationType[]>('/organization-types');
  },
  createType(input: CreateOrganizationTypeInput): Promise<OrganizationType> {
    return apiClient.post<OrganizationType>('/organization-types', input);
  },
  listTree(): Promise<OrganizationNode[]> {
    return apiClient.get<OrganizationNode[]>('/organizations');
  },
  createOrganization(input: CreateOrganizationInput): Promise<OrganizationNode> {
    return apiClient.post<OrganizationNode>('/organizations', input);
  }
};
