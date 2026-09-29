export interface ReviewIdentity { clientType: string; roleCodes: string[]; permissionCodes: string[] }
export function canReview(user: ReviewIdentity, enabled: boolean): boolean {
  return enabled && user.clientType === 'MINIAPP' && user.roleCodes.includes('PARENT')
    && !user.roleCodes.includes('SYS_AUDITOR') && user.permissionCodes.includes('TASK_ASSIGNMENT_REVIEW');
}
/** 页面隐藏、会话变化和刷新时废弃旧请求，防止历史数据重新出现。 */
export function requestEpoch() {
  let value = 0;
  return { next: () => ++value, current: (request: number) => value === request, invalidate: () => { value++; } };
}
