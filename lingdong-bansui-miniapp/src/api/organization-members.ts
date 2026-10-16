import {request} from './http';
export interface OrganizationMember {id:string;username:string;displayName:string;mobile:string|null;administrator:boolean;owner?:boolean;organizationNames?:string[]}
export interface MemberPage {items:OrganizationMember[];page:number;pageSize:number;total:number}
export type MemberAction='members:associate'|'members:remove'|'administrators:assign'|'administrators:revoke';
const header=(token:string)=>({Authorization:`Bearer ${token}`});
export const organizationMembersApi={
 list:(token:string,id:string,side:'MEMBERS'|'CANDIDATES',keyword:string,page=1)=>request<MemberPage>(`/organizations/${encodeURIComponent(id)}/${side==='MEMBERS'?'members':'member-picker'}?side=${side}&group=SCHOOL&keyword=${encodeURIComponent(keyword)}&page=${page}&pageSize=20`,{header:header(token)}),
 action:(token:string,id:string,action:MemberAction,userIds:string[])=>request<void>(`/organizations/${encodeURIComponent(id)}/${action}`,{method:'POST',header:header(token),data:{userIds}})
};
