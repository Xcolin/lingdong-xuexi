import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useState } from 'react';
import { Alert, Form, Input, Modal, Select, Space, Tabs, Tag, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { Plus, ShieldCheck, UserCog } from 'lucide-react';
import {
  iamApi,
  type CreateRoleInput,
  type Permission,
  type Role
} from '../../api/iam';
import { usersApi, type ManagedUser } from '../../api/users';
import { IamAuditPanel } from './IamAuditPanel';
import { RolePermissionTreeModal } from './RolePermissionTreeModal';
import { UserPermissionTreeModal } from './UserPermissionTreeModal';
import { RoleUsersModal } from './RoleUsersModal';

const dataScopeLabels: Record<string, string> = { ALL: '全部数据', REGION: '区域数据', SCHOOL: '学校数据', CLASS: '班级数据', SELF: '本人数据', CUSTOM: '自定义范围' };
interface DirectoryFilters { keyword?: string; status?: string }

export function IamManagementPage({ permissionCodes = ['IAM_ROLE_READ', 'IAM_PERMISSION_READ', 'IAM_USER_LIST'] }: { permissionCodes?: string[] }) {
  const [activeTab, setActiveTab] = useState('roles');
  const [directoryFilters, setDirectoryFilters] = useState<DirectoryFilters>({});
  const [directoryForm] = Form.useForm<DirectoryFilters>();
  const matchesDirectory = (item: Role) => (!directoryFilters.keyword || `${item.name} ${item.code}`.toLowerCase().includes(directoryFilters.keyword.toLowerCase())) && (!directoryFilters.status || item.status === directoryFilters.status);
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [selectedRole,setSelectedRole]=useState<Role>();
  const [memberRole,setMemberRole]=useState<Role>();
  const [loading, setLoading] = useState(true);

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [rolePermissionModalOpen, setRolePermissionModalOpen] = useState(false);
  const [userPermissionModalOpen, setUserPermissionModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [roleForm] = Form.useForm<CreateRoleInput>();


  useEffect(() => {
    void loadIamData();
  }, []);

  async function loadIamData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [loadedRoles, loadedPermissions, userPage] = await Promise.all([
        permissionCodes.includes('IAM_ROLE_READ') ? iamApi.listRoles() : Promise.resolve([]),
        permissionCodes.includes('IAM_PERMISSION_READ') ? iamApi.listPermissions() : Promise.resolve([]),
        permissionCodes.includes('IAM_USER_LIST') ? usersApi.list({ page: 1, pageSize: 100 }) : Promise.resolve({ items: [] })
      ]);
      setRoles(loadedRoles);
      setPermissions(loadedPermissions);
      setUsers(userPage.items);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
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
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="iam.iam-management-page.5" size="small" onClick={() => void loadIamData()}>重试</Button>} />}
      {activeTab !== 'audit' && <>
        <ProCard className="content-panel management-query" bordered={false}>
          <Form form={directoryForm} layout="inline" className="directory-filters management-query-form" onFinish={(values) => setDirectoryFilters({ ...values, keyword: values.keyword?.trim() })}>
            <Form.Item label="名称或编码" name="keyword"><Input allowClear /></Form.Item>
            <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>
            <Form.Item><Space><Button actionKey="iam.directory.query" type="primary" htmlType="submit">查询</Button>
            <Button actionKey="iam.directory.reset" onClick={() => { directoryForm.resetFields(); setDirectoryFilters({}); }}>重置</Button></Space></Form.Item>
          </Form>
        </ProCard>
        <div className="management-toolbar"><Space wrap>
          <>
            <Button actionKey="IAM_ROLE_CREATE" type="primary" icon={<Plus size={16} />} onClick={() => setRoleModalOpen(true)}>新增角色</Button>
            <Button actionKey="IAM_ROLE_PERMISSION_GRANT" icon={<ShieldCheck size={16} />} onClick={() => { setSelectedRole(undefined); setRolePermissionModalOpen(true); }}>配置角色权限</Button>
            <Button actionKey="IAM_USER_PERMISSION_CONFIGURE" icon={<UserCog size={16} />} onClick={() => setUserPermissionModalOpen(true)}>配置用户权限</Button>
          </>
        </Space></div>
      </>}
      <Tabs className="page-sections management-tabs" activeKey={activeTab} onChange={(key) => { setActiveTab(key); directoryForm.resetFields(); setDirectoryFilters({}); }} items={[
        { key: 'roles', label: '角色目录', children: (<ProCard className="content-panel management-list" bordered={false}>
        <Table<Role> rowKey="id" loading={loading} dataSource={roles.filter(matchesDirectory)} pagination={false} locale={{ emptyText: '暂无角色' }} columns={[
          { title: '编码', dataIndex: 'code', key: 'code', width: 220, ellipsis: true },
          { title: '名称', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
          { title: '数据范围', dataIndex: 'dataScope', key: 'dataScope', width: 110, render: (value) => dataScopeLabels[value] ?? value },
          { title: '来源', dataIndex: 'builtIn', key: 'builtIn', width: 90, render: (builtIn) => <Tag color={builtIn ? 'blue' : 'default'}>{builtIn ? '内置' : '自定义'}</Tag> },
          { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag> },
{title:'操作',key:'actions',width:220,fixed:'right',render:(_,role)=><Space className="management-row-actions"><Button actionKey="iam.role-row.authorize" size="small" onClick={()=>{setSelectedRole(role);setRolePermissionModalOpen(true);}}>授权</Button><Button actionKey="iam.role-row.users" size="small" disabled={role.status!=='ENABLED'} onClick={()=>setMemberRole(role)}>关联用户</Button></Space>},
        ]} />
      </ProCard>) },
        { key: 'audit', label: '授权审计', children: <IamAuditPanel /> }
      ]} />

      <Modal title="新增角色" open={roleModalOpen} footer={null} onCancel={() => setRoleModalOpen(false)} destroyOnHidden width="min(640px, 92vw)">
        <Form form={roleForm} layout="vertical" initialValues={{ dataScope: 'SELF' }} onFinish={createRole}>
          <Form.Item label="角色编码" name="code" rules={[{ required: true, message: '请输入角色编码' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="角色名称" name="name" rules={[{ required: true, message: '请输入角色名称' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="数据范围" name="dataScope" rules={[{ required: true, message: '请选择数据范围' }]}><Select options={['ALL', 'REGION', 'SCHOOL', 'CLASS', 'SELF', 'CUSTOM'].map((value) => ({ value, label: dataScopeLabels[value] }))} /></Form.Item>
          <Form.Item label="说明" name="description"><Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} /></Form.Item>
          <div className="form-actions"><Button actionKey="iam.iam-management-page.6" onClick={() => setRoleModalOpen(false)}>取消</Button><Button actionKey="iam.iam-management-page.7" type="primary" htmlType="submit" loading={submitting}>创建角色</Button></div>
        </Form>
      </Modal>

      <RolePermissionTreeModal
        open={rolePermissionModalOpen}
        roles={roles}
        role={selectedRole}
        permissions={permissions}
        onClose={() => setRolePermissionModalOpen(false)}
      />
      <UserPermissionTreeModal open={userPermissionModalOpen} users={users} permissions={permissions} actionPrefix="iam.user-permission-tree" onClose={()=>setUserPermissionModalOpen(false)}/>
      {memberRole&&<RoleUsersModal role={memberRole} onClose={()=>setMemberRole(undefined)}/>}

    </div>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
