import { apiClient } from './http';
export interface MenuNode {
  id: string; code: string; name: string; type: 'DIRECTORY' | 'PAGE' | 'BUTTON';
  parentId: string | null; route: string | null; icon: string | null; permissionCode: string | null;
  sortOrder: number; status: 'ENABLED' | 'DISABLED'; version: string; grantable?: boolean;
}
export type CreateMenuInput = Omit<MenuNode, 'id' | 'version'>;
export type UpdateMenuInput = Omit<MenuNode, 'id'>;
export interface MenuOrderInput { parentId: string | null; ids: string[]; versions: Record<string, string>; }
export interface MenuButtonInput { code: string; name: string; grantable: boolean; icon: string | null; sortOrder: number; status: MenuNode['status']; }
export const menuApi = {
  list: () => apiClient.get<MenuNode[]>('/iam/menus'),
  current: () => apiClient.get<MenuNode[]>('/menus/current'),
  create: (input: CreateMenuInput) => apiClient.post<MenuNode>('/iam/menus', input),
  update: (id: string, input: UpdateMenuInput) => apiClient.put<MenuNode>(`/iam/menus/${id}`, input),
  reorder: (input: MenuOrderInput) => apiClient.put<void>('/iam/menus/order', input),
  move: (id: string, input: { parentId: string | null; index: number; version: string }) => apiClient.put<MenuNode>(`/iam/menus/${id}/position`, input),
  createButtons: (pageId: string, input: MenuButtonInput[]) => apiClient.post<MenuNode[]>(`/iam/menus/${pageId}/buttons:batch`, input)
};
