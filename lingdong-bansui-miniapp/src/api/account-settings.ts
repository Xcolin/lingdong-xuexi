import { request } from './http';
export interface AccountSettings { mobile: string | null; email: string | null; wechatBound: boolean; hasPassword: boolean; canManageUsers?: boolean; passwordChangeRequired: boolean; }
export type ContactChannel = 'SMS' | 'EMAIL';
export function accountSettingsRequest<T>(token: string, suffix = '', data?: Record<string, unknown>): Promise<T> {
 return request<T>('/auth/account-settings'+suffix, {method: data ? 'POST' : 'GET',data,header:{Authorization:`Bearer ${token}`}});
}
export function changeAccountPassword(token:string,oldPassword:string,newPassword:string){return request<void>('/auth/password',{method:'POST',data:{oldPassword,newPassword},header:{Authorization:`Bearer ${token}`}});}
