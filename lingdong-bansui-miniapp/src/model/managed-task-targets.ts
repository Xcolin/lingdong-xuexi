export type ManagedTarget = { targetType: 'ORGANIZATION' | 'STUDENT'; targetId: string };
export function managedTargets(original: ManagedTarget[] | undefined, preserving: boolean, studentMode: boolean, organizationId: string, studentIds: string[]): ManagedTarget[] {
  if (preserving) return (original ?? []).map(target => ({ ...target }));
  if (!organizationId) throw new Error('请选择下发范围');
  if (!studentMode) return [{ targetType: 'ORGANIZATION', targetId: organizationId }];
  const ids = [...new Set(studentIds)];
  if (!ids.length || ids.some(id => !id.trim())) throw new Error('请选择指定孩子');
  return ids.map(targetId => ({ targetType: 'STUDENT', targetId }));
}
