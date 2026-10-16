export function classBelongsToSchool(
  item: { schoolOrganizationId?: string | null; parentId: string | null },
  schoolId: string | null | undefined
): boolean {
  if (!schoolId) return false;
  return (item.schoolOrganizationId || item.parentId) === schoolId;
}
