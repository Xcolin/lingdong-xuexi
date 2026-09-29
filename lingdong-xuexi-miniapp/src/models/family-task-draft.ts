export interface FamilyIdentity { clientType: string; roleCodes: string[]; permissionCodes: string[] }
export function canManageFamily(user: FamilyIdentity, permission: string) { return user.clientType === 'MINIAPP'
  && user.roleCodes.includes('PARENT') && !user.roleCodes.includes('SYS_AUDITOR') && user.permissionCodes.includes(permission); }
export interface FamilyForm { title: string; difficultyLevel: number; durationMinutes: number; scheduledDate: string; remark: string; studentIds: string[] }
export interface PreservedFields { categoryCode?: string | null; tagCodes?: string[]; recurrenceEnabled: boolean; recurrenceEndDate?: string | null }
/** 简化表单仍保留 Web 草稿的分类、标签、重复配置及所有学生目标。 */
export function draftInput(form: FamilyForm, previous?: PreservedFields) {
  return { sourceType: 'FAMILY' as const, title: form.title.trim(), difficultyLevel: Number(form.difficultyLevel),
    durationMinutes: Number(form.durationMinutes), scheduledDate: form.scheduledDate, remark: form.remark,
    categoryCode: previous?.categoryCode ?? null, tagCodes: previous?.tagCodes ?? [],
    recurrenceEnabled: previous?.recurrenceEnabled ?? false, recurrenceEndDate: previous?.recurrenceEndDate ?? null,
    targets: form.studentIds.map(targetId => ({ targetType: 'STUDENT' as const, targetId })) };
}
