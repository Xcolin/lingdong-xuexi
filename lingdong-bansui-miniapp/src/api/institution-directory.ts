import {request} from './http';
export interface DirectoryChild {studentId:string;studentName:string;studentNumber:string|null;status:'ACTIVE'|'INACTIVE';gradeNames:string;classNames:string;classOrganizationId:string|null}
export interface DirectoryFilters {institutionOrganizationId:string;keyword?:string;gradeOrganizationId?:string;classOrganizationId?:string;status?:'ALL'|'ACTIVE'|'INACTIVE'}
export interface DirectoryOptions {grades:{id:string;name:string}[];classes:{id:string;name:string;gradeId:string|null}[]}
export interface PageResult<T>{items:T[];total:number;page:number;pageSize:number}
function query(values:Record<string,string|number|undefined>){return Object.entries(values).filter(([,v])=>v!==undefined&&v!=='').map(([k,v])=>`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&');}
export const institutionDirectoryApi={
 list:(token:string,filters:DirectoryFilters,page=1)=>request<PageResult<DirectoryChild>>('/students/institution-directory?'+query({...filters,page,pageSize:20}),{header:{Authorization:`Bearer ${token}`}}),
 options:(token:string,institutionOrganizationId:string)=>request<DirectoryOptions>('/students/institution-directory/options?'+query({institutionOrganizationId}),{header:{Authorization:`Bearer ${token}`}}),
 detail:(token:string,studentId:string,institutionOrganizationId:string)=>request<DirectoryChild>(`/students/institution-directory/${encodeURIComponent(studentId)}?`+query({institutionOrganizationId}),{header:{Authorization:`Bearer ${token}`}})
};
