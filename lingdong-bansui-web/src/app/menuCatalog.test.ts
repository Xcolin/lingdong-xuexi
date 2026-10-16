import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {describe,expect,it} from 'vitest';
import {menuActionCatalog,menuPageCatalog} from './menuCatalog';
describe('规范菜单与实际操作定义一致',()=>{
 it('页面与按钮编码来自同一服务端定义，不含辅助交互或别名',()=>{
  const model=JSON.parse(readFileSync(resolve(process.cwd(),'../lingdong-bansui-server/src/main/resources/authorization-model.json'),'utf8'));
  expect(menuPageCatalog).toEqual(model.pages);
  expect(menuActionCatalog).toEqual(model.actions);
  const codes=menuActionCatalog.map(a=>a.code);
  expect(new Set(codes).size).toBe(codes.length);
  expect(codes.every(code=>/^[A-Z][A-Z0-9_]+$/.test(code))).toBe(true);
  expect(codes.some(code=>/\.ok$|\.cancel$|\.query$/.test(code))).toBe(false);
  expect(codes).toEqual(expect.arrayContaining(['ORG_ADMIN_ASSIGN','ORG_ADMIN_REVOKE','ORG_MEMBER_ASSOCIATE','REWARD_CREATE','REWARD_UPDATE','REWARD_DELETE']));
  const server=JSON.parse(readFileSync(resolve(process.cwd(),'../lingdong-bansui-server/src/main/resources/web-menu-actions.json'),'utf8'));
  expect(server).toEqual(menuActionCatalog);
 });
});
