import {describe,it,expect} from 'vitest';
import {buildUserPermissionTree,toggleUserPermissionBranch,userTreeCheckState} from './userPermissionTree';
const permissions:any=[{id:'read',code:'READ',name:'查看',status:'ENABLED'},{id:'edit',code:'EDIT',name:'编辑',status:'ENABLED'},{id:'deny',code:'DENY',name:'拒绝',status:'ENABLED'}];
const menus:any=[{id:'p',name:'页面',type:'PAGE',permissionCode:'READ',grantable:true,parentId:null,sortOrder:0},{id:'b',name:'编辑',type:'BUTTON',permissionCode:'EDIT',grantable:true,parentId:'p',sortOrder:0}];
describe('用户权限继承与独立授权',()=>{
 it('继承只读叶子仍显示已勾选',()=>{const tree=buildUserPermissionTree(menus,permissions,[],['read','edit'],[]);expect(tree[0].children![0].disableCheckbox).toBe(true);expect(userTreeCheckState(tree,new Set(['read','edit'])).checked).toContain('menu:b');});
 it('级联修改只操作额外权限，继承始终保留',()=>{const tree=buildUserPermissionTree(menus,permissions,[],['read'],[]);let selected=new Set(['read']);selected=toggleUserPermissionBranch(tree[0],selected,true,new Set(['read']));expect([...selected].sort()).toEqual(['edit','read']);selected=toggleUserPermissionBranch(tree[0],selected,false,new Set(['read']));expect([...selected]).toEqual(['read']);});
 it('已有用户和角色拒绝保持禁用，不进入可修改范围',()=>{const tree=buildUserPermissionTree(menus,permissions,[{permissionId:'edit',effect:'DENY'}],['read'],['deny']);expect(tree[0].children![0].disableCheckbox).toBe(true);expect(tree.flatMap(n=>n.permissionIds)).not.toContain('edit');expect(tree.flatMap(n=>n.permissionIds)).not.toContain('deny');});
});
