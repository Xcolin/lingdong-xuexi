import { describe, expect, it } from 'vitest';
import type { MenuNode } from '../../api/menus';
import { computeMenuDropAction, isProtectedMenuNode } from './menuTreeDrag';

function node(id: string, type: MenuNode['type'], parentId: string | null, sortOrder: number, extra: Partial<MenuNode> = {}): MenuNode {
  return {
    id, code: id.toUpperCase(), name: `菜单${id}`, type, parentId, sortOrder,
    route: type === 'PAGE' ? `/${id}` : null, icon: null, permissionCode: null,
    grantable: type !== 'DIRECTORY', status: 'ENABLED', version: `v${id}`, ...extra
  };
}

const dir1 = node('dir1', 'DIRECTORY', null, 0);
const dir2 = node('dir2', 'DIRECTORY', null, 10);
const dashboard = node('dash', 'PAGE', null, 20, { route: '/dashboard', code: 'DASHBOARD_READ' });
const pageA = node('pageA', 'PAGE', 'dir1', 0);
const pageB = node('pageB', 'PAGE', 'dir1', 10);
const pageC = node('pageC', 'PAGE', 'dir2', 0);
const btnA1 = node('btnA1', 'BUTTON', 'pageA', 0);
const btnA2 = node('btnA2', 'BUTTON', 'pageA', 10);
const btnB1 = node('btnB1', 'BUTTON', 'pageB', 0);
const all = [dir1, dir2, dashboard, pageA, pageB, pageC, btnA1, btnA2, btnB1];

function drop(dragNode: MenuNode, dropNode: MenuNode, dropToGap: boolean, relativePosition: -1 | 0 | 1) {
  return computeMenuDropAction({ dragNode, dropNode, dropToGap, relativePosition, nodes: all });
}

describe('菜单树拖拽落点解析', () => {
  it('同父间隙拖到目标之后提交全量兄弟顺序与版本', () => {
    expect(drop(pageB, pageA, true, 1)).toEqual({
      action: { kind: 'REORDER', parentId: 'dir1', ids: ['pageA', 'pageB'], versions: { pageA: 'vpageA', pageB: 'vpageB' } }
    });
  });

  it('同父间隙拖到目标之前按相对位置插入首位', () => {
    expect(drop(pageB, pageA, true, -1)).toEqual({
      action: { kind: 'REORDER', parentId: 'dir1', ids: ['pageB', 'pageA'], versions: { pageA: 'vpageA', pageB: 'vpageB' } }
    });
  });

  it('同父三节点拖到中间位置保持其余相对顺序', () => {
    const pageD = node('pageD', 'PAGE', 'dir1', 20);
    const result = computeMenuDropAction({
      dragNode: pageD, dropNode: pageB, dropToGap: true, relativePosition: -1, nodes: [...all, pageD]
    });
    expect(result).toEqual({
      action: {
        kind: 'REORDER', parentId: 'dir1', ids: ['pageA', 'pageD', 'pageB'],
        versions: { pageA: 'vpageA', pageB: 'vpageB', pageD: 'vpageD' }
      }
    });
  });

  it('拖入页面内部把按钮移动到该页面末尾', () => {
    expect(drop(btnB1, pageA, false, 0)).toEqual({
      action: { kind: 'MOVE', id: 'btnB1', parentId: 'pageA', index: 2, version: 'vbtnB1' }
    });
  });

  it('按钮跨页面间隙拖拽按目标父级与相对位置计算插入序号', () => {
    expect(drop(btnB1, btnA1, true, 1)).toEqual({
      action: { kind: 'MOVE', id: 'btnB1', parentId: 'pageA', index: 1, version: 'vbtnB1' }
    });
  });

  it('页面跨目录间隙拖拽落到目标目录对应序号', () => {
    expect(drop(pageB, pageC, true, 1)).toEqual({
      action: { kind: 'MOVE', id: 'pageB', parentId: 'dir2', index: 1, version: 'vpageB' }
    });
  });

  it('页面拖到根层级间隙按根兄弟集合计算序号', () => {
    expect(drop(pageA, dir2, true, 1)).toEqual({
      action: { kind: 'MOVE', id: 'pageA', parentId: null, index: 2, version: 'vpageA' }
    });
  });

  it('按钮拖入目录被类型约束拒绝', () => {
    const result = drop(btnA1, dir2, false, 0);
    expect(result.action).toBeUndefined();
    expect(result.reject?.code).toBe('TYPE_MISMATCH');
  });

  it('页面拖入页面被类型约束拒绝', () => {
    expect(drop(pageB, pageA, false, 0).reject?.code).toBe('TYPE_MISMATCH');
  });

  it('按钮拖到根层级被类型约束拒绝', () => {
    expect(drop(btnA1, dir2, true, 1).reject?.code).toBe('TYPE_MISMATCH');
  });

  it('拖入自身或子孙被拒绝', () => {
    expect(drop(dir1, pageA, false, 0).reject?.code).toBe('SELF_OR_DESCENDANT');
    expect(drop(pageA, btnA2, true, 1).reject?.code).toBe('SELF_OR_DESCENDANT');
    expect(drop(pageA, pageA, false, 0).reject?.code).toBe('SELF_OR_DESCENDANT');
  });

  it('关键入口节点不可拖动', () => {
    expect(isProtectedMenuNode(dashboard)).toBe(true);
    expect(drop(dashboard, dir2, true, 1).reject?.code).toBe('PROTECTED');
  });

  it('拖回自身父级内部提示改用间隙排序', () => {
    expect(drop(pageA, dir1, false, 0).reject?.code).toBe('SAME_PARENT_GAP_REQUIRED');
  });

  it('目标父级不存在时判为无效落点', () => {
    const orphan = computeMenuDropAction({
      dragNode: pageA, dropNode: node('ghost', 'PAGE', 'missing', 0), dropToGap: true, relativePosition: 1, nodes: all
    });
    expect(orphan.reject?.code).toBe('INVALID');
  });

  it('同级排序按传入节点集合完整提交顺序与版本', () => {
    const result = computeMenuDropAction({
      dragNode: pageA, dropNode: pageB, dropToGap: true, relativePosition: 1, nodes: [dir1, pageA, pageB]
    });
    expect(result).toEqual({
      action: { kind: 'REORDER', parentId: 'dir1', ids: ['pageB', 'pageA'], versions: { pageA: 'vpageA', pageB: 'vpageB' } }
    });
  });
});
