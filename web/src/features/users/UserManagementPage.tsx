import { useEffect, useState, type ReactNode } from 'react';
import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { CircleCheck, CircleOff, LockKeyhole, Search, UserPlus } from 'lucide-react';
import { usersApi, type CreateUserInput, type ManagedUser, type UserDirectoryPage, type UserStatus, type UserType } from '../../api/users';

const PAGE_SIZE = 20;

const userTypeOptions: Array<{ value: UserType; label: string }> = [
  { value: 'PLATFORM', label: '平台账号' },
  { value: 'ORGANIZATION', label: '机构账号' },
  { value: 'FAMILY', label: '家长账号' },
  { value: 'STUDENT', label: '学生账号' }
];

const statusOptions: Array<{ value: UserStatus; label: string }> = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' },
  { value: 'LOCKED', label: '锁定' }
];

interface FilterValues {
  keyword?: string;
  type?: UserType;
  status?: UserStatus;
}

export function UserManagementPage() {
  const [directory, setDirectory] = useState<UserDirectoryPage>({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 });
  const [filters, setFilters] = useState<FilterValues>({});
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [filterForm] = Form.useForm<FilterValues>();
  const [createForm] = Form.useForm<CreateUserInput>();

  useEffect(() => {
    void loadUsers({}, 1);
  }, []);

  async function loadUsers(nextFilters: FilterValues, page: number): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await usersApi.list({ ...nextFilters, page, pageSize: PAGE_SIZE });
      setDirectory(response);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function search(values: FilterValues): Promise<void> {
    const nextFilters = {
      keyword: values.keyword?.trim() || undefined,
      type: values.type,
      status: values.status
    };
    setFilters(nextFilters);
    await loadUsers(nextFilters, 1);
  }

  async function createUser(values: CreateUserInput): Promise<void> {
    setSubmitting(true);
    try {
      await usersApi.create({ ...values, mobile: values.mobile?.trim() || undefined });
      message.success('用户已创建');
      setCreateModalOpen(false);
      createForm.resetFields();
      await loadUsers(filters, directory.page);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function updateStatus(user: ManagedUser, status: UserStatus): Promise<void> {
    try {
      await usersApi.updateStatus(user.id, status);
      message.success(`账号已${statusLabel(status)}`);
      await loadUsers(filters, directory.page);
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>用户管理</h1>
        <Button type="primary" icon={<UserPlus size={16} />} onClick={() => setCreateModalOpen(true)}>新增用户</Button>
      </div>

      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button size="small" onClick={() => void loadUsers(filters, directory.page)}>重试</Button>} />}

      <ProCard className="content-panel" bordered={false}>
        <Form form={filterForm} layout="inline" className="directory-filters" onFinish={search}>
          <Form.Item label="账号或名称" name="keyword"><Input allowClear /></Form.Item>
          <Form.Item label="用户类型" name="type"><Select allowClear options={userTypeOptions} className="filter-select" /></Form.Item>
          <Form.Item label="账号状态" name="status"><Select allowClear options={statusOptions} className="filter-select" /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" icon={<Search size={16} />}>查询</Button></Form.Item>
        </Form>

        <Table<ManagedUser>
          rowKey="id"
          loading={loading}
          dataSource={directory.items}
          locale={{ emptyText: '暂无匹配用户' }}
          pagination={{
            current: directory.page,
            pageSize: directory.pageSize,
            total: directory.total,
            showSizeChanger: false,
            onChange: (page) => void loadUsers(filters, page)
          }}
          columns={[
            { title: '账号', dataIndex: 'username', key: 'username', width: 170 },
            { title: '名称', dataIndex: 'displayName', key: 'displayName', width: 150 },
            { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 140, render: (mobile: string | null) => mobile ?? '-' },
            { title: '类型', dataIndex: 'type', key: 'type', width: 120, render: (type: UserType) => userTypeLabel(type) },
            { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (status: UserStatus) => <Tag color={statusColor(status)}>{statusLabel(status)}</Tag> },
            { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatTime },
            {
              title: '操作', key: 'actions', width: 128,
              render: (_, user) => <Space size={2}>
                {user.status !== 'ENABLED' && <StatusAction user={user} status="ENABLED" icon={<CircleCheck size={16} />} />}
                {user.status !== 'DISABLED' && <StatusAction user={user} status="DISABLED" icon={<CircleOff size={16} />} />}
                {user.status !== 'LOCKED' && <StatusAction user={user} status="LOCKED" icon={<LockKeyhole size={16} />} />}
              </Space>
            }
          ]}
        />
      </ProCard>

      <Modal title="新增用户" open={createModalOpen} footer={null} onCancel={() => setCreateModalOpen(false)} destroyOnHidden>
        <Form form={createForm} layout="vertical" initialValues={{ type: 'PLATFORM' }} onFinish={createUser}>
          <Form.Item label="用户账号" name="username" rules={[{ required: true, message: '请输入用户账号' }, { max: 64, message: '用户账号不能超过 64 个字符' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="用户名称" name="displayName" rules={[{ required: true, message: '请输入用户名称' }, { max: 64, message: '用户名称不能超过 64 个字符' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="手机号" name="mobile" rules={[{ max: 32, message: '手机号不能超过 32 个字符' }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="用户类型" name="type" rules={[{ required: true, message: '请选择用户类型' }]}><Select options={userTypeOptions} /></Form.Item>
          <div className="form-actions"><Button onClick={() => setCreateModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit" loading={submitting}>创建用户</Button></div>
        </Form>
      </Modal>
    </div>
  );

  function StatusAction({ user, status, icon }: { user: ManagedUser; status: UserStatus; icon: ReactNode }) {
    const label = statusLabel(status);
    return (
      <Tooltip title={label}>
        <Popconfirm title={`确认${label}该用户？`} onConfirm={() => void updateStatus(user, status)}>
          <Button type="text" icon={icon} aria-label={`${label} ${user.displayName}`} />
        </Popconfirm>
      </Tooltip>
    );
  }
}

function userTypeLabel(type: UserType): string {
  return userTypeOptions.find((item) => item.value === type)?.label ?? type;
}

function statusLabel(status: UserStatus): string {
  return statusOptions.find((item) => item.value === status)?.label ?? status;
}

function statusColor(status: UserStatus): string {
  return status === 'ENABLED' ? 'green' : status === 'LOCKED' ? 'orange' : 'default';
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
