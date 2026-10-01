import { createContext, useContext, useLayoutEffect, type PropsWithChildren } from 'react';
import type { MenuNode } from '../api/menus';

export interface MenuConfiguration { nodes: MenuNode[]; refresh: () => Promise<void> }
const Context = createContext<MenuConfiguration | null>(null);
let currentConfiguration: MenuConfiguration | null = null;
export function getMenuConfigurationSnapshot() { return currentConfiguration; }
export function MenuConfigurationScope({ value, children }: PropsWithChildren<{ value: MenuConfiguration | null }>) {
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function MenuConfigurationProvider({ nodes, refresh, children }: PropsWithChildren<MenuConfiguration>) {
  useLayoutEffect(() => {
    const value = { nodes, refresh };
    currentConfiguration = value;
    return () => { if (currentConfiguration === value) currentConfiguration = null; };
  }, [nodes, refresh]);
  return <Context.Provider value={{ nodes, refresh }}>{children}</Context.Provider>;
}
export function useMenuConfiguration() { return useContext(Context); }

export function menuOrderStyles(nodes: MenuNode[]) {
  return nodes.filter(node => node.type === 'BUTTON').map(node => {
    const code = node.code.replace(/[^a-zA-Z0-9_.-]/g, '');
    const order = Number.isFinite(node.sortOrder) ? Math.trunc(node.sortOrder) : 0;
    return `.configured-action[data-menu-code="${code}"],.ant-space-item:has(> .configured-action[data-menu-code="${code}"]),.ant-list-item-action>li:has(.configured-action[data-menu-code="${code}"]){order:${order}}`;
  }).join('\n');
}
