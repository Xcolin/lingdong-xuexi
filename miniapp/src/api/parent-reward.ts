import { request } from './http';
import type { StudentRewardExchange } from './reward';
export interface RewardIdentity { clientType:string; roleCodes:string[]; permissionCodes:string[] }
export const MANAGE_REWARDS='MINIAPP_REWARD_MANAGE_CHILD';
export const REVIEW_EXCHANGES='MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD';
export function canUseParentRewards(user:RewardIdentity, permission?:string) {
  return user.clientType==='MINIAPP' && user.roleCodes.includes('PARENT') && !user.roleCodes.includes('SYS_AUDITOR')
    && (permission ? user.permissionCodes.includes(permission) : [MANAGE_REWARDS,REVIEW_EXCHANGES].some(code=>user.permissionCodes.includes(code)));
}
export interface RewardChild { studentId:string;studentName:string;relationshipRole:'PRIMARY_GUARDIAN'|'SECONDARY_GUARDIAN' }
export interface RewardInput {rewardName:string;requiredPoints:number;description:string|null;expiresAt:string|null;status:'ONLINE'|'OFFLINE'}
export interface ParentReward extends RewardInput {id:string;studentId:string}
export interface RewardPage<T> {items:T[];page:number;pageSize:number;total:number}
const header=(token:string)=>({Authorization:`Bearer ${token}`});
const id=(value:string)=>encodeURIComponent(value);
export const parentRewardApi={
 me:(token:string)=>request<RewardIdentity>('/auth/me',{header:header(token)}),
 children:(token:string)=>request<RewardChild[]>('/parent-rewards/students',{header:header(token)}),
 rewards:(token:string,studentId:string,page:number)=>request<RewardPage<ParentReward>>(`/parent-rewards/students/${id(studentId)}?page=${page}&pageSize=20`,{header:header(token)}),
 exchanges:(token:string,studentId:string,page:number)=>request<RewardPage<StudentRewardExchange>>(`/parent-reward-exchanges/students/${id(studentId)}?page=${page}&pageSize=20`,{header:header(token)}),
 save:(token:string,studentId:string,rewardId:string|null,data:RewardInput)=>request<ParentReward>(rewardId?`/parent-rewards/${id(rewardId)}`:`/parent-rewards/students/${id(studentId)}`,{header:header(token),method:rewardId?'PUT':'POST',data}),
 remove:(token:string,rewardId:string)=>request<void>(`/parent-rewards/${id(rewardId)}`,{header:header(token),method:'DELETE'}),
 process:(token:string,exchangeId:string,action:'approve'|'reject'|'verify',rejectReason?:string)=>request<StudentRewardExchange>(`/parent-reward-exchanges/${id(exchangeId)}/${action}`,{header:header(token),method:'POST',data:action==='reject'?{rejectReason}:{} })
};
