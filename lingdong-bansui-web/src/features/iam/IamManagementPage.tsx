import { Button as PlainButton } from 'antd';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ConfiguredAction } from '../../components/ConfiguredAction';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useRef, useState } from 'react';
import { Alert, Form, Input, Modal, Popconfirm, Radio, Select, Space, Tabs, Tag, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { Plus } from 'lucide-react';
import {
  iamApi,
  type CreateRoleInput,
  type Permission,
  type Role
} from '../../api/iam';
import { IamAuditPanel } from './IamAuditPanel';
import { RolePermissionTreeModal } from './RolePermissionTreeModal';
import { RoleUsersModal } from './RoleUsersModal';

const dataScopeLabels: Record<string, string> = { ALL: '全部数据', REGION: '区域数据', SCHOOL: '学校数据', CLASS: '班级数据', FAMILY: '家庭数据', SELF: '本人数据', CUSTOM: '自定义范围' };
interface DirectoryFilters { keyword?: string; status?: string }

export function IamManagementPage({ permissionCodes = ['IAM_ROLE_READ', 'IAM_PERMISSION_READ'], roleCodes = [] }: { permissionCodes?: string[]; roleCodes?: string[] }) {
  const systemAdministrator = roleCodes.includes('SYS_ADMIN');
  const [activeTab, setActiveTab] = useState('roles');
  const [directoryFilters, setDirectoryFilters] = useState<DirectoryFilters>({});
  const [directoryForm] = Form.useForm<DirectoryFilters>();
  const matchesDirectory = (item: Role) => (!directoryFilters.keyword || `${item.name} ${item.code}`.toLowerCase().includes(directoryFilters.keyword.toLowerCase())) && (!directoryFilters.status || item.status === directoryFilters.status);
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [editingRole, setEditingRole] = useState<Role>();
  const pendingRoleRef = useRef(false);
  const [pendingRole, setPendingRole] = useState<string>();
  const [editForm] = Form.useForm<{ name: string; description?: string }>();
  const [selectedRole,setSelectedRole]=useState<Role>();
  const [memberRole,setMemberRole]=useState<Role>();
  const [loading, setLoading] = useState(true);

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [rolePermissionModalOpen, setRolePermissionModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [roleForm] = Form.useForm<CreateRoleInput>();


  useEffect(() => {
    void loadIamData();
  }, []);

  async function loadIamData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [loadedRoles, loadedPermissions] = await Promise.all([
        permissionCodes.includes('IAM_ROLE_READ') ? iamApi.listRoles() : Promise.resolve([]),
        permissionCodes.includes('IAM_PERMISSION_READ') ? iamApi.listPermissions() : Promise.resolve([])
      ]);
      setRoles(loadedRoles);
      setPermissions(loadedPermissions);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function mutateRole(role: Role, permission: string, action: () => Promise<unknown>, success: string) {
    if ((isBuiltIn(role) && !(permission === 'IAM_ROLE_UPDATE' && systemAdministrator)) || pendingRoleRef.current || !permissionCodes.includes(permission)) return;
    pendingRoleRef.current = true;
    setPendingRole(role.id);
    try { await action(); message.success(success); await loadIamData(); }
    catch (error) { message.error(toMessage(error)); }
    finally { pendingRoleRef.current = false; setPendingRole(undefined); }
  }

  async function createRole(values: CreateRoleInput): Promise<void> {
    await submitAction(async () => {
      await iamApi.createRole(values);
      setRoleModalOpen(false);
      roleForm.resetFields();
      await loadIamData();
    }, '角色已创建');
  }

  async function submitAction(action: () => Promise<void>, successText: string): Promise<void> {
    setSubmitting(true);
    try {
      await action();
      message.success(successText);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page-stack management-page iam-management-page">
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<PlainButton  size="small" onClick={() => void loadIamData()}>重试</PlainButton>} />}
      {activeTab !== 'audit' && <>
        <ProCard className="content-panel management-query" bordered={false}>
          <Form form={directoryForm} layout="inline" className="directory-filters management-query-form" onFinish={(values) => setDirectoryFilters({ ...values, keyword: values.keyword?.trim() })}>
            <Form.Item label="名称或编码" name="keyword"><Input allowClear /></Form.Item>
            <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>
            <Form.Item><Space><PlainButton  type="primary" htmlType="submit">查询</PlainButton>
            <PlainButton  onClick={() => { directoryForm.resetFields(); setDirectoryFilters({}); }}>重置</PlainButton></Space></Form.Item>
          </Form>
        </ProCard>
        <div className="management-toolbar"><Space wrap>
          <>
            <Button actionKey="IAM_ROLE_CREATE" type="primary" icon={<Plus size={16} />} onClick={() => setRoleModalOpen(true)}>新增角色</Button>
          </>
        </Space></div>
      </>}
      <Tabs className="page-sections management-tabs" activeKey={activeTab} onChange={(key) => { setActiveTab(key); directoryForm.resetFields(); setDirectoryFilters({}); }} items={[
        { key: 'roles', label: '角色目录', children: (<ProCard className="content-panel management-list" bordered={false}>
        <Table<Role> rowKey="id" loading={loading} dataSource={roles.filter(matchesDirectory)} pagination={false} locale={{ emptyText: '暂无角色' }} columns={[
          { title: '编码', dataIndex: 'code', key: 'code', width: 220, ellipsis: true },
          { title: '名称', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
          { title: '数据范围', dataIndex: 'dataScope', key: 'dataScope', width: 110, render: (value) => dataScopeLabels[value] ?? value },
          { title: '来源', dataIndex: 'builtIn', key: 'builtIn', width: 90, render: (_, role) => <Tag color={isBuiltIn(role) ? 'blue' : 'default'}>{isBuiltIn(role) ? '内置' : '自定义'}</Tag> },
          { title: '状态', key: 'status', width: 180, render: (_, role) => <><Tag color={role.status === 'ENABLED' ? 'green' : 'default'}>{role.status === 'ENABLED' ? '启用' : '停用'}</Tag><ConfiguredAction actionKey="IAM_ROLE_STATUS_CHANGE" permissionCodes={permissionCodes}><Radio.Group size="small" aria-label={`${role.name}状态`} value={role.status} disabled={isBuiltIn(role) || pendingRole === role.id} onChange={event => void mutateRole(role, 'IAM_ROLE_STATUS_CHANGE', () => iamApi.updateRoleStatus(role.id, event.target.value), '角色状态已更新')} options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} /></ConfiguredAction></> },
          { title: '操作', key: 'actions', width: 320, fixed: 'right', render: (_, role) => <Space className="management-row-actions">
            {permissionCodes.includes('IAM_ROLE_UPDATE') && <Button actionKey="IAM_ROLE_UPDATE" size="small" disabled={(isBuiltIn(role) && !systemAdministrator) || pendingRole === role.id} onClick={() => { editForm.setFieldsValue({ name: role.name, description: role.description ?? '' }); setEditingRole(role); }}>编辑</Button>}
            <Button actionKey="IAM_ROLE_PERMISSION_GRANT" size="small" onClick={() => { setSelectedRole(role); setRolePermissionModalOpen(true); }}>授权</Button>
            <Button actionKey="IAM_USER_ROLE_ASSIGN" size="small" disabled={role.status !== 'ENABLED'} onClick={() => setMemberRole(role)}>关联用户</Button>
            {permissionCodes.includes('IAM_ROLE_DELETE') && <Popconfirm title={`确认删除角色“${role.name}”？`} description="已关联用户的角色请先解绑用户。" onConfirm={() => mutateRole(role, 'IAM_ROLE_DELETE', () => iamApi.deleteRole(role.id), '角色已删除')}><Button actionKey="IAM_ROLE_DELETE" size="small" danger disabled={isBuiltIn(role) || pendingRole === role.id}>删除</Button></Popconfirm>}
          </Space> },
        ]} />
      </ProCard>) },
        { key: 'audit', label: '授权审计', children: <IamAuditPanel /> }
      ]} />

      <Modal title="新增角色" open={roleModalOpen} footer={null} onCancel={() => setRoleModalOpen(false)} destroyOnHidden width="min(640px, 92vw)">
        <Form form={roleForm} layout="vertical" initialValues={{ dataScope: 'SELF' }} onFinish={createRole}>
          <Form.Item label="角色编码" name="code" rules={[{ required: true, message: '请输入角色编码' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="角色名称" name="name" rules={[{ required: true, message: '请输入角色名称' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="数据范围" name="dataScope" rules={[{ required: true, message: '请选择数据范围' }]}><Select options={['ALL', 'REGION', 'SCHOOL', 'CLASS', 'FAMILY', 'SELF', 'CUSTOM'].map((value) => ({ value, label: dataScopeLabels[value] }))} /></Form.Item>
          <Form.Item label="说明" name="description"><Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} /></Form.Item>
          <div className="form-actions"><PlainButton  onClick={() => setRoleModalOpen(false)}>取消</PlainButton><Button actionKey="IAM_ROLE_CREATE" type="primary" htmlType="submit" loading={submitting}>创建角色</Button></div>
        </Form>
      </Modal>

      <RolePermissionTreeModal
        open={rolePermissionModalOpen}
        roles={roles}
        role={selectedRole}
        permissions={permissions}
        onClose={() => setRolePermissionModalOpen(false)}
      />
      <Modal title="编辑角色" open={!!editingRole} footer={null} onCancel={() => setEditingRole(undefined)} destroyOnHidden>
        <Form form={editForm} layout="vertical" onFinish={values => editingRole && mutateRole(editingRole, 'IAM_ROLE_UPDATE', async () => { await iamApi.updateRole(editingRole.id, values); setEditingRole(undefined); }, '角色已更新')}>
          <Form.Item label="角色编码"><Input aria-label="角色编码" value={editingRole?.code} readOnly /></Form.Item>
          <Form.Item label="数据范围"><Input aria-label="数据范围" value={editingRole && dataScopeLabels[editingRole.dataScope]} readOnly /></Form.Item>
          <Form.Item label="角色名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入角色名称' }]}><Input /></Form.Item>
          <Form.Item label="说明" name="description"><Input.TextArea /></Form.Item>
          <div className="form-actions"><PlainButton  onClick={() => setEditingRole(undefined)}>取消</PlainButton><Button actionKey="IAM_ROLE_UPDATE" htmlType="submit" type="primary" loading={!!pendingRole}>保存</Button></div>
        </Form>
      </Modal>
      {memberRole&&<RoleUsersModal role={memberRole} onClose={()=>setMemberRole(undefined)}/>}

    </div>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}

function isBuiltIn(role: Role) { return role.builtIn || role.type === 'BUILT_IN'; }
