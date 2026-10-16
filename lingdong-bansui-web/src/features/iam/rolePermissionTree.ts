import type { Permission, PermissionAssignment } from '../../api/iam';
import type { MenuNode } from '../../api/menus';
export interface PermissionTreeNode {
  key: string; title: string; permissionIds: string[]; disableCheckbox?: boolean; children?: PermissionTreeNode[];
}
export function buildRolePermissionTree(menus: MenuNode[], permissions: Permission[], assignments: PermissionAssignment[]): PermissionTreeNode[] {
  const byCode = new Map(permissions.filter(p => !p.client || p.client === 'WEB' || p.client === 'BOTH').map(p => [p.code, p]));
  const deny = new Set(assignments.filter(a => a.effect === 'DENY').map(a => a.permissionId));
  const nodes = new Map<string, PermissionTreeNode>();
  const sorted = [...menus].sort((a, b) => a.sortOrder - b.sortOrder);
  const menusById = new Map(menus.map(menu => [menu.id, menu]));
  const availabilityReason = (menu: MenuNode): string | undefined => {
    let current: MenuNode | undefined = menu;
    const visited = new Set<string>();
    while (current) {
      if (visited.has(current.id)) return '菜单层级无效';
      visited.add(current.id);
      if (current.status === 'DISABLED') return current === menu ? '菜单已停用' : '上级菜单已停用';
      const permission = current.permissionCode ? byCode.get(current.permissionCode) : undefined;
      if (permission && deny.has(permission.id) && current !== menu) return '上级权限已禁止';
      if (permission?.status === 'DISABLED') return '权限已停用';
      if (permission?.available === false) return permission.unavailableReason || '当前功能或授权范围不可用';
      if (!current.parentId) return undefined;
      current = menusById.get(current.parentId);
      if (!current) return '上级菜单不存在';
    }
    return undefined;
  };
  for (const menu of sorted) {
    const permission = menu.type !== 'DIRECTORY' && menu.grantable !== false && menu.permissionCode ? byCode.get(menu.permissionCode) : undefined;
    // Miniapp-only leaves must never be offered in the Web authorization tree.
    if (menu.permissionCode && !permission && permissions.some(p => p.code === menu.permissionCode && p.client === 'MINIAPP')) continue;
    const denied = permission && deny.has(permission.id);
    const reason = availabilityReason(menu);
    nodes.set(menu.id, { key: `menu:${menu.id}`, title: `${menu.name}${denied ? '（已禁止，保留）' : reason ? `（${reason}）` : ''}`,
      permissionIds: permission && !denied && !reason ? [permission.id] : [], disableCheckbox: !!denied || !!reason, children: [] });
  }
  const roots: PermissionTreeNode[] = [];
  for (const menu of sorted) {
    const node = nodes.get(menu.id);
    if (!node) continue;
    if (menu.parentId && nodes.has(menu.parentId)) nodes.get(menu.parentId)!.children!.push(node);
    else roots.push(node);
  }
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
