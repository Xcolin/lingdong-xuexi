import { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { KeyRound, Plus, ShieldCheck } from 'lucide-react';
import { iamApi, type CreatePermissionInput, type CreateRoleInput, type Permission, type Role } from '../../api/iam';

export function IamManagementPage() {
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [permissionModalOpen, setPermissionModalOpen] = useState(false);
  const [grantModalOpen, setGrantModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [roleForm] = Form.useForm<CreateRoleInput>();
  const [permissionForm] = Form.useForm<CreatePermissionInput>();
  const [grantForm] = Form.useForm<{ roleId: string; permissionId: string }>();

  useEffect(() => {
    void loadIamData();
  }, []);

  async function loadIamData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [loadedRoles, loadedPermissions] = await Promise.all([iamApi.listRoles(), iamApi.listPermissions()]);
      setRoles(loadedRoles);
      setPermissions(loadedPermissions);
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

  async function grantRolePermission(values: { roleId: string; permissionId: string }): Promise<void> {
    await submitAction(async () => {
      await iamApi.grantRolePermission(values.roleId, values.permissionId);
      setGrantModalOpen(false);
      grantForm.resetFields();
    }, '角色权限已授予');
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
    <div className="page-stack">
      <div className="page-heading">
        <h1>角色与权限</h1>
        <Space wrap>
          <Button icon={<Plus size={16} />} onClick={() => setRoleModalOpen(true)}>新增角色</Button>
          <Button icon={<KeyRound size={16} />} onClick={() => setPermissionModalOpen(true)}>新增权限</Button>
          <Button type="primary" icon={<ShieldCheck size={16} />} onClick={() => setGrantModalOpen(true)}>授予角色权限</Button>
        </Space>
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button size="small" onClick={() => void loadIamData()}>重试</Button>} />}
      <ProCard className="content-panel" title="角色目录" bordered={false}>
        <Table<Role> rowKey="id" loading={loading} dataSource={roles} pagination={false} locale={{ emptyText: '暂无角色' }} columns={[
          { title: '编码', dataIndex: 'code', key: 'code' },
          { title: '名称', dataIndex: 'name', key: 'name' },
          { title: '数据范围', dataIndex: 'dataScope', key: 'dataScope', width: 110 },
          { title: '来源', dataIndex: 'builtIn', key: 'builtIn', width: 90, render: (builtIn) => <Tag color={builtIn ? 'blue' : 'default'}>{builtIn ? '内置' : '自定义'}</Tag> },
          { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag> }
        ]} />
      </ProCard>
      <ProCard className="content-panel" title="权限目录" bordered={false}>
        <Table<Permission> rowKey="id" loading={loading} dataSource={permissions} pagination={{ pageSize: 10, showSizeChanger: false }} locale={{ emptyText: '暂无权限' }} columns={[
          { title: '编码', dataIndex: 'code', key: 'code' },
          { title: '名称', dataIndex: 'name', key: 'name' },
          { title: '资源类型', dataIndex: 'resourceType', key: 'resourceType', width: 110 },
          { title: '客户端', dataIndex: 'client', key: 'client', width: 100 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (status) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag> }
        ]} />
      </ProCard>

      <Modal title="新增角色" open={roleModalOpen} footer={null} onCancel={() => setRoleModalOpen(false)} destroyOnHidden>
        <Form form={roleForm} layout="vertical" initialValues={{ dataScope: 'SELF' }} onFinish={createRole}>
          <Form.Item label="角色编码" name="code" rules={[{ required: true, message: '请输入角色编码' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="角色名称" name="name" rules={[{ required: true, message: '请输入角色名称' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="数据范围" name="dataScope" rules={[{ required: true, message: '请选择数据范围' }]}><Select options={['ALL', 'REGION', 'SCHOOL', 'CLASS', 'SELF', 'CUSTOM'].map((value) => ({ value, label: value }))} /></Form.Item>
          <Form.Item label="说明" name="description"><Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} /></Form.Item>
          <div className="form-actions"><Button onClick={() => setRoleModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit" loading={submitting}>创建角色</Button></div>
        </Form>
      </Modal>

      <Modal title="新增权限" open={permissionModalOpen} footer={null} onCancel={() => setPermissionModalOpen(false)} destroyOnHidden>
        <Form form={permissionForm} layout="vertical" initialValues={{ resourceType: 'OPERATION', client: 'WEB' }} onFinish={createPermission}>
          <Form.Item label="权限编码" name="code" rules={[{ required: true, message: '请输入权限编码' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="权限名称" name="name" rules={[{ required: true, message: '请输入权限名称' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="资源类型" name="resourceType" rules={[{ required: true, message: '请选择资源类型' }]}><Select options={['MENU', 'PAGE', 'BUTTON', 'OPERATION'].map((value) => ({ value, label: value }))} /></Form.Item>
          <Form.Item label="客户端" name="client" rules={[{ required: true, message: '请选择客户端' }]}><Select options={['WEB', 'MINIAPP', 'BOTH'].map((value) => ({ value, label: value }))} /></Form.Item>
          <Form.Item label="父级权限" name="parentId"><Select allowClear options={permissions.map((permission) => ({ value: permission.id, label: `${permission.name}（${permission.code}）` }))} /></Form.Item>
          <div className="form-actions"><Button onClick={() => setPermissionModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit" loading={submitting}>创建权限</Button></div>
        </Form>
      </Modal>

      <Modal title="授予角色权限" open={grantModalOpen} footer={null} onCancel={() => setGrantModalOpen(false)} destroyOnHidden>
        <Form form={grantForm} layout="vertical" onFinish={grantRolePermission}>
          <Form.Item label="角色" name="roleId" rules={[{ required: true, message: '请选择角色' }]}><Select options={roles.map((role) => ({ value: role.id, label: `${role.name}（${role.code}）` }))} /></Form.Item>
          <Form.Item label="权限" name="permissionId" rules={[{ required: true, message: '请选择权限' }]}><Select options={permissions.map((permission) => ({ value: permission.id, label: `${permission.name}（${permission.code}）` }))} /></Form.Item>
          <div className="form-actions"><Button onClick={() => setGrantModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit" loading={submitting}>确认授权</Button></div>
        </Form>
      </Modal>
    </div>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
