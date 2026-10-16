import type {TeacherOption} from './types';
/** Preserve the name-only fallback for older responses; include paths for names shared across campuses. */
export function teacherOptionLabel(teacher:TeacherOption):string {
 const path=teacher.organizationNames?.filter(Boolean).join('、');
 return path?`${teacher.displayName} · ${path}`:teacher.displayName;
}
