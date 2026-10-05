import { describe, expect, it } from 'vitest';
import type { MenuNode } from '../../api/menus';
import { menuDrop } from './menuDrag';
const item = (id: string, type: MenuNode['type'], parentId: string | null, sortOrder: number): MenuNode => ({ id, code: id, name: id, type, parentId, sortOrder, route: null, icon: null, permissionCode: null, status: 'ENABLED', version: '9007199254740993' });
const nodes = [item('dir','DIRECTORY',null,0),item('other','DIRECTORY',null,10),item('a','PAGE','dir',0),item('b','PAGE','dir',10),item('child','DIRECTORY','dir',20),item('button','BUTTON','a',0)];
describe('菜单拖动', () => {
  it('同级插入保留完整兄弟及精确版本', () => { expect(menuDrop(nodes,'b','a',-1)).toEqual({ kind:'order', input:{parentId:'dir',ids:['b','a','child'],versions:{a:'9007199254740993',b:'9007199254740993',child:'9007199254740993'}} }); });
  it('跨目录移动保留目标位置与节点版本', () => { expect(menuDrop(nodes,'a','other',0)).toEqual({kind:'move',id:'a',input:{parentId:'other',index:0,version:'9007199254740993'}}); });
  it('拒绝自身子孙和不合法类型', () => { expect(()=>menuDrop(nodes,'dir','child',0)).toThrow(); expect(()=>menuDrop(nodes,'a','b',0)).toThrow(); expect(()=>menuDrop(nodes,'button','dir',0)).toThrow(); });
  it('按钮可拖到页面下，核心入口不可跨目录', () => { expect(menuDrop(nodes,'button','b',0)).toMatchObject({kind:'move',input:{parentId:'b',index:0}}); expect(()=>menuDrop([...nodes,{...nodes[2],id:'core',route:'/dashboard'}],'core','other',0)).toThrow(); });
  it('原位置拖动无操作', () => { expect(menuDrop(nodes,'a','b',-1)).toBeNull(); });
});
