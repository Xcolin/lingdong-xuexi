import type { ReactNode } from 'react';
import type { MenuProps } from 'antd';
import type { MenuNode } from '../api/menus';

export function buildMenuNavigation(nodes: MenuNode[], registered: { path: string; label: string; icon?: ReactNode }[], icon: (name: string | null) => ReactNode) {
  const byId = new Map(nodes.map(node => [node.id, node]));
  const enabled = (node: MenuNode, seen = new Set<string>()): boolean => {
    if (node.status !== 'ENABLED' || seen.has(node.id)) return false;
    if (!node.parentId) return true;
    seen.add(node.id);
    const parent = byId.get(node.parentId);
    return !!parent && enabled(parent, seen);
  };
  const sorted = nodes.filter(node => enabled(node)).sort((a, b) => a.sortOrder - b.sortOrder || (a.code < b.code ? -1 : a.code > b.code ? 1 : 0));
  const pages: { path: string; label: string }[] = [];
  const used = new Set<string>();
  const render = (parentId: string | null): NonNullable<MenuProps['items']> => sorted.filter(node => node.parentId === parentId && node.type !== 'BUTTON').flatMap(node => {
    if (node.type === 'DIRECTORY') {
      const children = render(node.id);
      return children.length ? [{ key: `directory:${node.id}`, label: node.name, icon: icon(node.icon), children }] : [];
    }
    const existing = registered.find(page => page.path === node.route);
    if (!existing || used.has(existing.path)) return [];
    used.add(existing.path);
    pages.push({ path: existing.path, label: node.name });
    const children = render(node.id);
    const leaf = { key: existing.path, label: node.name, icon: icon(node.icon) ?? existing.icon };
    // Business pages remain selectable even if they own nested business pages.
    return children.length ? [{ key: `directory:${node.id}`, label: node.name, icon: leaf.icon, children: [leaf, ...children] }] : [leaf];
  });
  return { items: render(null), pages };
}
