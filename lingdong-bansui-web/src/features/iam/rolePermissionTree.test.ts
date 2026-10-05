import { describe, expect, it } from 'vitest';
import type { Permission } from '../../api/iam';
import type { MenuNode } from '../../api/menus';
import { buildRolePermissionTree, treeCheckState, togglePermissionBranch } from './rolePermissionTree';

const permissions = ['PAGE_READ', 'EDIT', 'REMOVE', 'EXTRA'].map((code, index) => ({
  id: String(index + 1), code, name: code, status: 'ENABLED', resourceType: 'OPERATION', client: 'WEB', parentId: null, description: null
} as Permission));
const menus = [
  { id: 'd', name: '目录', type: 'DIRECTORY', parentId: null },
  { id: 'p', name: '页面', type: 'PAGE', parentId: 'd', permissionCode: 'PAGE_READ', grantable: true },
  { id: 'b', name: '编辑', type: 'BUTTON', parentId: 'p', permissionCode: 'EDIT', grantable: true },
  { id: 'x', name: '删除', type: 'BUTTON', parentId: 'p', permissionCode: 'REMOVE', grantable: true }
] as MenuNode[];
describe('角色权限树', () => {
  it('系统任务查看详情、批准、驳回使用动作编码独立勾选并可级联全选', () => {
    const buttons=['detail','approve','reject'].map((name,index)=>({id:`task-${index}`,code:`system-tasks.${name}`,name,permissionCode:`system-tasks.${name}`,type:'BUTTON',parentId:'tasks',grantable:true,sortOrder:index} as MenuNode));
    const taskPermissions=buttons.map(button=>({id:button.id,code:button.code,name:button.name,status:'ENABLED'} as Permission));
    const tree=buildRolePermissionTree([{id:'tasks',name:'系统任务',type:'PAGE',parentId:null} as MenuNode,...buttons],taskPermissions,[]);
    expect(tree[0].children?.every(button=>!button.disableCheckbox)).toBe(true);
    const selected=togglePermissionBranch(tree[0].children![1],new Set(),true);
    expect(selected).toEqual(new Set(['task-1']));
    expect(togglePermissionBranch(tree[0],selected,true)).toEqual(new Set(['task-0','task-1','task-2']));
  });
  it('勾选目录包含页面自身和按钮权限，跳过DENY，其他权限保留独立分组', () => {
    const tree = buildRolePermissionTree(menus, permissions, [{ permissionId: '3', effect: 'DENY' }]);
    const selected = togglePermissionBranch(tree[0], new Set<string>(), true);
    expect([...selected]).toEqual(['1', '2']);
    expect(tree[1].title).toBe('其他权限');
    expect(tree[1].permissionIds).toEqual(['4']);
    expect(tree[0].children?.[0].children?.[1].disableCheckbox).toBe(true);
  });
  it('页面自身权限没有全选按钮时上级显示半选，取消目录级联删除', () => {
    const tree = buildRolePermissionTree(menus, permissions, []);
    const state = treeCheckState(tree, new Set(['1']));
    expect(state.halfChecked).toContain('menu:p');
    expect(state.halfChecked).toContain('menu:d');
    expect(togglePermissionBranch(tree[0], new Set(['1', '2', '4']), false)).toEqual(new Set(['4']));
  });
});
