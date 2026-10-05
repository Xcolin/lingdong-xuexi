import type { MenuNode, MenuOrderInput } from '../../api/menus';
export const protectedMenu = (node?: MenuNode) => node?.route === '/dashboard' || node?.route === '/menu-management' || node?.code === 'menu-management' || !!node?.code.startsWith('menu-management.');
export const sortedMenus = (nodes: MenuNode[]) => [...nodes].sort((a,b) => a.sortOrder-b.sortOrder || (a.code < b.code ? -1 : a.code > b.code ? 1 : 0));
type Drop = {kind:'order';input:MenuOrderInput} | {kind:'move';id:string;input:{parentId:string|null;index:number;version:string}};
/** position: -1 before, 1 after, 0 inside the target. */
export function menuDrop(nodes: MenuNode[], dragId: string, targetId: string, position: number): Drop | null {
  const drag=nodes.find(n=>n.id===dragId), target=nodes.find(n=>n.id===targetId);
  if(!drag || !target) throw new Error('菜单已变化，请刷新');
  if(dragId===targetId) return null;
  const parentId=position===0 ? target.id : target.parentId;
  const parent=nodes.find(n=>n.id===parentId);
  if(parentId && (!parent || (drag.type==='BUTTON' ? parent.type!=='PAGE' : parent.type!=='DIRECTORY'))
    || !parentId && drag.type==='BUTTON') throw new Error('目录下只能放目录或页面，按钮必须放在页面下');
  const visited=new Set<string>(); let cursor=parentId;
  while(cursor) { if(cursor===drag.id || visited.has(cursor)) throw new Error('不能移入自身或子孙菜单'); visited.add(cursor); cursor=nodes.find(n=>n.id===cursor)?.parentId ?? null; }
  if(protectedMenu(drag) && parentId!==drag.parentId) throw new Error('核心入口不能移动层级');
  const siblings=sortedMenus(nodes.filter(n=>n.parentId===parentId && n.id!==dragId));
  const index=position===0 ? siblings.length : siblings.findIndex(n=>n.id===targetId)+(position>0?1:0);
  if(index<0) throw new Error('目标位置无效');
  if(parentId!==drag.parentId) return {kind:'move',id:drag.id,input:{parentId,index,version:drag.version}};
  siblings.splice(index,0,drag);
  const previous=sortedMenus(nodes.filter(n=>n.parentId===parentId));
  if(siblings.every((n,i)=>previous[i]?.id===n.id)) return null;
  return {kind:'order',input:{parentId,ids:siblings.map(n=>n.id),versions:Object.fromEntries(siblings.map(n=>[n.id,n.version]))}};
}
