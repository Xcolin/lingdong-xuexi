import { apiClient } from './http';
export interface MenuNode {
  id: string; code: string; name: string; type: 'DIRECTORY' | 'PAGE' | 'BUTTON';
  parentId: string | null; route: string | null; icon: string | null; permissionCode: string | null;
  sortOrder: number; status: 'ENABLED' | 'DISABLED'; version: string;
}
export type CreateMenuInput = Omit<MenuNode, 'id' | 'version'>;
export type UpdateMenuInput = Omit<MenuNode, 'id'>;
export interface MenuOrderInput { parentId: string | null; ids: string[]; versions: Record<string, string>; }
export const menuApi = {
  list: () => apiClient.get<MenuNode[]>('/iam/menus'),
  current: () => apiClient.get<MenuNode[]>('/menus/current'),
  create: (input: CreateMenuInput) => apiClient.post<MenuNode>('/iam/menus', input),
  update: (id: string, input: UpdateMenuInput) => apiClient.put<MenuNode>(`/iam/menus/${id}`, input),
  reorder: (input: MenuOrderInput) => apiClient.put<void>('/iam/menus/order', input)
};
