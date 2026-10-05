import type { MenuNode } from '../../api/menus';

/** 拖拽结果：同级排序（全量兄弟 + 版本）或改父级移动（目标父级 + 插入序号 + 版本）。 */
export type MenuDropAction =
  | { kind: 'REORDER'; parentId: string | null; ids: string[]; versions: Record<string, string> }
  | { kind: 'MOVE'; id: string; parentId: string | null; index: number; version: string };

export type MenuDropRejectCode =
  | 'PROTECTED'
  | 'SELF_OR_DESCENDANT'
  | 'TYPE_MISMATCH'
  | 'SAME_PARENT_GAP_REQUIRED'
  | 'INVALID';

export type MenuDropReject = { code: MenuDropRejectCode; message: string };

export type MenuDropResult =
  | { action: MenuDropAction; reject?: undefined }
  | { action?: undefined; reject: MenuDropReject };

/** 关键入口节点禁止拖动，与页面既有「停用/移动」禁用口径保持一致。 */
export function isProtectedMenuNode(node?: MenuNode): boolean {
  if (!node) return false;
  return node.route === '/dashboard' || node.route === '/menu-management'
    || node.code === 'menu-management' || node.code.startsWith('menu-management.');
}

/** 与后端一致的同级排序口径：先排序号，再按 ID 升序。 */
const bySortOrder = (a: MenuNode, b: MenuNode): number =>
  a.sortOrder - b.sortOrder || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);

function rejected(code: MenuDropRejectCode, message: string): MenuDropResult {
  return { reject: { code, message } };
}

/**
 * 拖拽落点解析：拖入节点内部为改父级并追加到末尾，间隙落点按相对位置决定插入序号；
 * 同父间隙为完整同级排序（后端要求提交该父级下全部兄弟与版本）。
 * relativePosition 取 antd Tree 的相对落点（-1 前 / 0 内部 / 1 后）。
 */
export function computeMenuDropAction({ dragNode, dropNode, dropToGap, relativePosition, nodes }: {
  dragNode: MenuNode;
  dropNode: MenuNode;
  dropToGap: boolean;
  relativePosition: -1 | 0 | 1;
  nodes: MenuNode[];
}): MenuDropResult {
  if (isProtectedMenuNode(dragNode)) return rejected('PROTECTED', `「${dragNode.name}」为关键入口，不可拖动调整`);

  const byId = new Map(nodes.map((node) => [node.id, node]));
  if (!byId.has(dragNode.id) || !byId.has(dropNode.id)) return rejected('INVALID', '菜单数据已变化，请刷新后重试');
  if (dropNode.id === dragNode.id) return rejected('SELF_OR_DESCENDANT', '不可拖拽到自身');

  const seen = new Set<string>();
  let cursor: string | null = dropNode.parentId;
  while (cursor && !seen.has(cursor)) {
    if (cursor === dragNode.id) return rejected('SELF_OR_DESCENDANT', '不可拖拽到自身或其子孙节点之下');
    seen.add(cursor);
    cursor = byId.get(cursor)?.parentId ?? null;
  }

  const targetParentId = dropToGap ? dropNode.parentId : dropNode.id;
  const targetParent = targetParentId ? byId.get(targetParentId) : undefined;
  if (targetParentId && !targetParent) return rejected('INVALID', '目标父级不存在，请刷新后重试');

  // 层级类型约束：按钮只能挂页面，目录与页面只能挂目录或根层级
  if (dragNode.type === 'BUTTON') {
    if (!targetParent || targetParent.type !== 'PAGE') return rejected('TYPE_MISMATCH', '按钮只能挂载在页面之下');
  } else if (targetParent && targetParent.type !== 'DIRECTORY') {
    return rejected('TYPE_MISMATCH', '目录与页面只能挂载在目录或根层级之下');
  }

  const siblingsOf = (parentId: string | null): MenuNode[] =>
    nodes.filter((node) => node.parentId === parentId).sort(bySortOrder);

  if (targetParentId === dragNode.parentId) {
    // 同父级：仅间隙落点可排序，拖入父级内部等价于原地不动，后端也会拒绝
    if (!dropToGap) return rejected('SAME_PARENT_GAP_REQUIRED', '同级顺序请拖拽到节点之间的间隙');
    const ordered = siblingsOf(dragNode.parentId).filter((node) => node.id !== dragNode.id);
    const dropIndex = ordered.findIndex((node) => node.id === dropNode.id);
    if (dropIndex < 0) return rejected('INVALID', '落点无效，请刷新后重试');
    ordered.splice(relativePosition < 0 ? dropIndex : dropIndex + 1, 0, dragNode);
    return {
      action: {
        kind: 'REORDER',
        parentId: dragNode.parentId,
        ids: ordered.map((node) => node.id),
        versions: Object.fromEntries(ordered.map((node) => [node.id, node.version]))
      }
    };
  }

  const targetSiblings = siblingsOf(targetParentId).filter((node) => node.id !== dragNode.id);
  let index: number;
  if (!dropToGap) {
    index = targetSiblings.length;
  } else {
    const dropIndex = targetSiblings.findIndex((node) => node.id === dropNode.id);
    if (dropIndex < 0) return rejected('INVALID', '落点无效，请刷新后重试');
    index = relativePosition < 0 ? dropIndex : dropIndex + 1;
  }
  return { action: { kind: 'MOVE', id: dragNode.id, parentId: targetParentId, index, version: dragNode.version } };
}
