import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ConfiguredButton } from './ConfiguredButton';
import { MenuConfigurationProvider, menuOrderStyles } from '../app/MenuConfiguration';
import type { MenuNode } from '../api/menus';

const action: MenuNode = { id: '1', code: 'IAM_USER_CREATE', name: '新增用户', type: 'BUTTON', parentId: null, route: null, icon: null, permissionCode: null, sortOrder: 30, status: 'ENABLED', version: '1' };
describe('配置按钮', () => {
  it('菜单授权名称带功能前缀但实际按钮仍使用短名称',()=>{
    render(<MenuConfigurationProvider permissionCodes={['IAM_USER_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE']} nodes={[{...action,name:'新增用户 · 新增用户'}]} refresh={async()=>{}}><ConfiguredButton actionKey="IAM_USER_CREATE">新增用户</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.getByRole('button',{name:'新增用户'})).toBeVisible();
    expect(screen.queryByRole('button',{name:'新增用户 · 新增用户'})).toBeNull();
  });
  it('分步操作保留上下文按钮名称并继续按权限控制', () => {
    const requestAction={...action,code:'STUDENT_ENROLLMENT_REQUEST_CREATE',name:'申请关联教育机构'};
    const view=render(<MenuConfigurationProvider permissionCodes={['IAM_USER_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE']} nodes={[requestAction]} refresh={async()=>{}}><ConfiguredButton actionKey={requestAction.code} preserveLabel>确认班级</ConfiguredButton><ConfiguredButton actionKey={requestAction.code} preserveLabel>提交入学申请</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.getByRole('button',{name:'确认班级'})).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'提交入学申请'})).toBeInTheDocument();
    view.rerender(<MenuConfigurationProvider permissionCodes={['IAM_USER_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE']} nodes={[]} refresh={async()=>{}}><ConfiguredButton actionKey={requestAction.code} preserveLabel>提交入学申请</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.queryByRole('button',{name:'提交入学申请'})).toBeNull();
  });
  it('没有菜单权限也可以取消、重试和重置', () => {
    render(<MenuConfigurationProvider permissionCodes={['IAM_USER_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE']} nodes={[]} refresh={async()=>{}}>
      <ConfiguredButton actionKey="users.reset-password.cancel">取消</ConfiguredButton>
      <ConfiguredButton actionKey="organizations.members.retry">重试</ConfiguredButton>
      <ConfiguredButton actionKey="organizations.members.reset">重置</ConfiguredButton>
      <ConfiguredButton actionKey="organizations.members.dissociate">取消关联</ConfiguredButton>
      <ConfiguredButton actionKey="IAM_USER_PASSWORD_SET">重置密码</ConfiguredButton>
    </MenuConfigurationProvider>);
    for (const name of ['取消','重试','重置']) expect(screen.getByRole('button',{name})).toBeVisible();
    expect(screen.queryByRole('button',{name:'取消关联'})).toBeNull();
    expect(screen.queryByRole('button',{name:'重置密码'})).toBeNull();
  });
  it('真实种子大写编码匹配动作目录，保留调用方上下文名称', () => {
    render(<MenuConfigurationProvider permissionCodes={['IAM_USER_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE']} nodes={[{...action,permissionCode:'IAM_USER_CREATE',grantable:true}]} refresh={async()=>{}}><ConfiguredButton actionKey="IAM_USER_CREATE" aria-label="新增用户至当前组织">新增用户</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.getByRole('button',{name:'新增用户至当前组织'})).toBeVisible();
  });
  it('停用或无当前展示权限时隐藏动作，单独渲染页面保持原始行为', () => {
    const view = render(<MenuConfigurationProvider permissionCodes={['IAM_USER_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE']} nodes={[]} refresh={async () => {}}><ConfiguredButton actionKey={action.code}>新增用户</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    view.rerender(<ConfiguredButton actionKey={action.code}>新增用户</ConfiguredButton>);
    expect(screen.getByRole('button', { name: '新增用户' })).toBeVisible();
  });
  it('菜单节点存在但没有有效权限仍隐藏，PAGE规范码可用于查询', () => {
    const page = { ...action, type: 'PAGE' as const, code: 'READ', permissionCode: 'READ' };
    const view = render(<MenuConfigurationProvider nodes={[page]} permissionCodes={[]} refresh={async()=>{}}><ConfiguredButton actionKey="READ">查询</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.queryByRole('button')).toBeNull();
    view.rerender(<MenuConfigurationProvider nodes={[page]} permissionCodes={['READ']} refresh={async()=>{}}><ConfiguredButton actionKey="READ">查询</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.getByRole('button',{name:/查\s*询/})).toBeVisible();
  });
  it('展示配置名称并为Space包装节点生成同级排序', () => {
    render(<MenuConfigurationProvider permissionCodes={['IAM_USER_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE']} nodes={[{ ...action, name: '添加账号' }]} refresh={async () => {}}><ConfiguredButton actionKey={action.code}>新增用户</ConfiguredButton></MenuConfigurationProvider>);
    expect(screen.getByRole('button', { name: '添加账号' })).toBeVisible();
    expect(menuOrderStyles([action])).toContain('.ant-space-item:has(');
    expect(menuOrderStyles([action])).toContain('order:30');
  });
});
