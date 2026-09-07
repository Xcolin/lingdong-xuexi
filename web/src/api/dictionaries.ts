import { apiClient } from './http';

export type DictionaryStatus = 'ENABLED' | 'DISABLED';
export interface DictionaryType { id: string; code: string; name: string; status: DictionaryStatus; sortOrder: number; }
export interface DictionaryItem { id: string; typeId: string; code: string; name: string; sortOrder: number; defaultItem: boolean; status: DictionaryStatus; }
export interface CreateDictionaryTypeInput { code: string; name: string; sortOrder: number; }
export interface UpdateDictionaryTypeInput { name: string; sortOrder: number; status: DictionaryStatus; }
export interface CreateDictionaryItemInput { code: string; name: string; sortOrder: number; defaultItem: boolean; }
export interface UpdateDictionaryItemInput { name: string; sortOrder: number; status: DictionaryStatus; defaultItem: boolean; }

export const dictionaryApi = {
  listTypes: (): Promise<DictionaryType[]> => apiClient.get('/dictionaries/types'),
  createType: (input: CreateDictionaryTypeInput): Promise<DictionaryType> => apiClient.post('/dictionaries/types', input),
  updateType: (typeId: string, input: UpdateDictionaryTypeInput): Promise<DictionaryType> => apiClient.put(`/dictionaries/types/${typeId}`, input),
  listItems: (typeId: string): Promise<DictionaryItem[]> => apiClient.get(`/dictionaries/types/${typeId}/items`),
  createItem: (typeId: string, input: CreateDictionaryItemInput): Promise<DictionaryItem> => apiClient.post(`/dictionaries/types/${typeId}/items`, input),
  updateItem: (itemId: string, input: UpdateDictionaryItemInput): Promise<DictionaryItem> => apiClient.put(`/dictionaries/items/${itemId}`, input)
};

