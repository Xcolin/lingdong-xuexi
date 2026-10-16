interface TeacherCampusContext {
  schoolId: string;
  schoolName: string;
  classOrganizationIds: string[];
}
interface SchoolChild { id: string; parentId: string | null; schoolOrganizationId?: string | null }
interface Campus { id: string; name: string; schoolOrganizationId: string | null; sortOrder: number }

/** Derive campus labels only from actual class ancestry inside the displayed institution. */
export function teacherCampusLabel(teacher: TeacherCampusContext, classes: SchoolChild[],
  grades: SchoolChild[], campuses: Campus[]): string {
  const classIds = new Set(teacher.classOrganizationIds);
  const gradeIds = new Set(classes.filter(item => classIds.has(item.id)
    && item.schoolOrganizationId === teacher.schoolId).map(item => item.parentId));
  const campusIds = new Set(grades.filter(item => gradeIds.has(item.id)
    && item.schoolOrganizationId === teacher.schoolId).map(item => item.parentId));
  const names = campuses.filter(item => campusIds.has(item.id)
    && item.schoolOrganizationId === teacher.schoolId)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'zh-CN'))
    .map(item => item.name);
  return names.length ? [...new Set(names)].join('、') : teacher.schoolName;
}
