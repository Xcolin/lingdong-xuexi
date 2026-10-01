import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useState } from 'react';
import { Alert, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { KeyRound, Plus, ShieldCheck, Trash2, UserCog } from 'lucide-react';
import {
  iamApi,
  type CreatePermissionInput,
  type CreateRoleInput,
  type Permission,
  type PermissionAssignment,
  type PermissionEffect,
  type Role
} from '../../api/iam';
import { usersApi, type ManagedUser } from '../../api/users';
import { IamAuditPanel } from './IamAuditPanel';

interface PermissionConfigurationInput {
  subjectId: string;
  permissionId: string;
  effect: PermissionEffect;
}

export function IamManagementPage() {
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [roleAssignments, setRoleAssignments] = useState<PermissionAssignment[]>([]);
  const [userAssignments, setUserAssignments] = useState<PermissionAssignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [assignmentLoading, setAssignmentLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [permissionModalOpen, setPermissionModalOpen] = useState(false);
  const [rolePermissionModalOpen, setRolePermissionModalOpen] = useState(false);
  const [userPermissionModalOpen, setUserPermissionModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [roleForm] = Form.useForm<CreateRoleInput>();
  const [permissionForm] = Form.useForm<CreatePermissionInput>();
  const [rolePermissionForm] = Form.useForm<PermissionConfigurationInput>();
  const [userPermissionForm] = Form.useForm<PermissionConfigurationInput>();

  useEffect(() => {
    void loadIamData();
  }, []);

  async function loadIamData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [loadedRoles, loadedPermissions, userPage] = await Promise.all([
        iamApi.listRoles(),
        iamApi.listPermissions(),
        usersApi.list({ page: 1, pageSize: 100 })
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

  async function createPermission(values: CreatePermissionInput): Promise<void> {
    await submitAction(async () => {
      await iamApi.createPermission({ ...values, parentId: values.parentId || undefined });
      setPermissionModalOpen(false);
      permissionForm.resetFields();
      await loadIamData();
    }, '权限已创建');
  }

  async function loadRoleAssignments(roleId: string): Promise<void> {
    await loadAssignments(() => iamApi.listRolePermissions(roleId), setRoleAssignments);
  }

  async function loadUserAssignments(userId: string): Promise<void> {
    await loadAssignments(() => iamApi.listUserPermissions(userId), setUserAssignments);
  }

  async function loadAssignments(
    loader: () => Promise<PermissionAssignment[]>,
    setter: (assignments: PermissionAssignment[]) => void
  ): Promise<void> {
    setAssignmentLoading(true);
    try {
      setter(await loader());
    } catch (error) {
      setter([]);
      message.error(toMessage(error));
    } finally {
      setAssignmentLoading(false);
    }
  }

  async function configureRolePermission(values: PermissionConfigurationInput): Promise<void> {
    await submitAction(async () => {
      await iamApi.configureRolePermission(values.subjectId, values.permissionId, values.effect);
      await loadRoleAssignments(values.subjectId);
      rolePermissionForm.setFieldsValue({ permissionId: undefined, effect: 'ALLOW' });
    }, '角色权限已保存');
  }

  async function configureUserPermission(values: PermissionConfigurationInput): Promise<void> {
    await submitAction(async () => {
      await iamApi.configureUserPermission(values.subjectId, values.permissionId, values.effect);
      await loadUserAssignments(values.subjectId);
      userPermissionForm.setFieldsValue({ permissionId: undefined, effect: 'ALLOW' });
    }, '用户权限已保存');
  }

  async function removeRolePermission(permissionId: string): Promise<void> {
    const roleId = rolePermissionForm.getFieldValue('subjectId');
    if (!roleId) return;
    await submitAction(async () => {
      await iamApi.removeRolePermission(roleId, permissionId);
      await loadRoleAssignments(roleId);
    }, '角色显式权限已撤销');
  }

  async function removeUserPermission(permissionId: string): Promise<void> {
    const userId = userPermissionForm.getFieldValue('subjectId');
    if (!userId) return;
    await submitAction(async () => {
      await iamApi.removeUserPermission(userId, permissionId);
      await loadUserAssignments(userId);
    }, '用户显式权限已撤销');
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

  function closeRolePermissionModal(): void {
    setRolePermissionModalOpen(false);
    setRoleAssignments([]);
    rolePermissionForm.resetFields();
  }

  function closeUserPermissionModal(): void {
    setUserPermissionModalOpen(false);
    setUserAssignments([]);
    userPermissionForm.resetFields();
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>角色与权限</h1>
        <Space wrap>
          <Button actionKey="iam.iam-management-page.1" icon={<Plus size={16} />} onClick={() => setRoleModalOpen(true)}>新增角色</Button>
          <Button actionKey="iam.iam-management-page.2" icon={<KeyRound size={16} />} onClick={() => setPermissionModalOpen(true)}>新增权限</Button>
          <Button actionKey="iam.iam-management-page.3" icon={<ShieldCheck size={16} />} onClick={() => setRolePermissionModalOpen(true)}>配置角色权限</Button>
          <Button actionKey="iam.iam-management-page.4" type="primary" icon={<UserCog size={16} />} onClick={() => setUserPermissionModalOpen(true)}>配置用户权限</Button>
        </Space>
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="iam.iam-management-page.5" size="small" onClick={() => void loadIamData()}>重试</Button>} />}
      <Tabs className="page-sections" items={[
        { key: 'roles', label: '角色目录', children: (<ProCard className="content-panel" title="角色目录" bordered={false}>
        <Table<Role> rowKey="id" loading={loading} dataSource={roles} pagination={false} locale={{ emptyText: '暂无角色' }} columns={[
          { title: '编码', dataIndex: 'code', key: 'code' },
          { title: '名称', dataIndex: 'name', key: 'name' },
          { title: '数据范围', dataIndex: 'dataScope', key: 'dataScope', width: 110 },
          { title: '来源', dataIndex: 'builtIn', key: 'builtIn', width: 90, render: (builtIn) => <Tag color={builtIn ? 'blue' : 'default'}>{builtIn ? '内置' : '自定义'}</Tag> },
          { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag> }
        ]} />
      </ProCard>) },
        { key: 'permissions', label: '权限目录', children: (<ProCard className="content-panel" title="权限目录" bordered={false}>
        <Table<Permission> rowKey="id" loading={loading} dataSource={permissions} pagination={{ pageSize: 10, showSizeChanger: false }} locale={{ emptyText: '暂无权限' }} columns={[
          { title: '编码', dataIndex: 'code', key: 'code' },
          { title: '名称', dataIndex: 'name', key: 'name' },
          { title: '资源类型', dataIndex: 'resourceType', key: 'resourceType', width: 110 },
          { title: '客户端', dataIndex: 'client', key: 'client', width: 100 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag> }
        ]} />
      </ProCard>) },
        { key: 'audit', label: '授权审计', children: <IamAuditPanel /> }
      ]} />

      <Modal title="新增角色" open={roleModalOpen} footer={null} onCancel={() => setRoleModalOpen(false)} destroyOnHidden>
        <Form form={roleForm} layout="vertical" initialValues={{ dataScope: 'SELF' }} onFinish={createRole}>
          <Form.Item label="角色编码" name="code" rules={[{ required: true, message: '请输入角色编码' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="角色名称" name="name" rules={[{ required: true, message: '请输入角色名称' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="数据范围" name="dataScope" rules={[{ required: true, message: '请选择数据范围' }]}><Select options={['ALL', 'REGION', 'SCHOOL', 'CLASS', 'SELF', 'CUSTOM'].map((value) => ({ value, label: value }))} /></Form.Item>
          <Form.Item label="说明" name="description"><Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} /></Form.Item>
          <div className="form-actions"><Button actionKey="iam.iam-management-page.6" onClick={() => setRoleModalOpen(false)}>取消</Button><Button actionKey="iam.iam-management-page.7" type="primary" htmlType="submit" loading={submitting}>创建角色</Button></div>
        </Form>
      </Modal>

      <Modal title="新增权限" open={permissionModalOpen} footer={null} onCancel={() => setPermissionModalOpen(false)} destroyOnHidden>
        <Form form={permissionForm} layout="vertical" initialValues={{ resourceType: 'OPERATION', client: 'WEB' }} onFinish={createPermission}>
          <Form.Item label="权限编码" name="code" rules={[{ required: true, message: '请输入权限编码' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="权限名称" name="name" rules={[{ required: true, message: '请输入权限名称' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="资源类型" name="resourceType" rules={[{ required: true, message: '请选择资源类型' }]}><Select options={['MENU', 'PAGE', 'BUTTON', 'OPERATION'].map((value) => ({ value, label: value }))} /></Form.Item>
          <Form.Item label="客户端" name="client" rules={[{ required: true, message: '请选择客户端' }]}><Select options={['WEB', 'MINIAPP', 'BOTH'].map((value) => ({ value, label: value }))} /></Form.Item>
          <Form.Item label="父级权限" name="parentId"><Select allowClear options={permissions.map((permission) => ({ value: permission.id, label: `${permission.name}（${permission.code}）` }))} /></Form.Item>
          <div className="form-actions"><Button actionKey="iam.iam-management-page.8" onClick={() => setPermissionModalOpen(false)}>取消</Button><Button actionKey="iam.iam-management-page.9" type="primary" htmlType="submit" loading={submitting}>创建权限</Button></div>
        </Form>
      </Modal>

      <PermissionConfigurationModal
        title="配置角色权限"
        subjectLabel="角色"
        subjectOptions={roles.map((role) => ({ value: role.id, label: `${role.name}（${role.code}）` }))}
        open={rolePermissionModalOpen}
        form={rolePermissionForm}
        assignments={roleAssignments}
        permissions={permissions}
        loading={assignmentLoading}
        submitting={submitting}
        submitText="保存角色权限"
        onSubjectChange={loadRoleAssignments}
        onSubmit={configureRolePermission}
        onRemove={removeRolePermission}
        onClose={closeRolePermissionModal}
      />
      <PermissionConfigurationModal
        title="配置用户权限"
        subjectLabel="用户"
        subjectOptions={users.map((user) => ({ value: user.id, label: `${user.displayName}（${user.username}）` }))}
        open={userPermissionModalOpen}
        form={userPermissionForm}
        assignments={userAssignments}
        permissions={permissions}
        loading={assignmentLoading}
        submitting={submitting}
        submitText="保存用户权限"
        onSubjectChange={loadUserAssignments}
        onSubmit={configureUserPermission}
        onRemove={removeUserPermission}
        onClose={closeUserPermissionModal}
      />
    </div>
  );
}

interface PermissionConfigurationModalProps {
  title: string;
  subjectLabel: string;
  subjectOptions: Array<{ value: string; label: string }>;
  open: boolean;
  form: ReturnType<typeof Form.useForm<PermissionConfigurationInput>>[0];
  assignments: PermissionAssignment[];
  permissions: Permission[];
  loading: boolean;
  submitting: boolean;
  submitText: string;
  onSubjectChange: (subjectId: string) => Promise<void>;
  onSubmit: (values: PermissionConfigurationInput) => Promise<void>;
  onRemove: (permissionId: string) => Promise<void>;
  onClose: () => void;
}

function PermissionConfigurationModal({
  title, subjectLabel, subjectOptions, open, form, assignments, permissions, loading, submitting,
  submitText, onSubjectChange, onSubmit, onRemove, onClose
}: PermissionConfigurationModalProps) {
  const selectedSubjectId = Form.useWatch('subjectId', form);
  const permissionById = new Map(permissions.map((permission) => [permission.id, permission]));
  return (
    <Modal title={title} open={open} footer={null} onCancel={onClose} width={760} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ effect: 'ALLOW' }} onFinish={onSubmit}>
        <Form.Item label={subjectLabel} name="subjectId" rules={[{ required: true, message: `请选择${subjectLabel}` }]}>
          <Select showSearch optionFilterProp="label" options={subjectOptions} onChange={(value) => void onSubjectChange(value)} />
        </Form.Item>
        <Space align="start" wrap size="middle">
          <Form.Item label="权限" name="permissionId" rules={[{ required: true, message: '请选择权限' }]}>
            <Select
              style={{ width: 320 }}
              showSearch
              optionFilterProp="label"
              options={permissions.filter((permission) => permission.status === 'ENABLED').map((permission) => ({
                value: permission.id,
                label: `${permission.name}（${permission.code}）`
              }))}
            />
          </Form.Item>
          <Form.Item label="效果" name="effect" rules={[{ required: true, message: '请选择权限效果' }]}>
            <Segmented options={[{ label: '允许', value: 'ALLOW' }, { label: '禁止', value: 'DENY' }]} />
          </Form.Item>
        </Space>
        <div className="form-actions"><Button actionKey="iam.iam-management-page.10" type="primary" htmlType="submit" loading={submitting} disabled={!selectedSubjectId}>{submitText}</Button></div>
      </Form>
      <Table<PermissionAssignment>
        rowKey="permissionId"
        size="small"
        loading={loading}
        dataSource={assignments}
        pagination={false}
        locale={{ emptyText: selectedSubjectId ? '暂无显式权限配置' : `请先选择${subjectLabel}` }}
        columns={[
          {
            title: '权限',
            dataIndex: 'permissionId',
            key: 'permissionId',
            render: (permissionId: string) => permissionById.get(permissionId)?.name ?? permissionId
          },
          {
            title: '效果',
            dataIndex: 'effect',
            key: 'effect',
            width: 100,
            render: (effect: PermissionEffect) => <Tag color={effect === 'ALLOW' ? 'green' : 'red'}>{effect === 'ALLOW' ? '允许' : '禁止'}</Tag>
          },
          {
            title: '操作',
            key: 'action',
            width: 80,
            render: (_, assignment) => {
              const permissionName = permissionById.get(assignment.permissionId)?.name ?? assignment.permissionId;
              return (
                <Popconfirm title="确认撤销该显式权限？" onConfirm={() => void onRemove(assignment.permissionId)}>
                  <Tooltip title="撤销显式权限">
                    <Button actionKey="iam.iam-management-page.11" danger type="text" icon={<Trash2 size={16} />} aria-label={`撤销 ${permissionName}`} />
                  </Tooltip>
                </Popconfirm>
              );
            }
          }
        ]}
      />
    </Modal>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
