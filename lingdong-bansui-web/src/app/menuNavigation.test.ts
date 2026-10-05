import { describe, expect, it } from 'vitest';
import { buildMenuNavigation } from './menuNavigation';
import type { MenuNode } from '../api/menus';
const node = (id: string, route: string | null, parentId: string | null = null): MenuNode => ({ id, code: id, name: id, type: route ? 'PAGE' : 'DIRECTORY', parentId, route, icon: null, permissionCode: null, sortOrder: Number(id) || 0, status: 'ENABLED', version: '1' });
describe('动态菜单导航', () => {
  it('权限管理与导入导出管理按模块分组，保留工作台独立入口', () => {
    const groups = [
      {...node('access', null),name:'权限管理'},
      {...node('transfer', null),name:'导入导出管理'}
    ];
    const routes = [
      ['/iam','access'],['/users','access'],['/menu-management','access'],['/organizations','access'],
      ['/import-export-templates','transfer'],['/import-jobs','transfer'],['/export-jobs','transfer']
    ];
    const result=buildMenuNavigation([
      node('dashboard','/dashboard'),...groups,
      ...routes.map(([route,parent])=>node(route,route,parent))
    ],[{path:'/dashboard',label:'工作台'},...routes.map(([path])=>({path,label:path}))],()=>null);
    expect(result.items).toContainEqual(expect.objectContaining({key:'/dashboard'}));
    expect(result.items).toContainEqual(expect.objectContaining({key:'directory:access',label:'权限管理',children:expect.arrayContaining(['/iam','/users','/menu-management','/organizations'].map(key=>expect.objectContaining({key})))}));
    expect(result.items).toContainEqual(expect.objectContaining({key:'directory:transfer',label:'导入导出管理',children:expect.arrayContaining(['/import-export-templates','/import-jobs','/export-jobs'].map(key=>expect.objectContaining({key})))}));
    expect(result.pages).toHaveLength(8);
  });
  it('按配置顺序与名称生成目录，保留业务授权过滤', () => {
    const result = buildMenuNavigation([node('2', '/users', '1'), node('1', null), node('3', '/iam', '1')], [{ path: '/users', label: '原名称' }], () => null);
    expect(result.pages).toEqual([{ path: '/users', label: '2' }]);
    expect(result.items).toHaveLength(1);
    expect(result.items[0]).toMatchObject({ key: 'directory:1', children: [{ key: '/users', label: '2' }] });
  });
  it('停用祖先和循环节点不能显示，重复页面只出现一次', () => {
    const result = buildMenuNavigation([{ ...node('1', null), status: 'DISABLED' }, node('2', '/users', '1'), node('3', '/dashboard'), node('4', '/dashboard'), node('5', null, '6'), node('6', null, '5')], [{ path: '/users', label: '用户' }, { path: '/dashboard', label: '首页' }], () => null);
    expect(result.pages).toEqual([{ path: '/dashboard', label: '3' }]);
  });
});
