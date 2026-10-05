import type { Permission, PermissionAssignment } from '../../api/iam';
import type { MenuNode } from '../../api/menus';
export interface PermissionTreeNode {
  key: string; title: string; permissionIds: string[]; disableCheckbox?: boolean; children?: PermissionTreeNode[];
}
export function buildRolePermissionTree(menus: MenuNode[], permissions: Permission[], assignments: PermissionAssignment[]): PermissionTreeNode[] {
  const byCode = new Map(permissions.map(p => [p.code, p]));
  const deny = new Set(assignments.filter(a => a.effect === 'DENY').map(a => a.permissionId));
  const mapped = new Set<string>();
  const nodes = new Map<string, PermissionTreeNode>();
  const sorted = [...menus].sort((a, b) => a.sortOrder - b.sortOrder);
  for (const menu of sorted) {
    const permission = menu.type !== 'DIRECTORY' && menu.grantable !== false && menu.permissionCode ? byCode.get(menu.permissionCode) : undefined;
    if (permission) mapped.add(permission.id);
    const denied = permission && deny.has(permission.id);
    const disabled = permission && permission.status !== 'ENABLED';
    nodes.set(menu.id, { key: `menu:${menu.id}`, title: `${menu.name}${denied ? '（已禁止，保留）' : disabled ? '（权限已停用，保留）' : ''}`,
      permissionIds: permission && !denied && !disabled ? [permission.id] : [], disableCheckbox: !!denied || !!disabled, children: [] });
  }
  const roots: PermissionTreeNode[] = [];
  for (const menu of sorted) {
    const node = nodes.get(menu.id)!;
    if (menu.parentId && nodes.has(menu.parentId)) nodes.get(menu.parentId)!.children!.push(node);
    else roots.push(node);
  }
  const other: PermissionTreeNode = { key: 'other', title: '其他权限', permissionIds: [], children: permissions.filter(p => !mapped.has(p.id)).map(p => ({
    key: `permission:${p.id}`, title: `${p.name}（${p.code}）${deny.has(p.id) ? '（已禁止，保留）' : p.status !== 'ENABLED' ? '（已停用，保留）' : ''}`,
    permissionIds: p.status === 'ENABLED' && !deny.has(p.id) ? [p.id] : [], disableCheckbox: p.status !== 'ENABLED' || deny.has(p.id)
  })) };
  if (other.children!.length) roots.push(other);
  const collect = (node: PermissionTreeNode): string[] => {
    node.permissionIds = [...new Set([...node.permissionIds, ...(node.children ?? []).flatMap(collect)])];
    if (!node.permissionIds.length) node.disableCheckbox = true;
    return node.permissionIds;
  };
  roots.forEach(collect);
  return roots;
}
export function treeCheckState(tree: PermissionTreeNode[], selected: Set<string>): { checked: string[]; halfChecked: string[] } {
  const checked: string[] = [], halfChecked: string[] = [];
  const visit = (node: PermissionTreeNode) => {
    const count = node.permissionIds.filter(id => selected.has(id)).length;
    if (!node.disableCheckbox && count && count === node.permissionIds.length) checked.push(node.key);
    else if (!node.disableCheckbox && count) halfChecked.push(node.key);
    node.children?.forEach(visit);
  };
  tree.forEach(visit);
  return { checked, halfChecked };
}
export function togglePermissionBranch(node: PermissionTreeNode, selected: Set<string>, checked: boolean): Set<string> {
  const next = new Set(selected);
  if (!node.disableCheckbox) node.permissionIds.forEach(id => checked ? next.add(id) : next.delete(id));
  return next;
}
