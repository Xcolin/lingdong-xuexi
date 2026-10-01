import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ConfiguredButton } from './ConfiguredButton';
import { MenuConfigurationProvider, menuOrderStyles } from '../app/MenuConfiguration';
import type { MenuNode } from '../api/menus';

const action: MenuNode = { id: '1', code: 'users.user-management-page.1', name: '新增用户', type: 'BUTTON', parentId: '2', route: null, icon: null, permissionCode: null, sortOrder: 30, status: 'ENABLED', version: '1' };
describe('配置按钮', () => {
  it('停用或无当前展示权限时隐藏动作，单独渲染页面保持原始行为', () => {
    const view = render(<MenuConfigurationProvider nodes={[]} refresh={async () => {}}><ConfiguredButton actionKey={action.code}>新增用户</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    view.rerender(<ConfiguredButton actionKey={action.code}>新增用户</ConfiguredButton>);
    expect(screen.getByRole('button', { name: '新增用户' })).toBeVisible();
  });
  it('展示配置名称并为Space包装节点生成同级排序', () => {
    render(<MenuConfigurationProvider nodes={[{ ...action, name: '添加账号' }]} refresh={async () => {}}><ConfiguredButton actionKey={action.code}>新增用户</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.getByRole('button', { name: '添加账号' })).toBeVisible();
    expect(menuOrderStyles([action])).toContain('.ant-space-item:has(');
    expect(menuOrderStyles([action])).toContain('order:30');
  });
});
