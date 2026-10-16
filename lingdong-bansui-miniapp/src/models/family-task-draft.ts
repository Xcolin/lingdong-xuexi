import {isParentIdentity} from '@/models/parent-identity';
export interface FamilyIdentity { parentProfile?:boolean;userType?:string;clientType: string; roleCodes: string[]; permissionCodes: string[] }
export function canManageFamily(user: FamilyIdentity, permission: string) { return isParentIdentity(user) && user.permissionCodes.includes(permission); }
export interface FamilyForm { title: string; difficultyLevel: number; durationMinutes: number; scheduledDate: string; remark: string; studentIds: string[]; recurrenceEnabled?: boolean; recurrenceEndDate?: string | null; recurrenceFrequency?: 'DAILY' | 'WEEKLY' | 'CUSTOM'; recurrenceWeekdays?: number[] }
export interface PreservedFields { categoryCode?: string | null; tagCodes?: string[]; recurrenceEnabled: boolean; recurrenceEndDate?: string | null; recurrenceFrequency?: 'DAILY' | 'WEEKLY' | 'CUSTOM'; recurrenceWeekdays?: number[] }
/** 简化表单仍保留 Web 草稿的分类、标签、重复配置及所有学生目标。 */
export function draftInput(form: FamilyForm, previous?: PreservedFields) {
  return { sourceType: 'FAMILY' as const, title: form.title.trim(), difficultyLevel: Number(form.difficultyLevel),
    durationMinutes: Number(form.durationMinutes), scheduledDate: form.scheduledDate, remark: form.remark,
    categoryCode: previous?.categoryCode ?? null, tagCodes: previous?.tagCodes ?? [],
    ...recurrenceFields(form, previous),
    targets: form.studentIds.map(targetId => ({ targetType: 'STUDENT' as const, targetId })) };
}

export function recurrenceError(form: FamilyForm, weekdayCapability?: boolean): string {
  if (!form.recurrenceEnabled) return '';
  if (form.recurrenceEndDate && form.recurrenceEndDate < form.scheduledDate) return '重复截止日期不能早于任务日期';
  const frequency = form.recurrenceFrequency ?? 'DAILY';
  if (frequency !== 'DAILY' && weekdayCapability !== true) return '当前服务暂未开启星期重复，请选择每天';
  if (!['DAILY','WEEKLY','CUSTOM'].includes(frequency)) return '请选择有效重复频率';
  const days = form.recurrenceWeekdays ?? [];
  if (days.some(day => !Number.isInteger(day) || day < 1 || day > 7) || new Set(days).size !== days.length) return '请选择有效的重复星期';
  if (frequency === 'DAILY') return '';
  if (!days.length) return '请选择重复星期';
  if (frequency === 'WEEKLY' && days.length !== 1) return '每周重复请选择一个星期';
  const weekday = new Date(form.scheduledDate + 'T00:00:00Z').getUTCDay() || 7;
  return days.includes(weekday) ? '' : '重复星期必须包含任务起始日期的星期';
}

export function recurrenceFields(form: Pick<FamilyForm, "recurrenceEnabled" | "recurrenceEndDate" | "recurrenceFrequency" | "recurrenceWeekdays">, previous?: PreservedFields) {
  return {
    recurrenceEnabled: form.recurrenceEnabled ?? previous?.recurrenceEnabled ?? false, recurrenceEndDate: form.recurrenceEnabled === false ? null : (form.recurrenceEndDate !== undefined ? form.recurrenceEndDate || null : previous?.recurrenceEndDate ?? null),
    recurrenceFrequency: form.recurrenceEnabled === false ? null : form.recurrenceFrequency ?? previous?.recurrenceFrequency ?? 'DAILY', recurrenceWeekdays: form.recurrenceEnabled === false ? [] : [...(form.recurrenceWeekdays ?? previous?.recurrenceWeekdays ?? [])],
  };
}
