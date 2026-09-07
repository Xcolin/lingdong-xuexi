import { request } from './http';

export interface TeacherWorkbenchClass {
  classId: string;
  className: string;
  schoolId: string;
  schoolName: string;
}

export interface TeacherWorkbenchContext {
  userId: string;
  username: string;
  displayName: string;
  permissionCodes: string[];
  classes: TeacherWorkbenchClass[];
}

/** 使用显式组织用户令牌读取教师本人班级，前端不提交教师或班级范围。 */
export function getTeacherWorkbenchContext(
  accessToken: string
): Promise<TeacherWorkbenchContext> {
  return request<TeacherWorkbenchContext>('/teacher-workbench/context', {
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
