import type {MenuNode} from '../../api/menus';
import type {Permission,PermissionAssignment} from '../../api/iam';
import {buildRolePermissionTree,togglePermissionBranch,treeCheckState,type PermissionTreeNode} from './rolePermissionTree';
export interface UserPermissionTreeNode extends PermissionTreeNode {inherited?:boolean;children?:UserPermissionTreeNode[];}
export function buildUserPermissionTree(menus:MenuNode[],permissions:Permission[],assignments:PermissionAssignment[],inherited:string[],denied:string[]):UserPermissionTreeNode[]{
 const tree=buildRolePermissionTree(menus,permissions,[...assignments,...denied.map(permissionId=>({permissionId,effect:'DENY' as const}))]) as UserPermissionTreeNode[];
 const blocked=new Set([...denied,...assignments.filter(a=>a.effect==='DENY').map(a=>a.permissionId)]);const locked=new Set(inherited);const byCode=new Map(permissions.map(p=>[p.code,p.id]));
 const menuOwn=new Map(menus.map(m=>['menu:'+m.id,m.permissionCode?byCode.get(m.permissionCode):undefined]));
 const walk=(node:UserPermissionTreeNode)=>{const own=menuOwn.get(node.key)??(node.key.startsWith('permission:')?node.key.slice(11):undefined);if(own&&locked.has(own)&&!blocked.has(own)){node.inherited=true;node.title+='（角色继承）';if(node.permissionIds.every(id=>locked.has(id)))node.disableCheckbox=true;}node.children?.forEach(walk);};tree.forEach(walk);return tree;
}
export function toggleUserPermissionBranch(node:PermissionTreeNode,selected:Set<string>,checked:boolean,locked:Set<string>){const next=togglePermissionBranch(node,selected,checked);locked.forEach(id=>next.add(id));return next;}

export function userTreeCheckState(tree:UserPermissionTreeNode[],selected:Set<string>){const display=(node:UserPermissionTreeNode):PermissionTreeNode=>({...node,disableCheckbox:node.inherited?false:node.disableCheckbox,children:node.children?.map(display)});return treeCheckState(tree.map(display),selected);}
