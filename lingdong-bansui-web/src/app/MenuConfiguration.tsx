import { createContext, useContext, useLayoutEffect, type PropsWithChildren } from 'react';
import type { MenuNode } from '../api/menus';

export interface MenuConfiguration { nodes: MenuNode[]; permissionCodes?: string[]; refresh: () => Promise<void> }
const Context = createContext<MenuConfiguration | null>(null);
let currentConfiguration: MenuConfiguration | null = null;
export function getMenuConfigurationSnapshot() { return currentConfiguration; }
export function MenuConfigurationScope({ value, children }: PropsWithChildren<{ value: MenuConfiguration | null }>) {
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function MenuConfigurationProvider({ nodes, permissionCodes = [], refresh, children }: PropsWithChildren<MenuConfiguration>) {
  useLayoutEffect(() => {
    const value = { nodes, permissionCodes, refresh };
    currentConfiguration = value;
    return () => { if (currentConfiguration === value) currentConfiguration = null; };
  }, [nodes, permissionCodes, refresh]);
  return <Context.Provider value={{ nodes, permissionCodes, refresh }}>{children}</Context.Provider>;
}
export function useMenuConfiguration() { return useContext(Context); }

export function menuOrderStyles(nodes: MenuNode[]) {
  return nodes.filter(node => node.type === 'BUTTON').map(node => {
    const code = node.code.replace(/[^a-zA-Z0-9_.-]/g, '');
    const order = Number.isFinite(node.sortOrder) ? Math.trunc(node.sortOrder) : 0;
    return `.configured-action[data-menu-code="${code}"],.ant-space-item:has(> .configured-action[data-menu-code="${code}"]),.ant-list-item-action>li:has(.configured-action[data-menu-code="${code}"]){order:${order}}`;
  }).join('\n');
}

/** Menu presentation and effective server permissions must both authorize an action. */
export function configuredAccess(configuration: MenuConfiguration | null, actionKey: string): boolean {
  if (!configuration) return true; // Standalone components remain usable outside the authenticated shell.
  const node = configuration.nodes.find(item => item.type !== 'DIRECTORY' && (item.code === actionKey || item.permissionCode === actionKey));
  if (!node || !(configuration.permissionCodes ?? []).includes(node.permissionCode ?? node.code)) return false;
  const byId = new Map(configuration.nodes.map(item => [item.id, item]));
  let current: MenuNode | undefined = node;
  const visited = new Set<string>();
  while (current) {
    if (current.status !== 'ENABLED' || visited.has(current.id)) return false;
    visited.add(current.id);
    if (!current.parentId) return true;
    current = byId.get(current.parentId);
    if (!current) return false;
  }
  return false;
}
