import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useState } from 'react';
import { Alert, Form, Input, Popconfirm, Select, Space, Tag, TreeSelect, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { KeyRound, Search, UserPlus } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import {organizationApi,type OrganizationNode} from '../../api/organization';
import {ResetUserPasswordModal} from './ResetUserPasswordModal';
import { usersApi, type ManagedUser, type MutableUserStatus, type UserDirectoryPage, type UserStatus, type UserType } from '../../api/users';
import { formatDateTime as formatTime } from '../../utils/datetime';

import {CreateUserDrawer} from './CreateUserDrawer';
import {UserPermissionTreeModal} from '../iam/UserPermissionTreeModal';

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
  { value: 'LOCKED', label: '锁定' },
  { value: 'CANCELLED', label: '已注销' }
];

interface FilterValues {
  organizationId?:string;
  keyword?: string;
  type?: UserType;
  status?: UserStatus;
}

function organizationFilters(nodes:OrganizationNode[]):{title:string;value:string;children:ReturnType<typeof organizationFilters>}[]{return nodes.map(n=>({title:n.name,value:n.id,children:organizationFilters(n.children??[])}));}
export function UserManagementPage({currentUser}:{currentUser:Pick<CurrentUser,'roleCodes'|'permissionCodes'>}) {
  const canResetPassword=currentUser.permissionCodes.includes('IAM_USER_PASSWORD_SET');
  const [passwordUser,setPasswordUser]=useState<ManagedUser>();
  const [organizationNodes,setOrganizationNodes]=useState<OrganizationNode[]>([]);
  const [organizationError,setOrganizationError]=useState<string>();
  const [directory, setDirectory] = useState<UserDirectoryPage>({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 });
  const [filters, setFilters] = useState<FilterValues>({});
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [permissionUser,setPermissionUser]=useState<ManagedUser>();
  const [filterForm] = Form.useForm<FilterValues>();

  async function reloadOrganizations(){setOrganizationError(undefined);try{setOrganizationNodes(await organizationApi.listTree());}catch(error){setOrganizationError(toMessage(error));}}


  useEffect(() => {
    void loadUsers({}, 1);
    let active=true;
    void organizationApi.listTree().then(nodes=>{if(active)setOrganizationNodes(nodes);}).catch(error=>{if(active)setOrganizationError(toMessage(error));});
    return()=>{active=false;};
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
      status: values.status,
      organizationId:values.organizationId
    };
    setFilters(nextFilters);
    await loadUsers(nextFilters, 1);
  }

  async function updateStatus(user: ManagedUser, status: MutableUserStatus): Promise<void> {
    try {
      await usersApi.updateStatus(user.id, status);
      message.success(`账号已${statusLabel(status)}`);
      await loadUsers(filters, directory.page);
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  return (
    <div className="page-stack management-page user-management-page">
      {organizationError&&<Alert type="error" showIcon message={`组织机构加载失败：${organizationError}`} action={<Button actionKey="users.user-management-page.2" size="small" onClick={()=>void reloadOrganizations()}>重试</Button>}/>}
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="users.user-management-page.2" size="small" onClick={() => void loadUsers(filters, directory.page)}>重试</Button>} />}

      <ProCard className="content-panel management-query" bordered={false}>
        <Form name="user-directory" form={filterForm} layout="inline" className="directory-filters management-query-form" onFinish={search}>
          <Form.Item label="账号或名称" name="keyword"><Input allowClear /></Form.Item>
          <Form.Item label="用户类型" name="type"><Select allowClear options={userTypeOptions} className="filter-select" /></Form.Item>
          <Form.Item label="账号状态" name="status"><Select allowClear options={statusOptions} className="filter-select" /></Form.Item>
          <Form.Item label="所属组织机构" name="organizationId"><TreeSelect allowClear showSearch treeNodeFilterProp="title" treeData={organizationFilters(organizationNodes)} className="filter-select" /></Form.Item>
          <Form.Item><Space><Button actionKey="users.user-management-page.3" type="primary" htmlType="submit" icon={<Search size={16} />}>查询</Button>
          <Button actionKey="users.directory.reset" onClick={() => { filterForm.resetFields(); void search({}); }}>重置</Button></Space></Form.Item>
        </Form>
      </ProCard>
      <div className="management-toolbar"><Button actionKey="IAM_USER_CREATE" type="primary" icon={<UserPlus size={16} />} onClick={() => setCreateModalOpen(true)}>新增用户</Button></div>
      <ProCard className="content-panel management-list" bordered={false}>
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
            { title: '账号', dataIndex: 'username', key: 'username', width: 120, ellipsis: true },
            { title: '名称', dataIndex: 'displayName', key: 'displayName', width: 120, ellipsis: true },
            { title: '机构名称', dataIndex: 'organizationNames', key: 'organizationNames', width: 200, ellipsis: true, render: (names?: string[]) => names?.length ? names.join('、') : '—' },
            { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 130, render: (mobile: string | null) => mobile ?? '-' },
            { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (type: UserType) => userTypeLabel(type) },
            { title: '状态', dataIndex: 'status', key: 'status', width: 80, render: (status: UserStatus) => <Tag color={statusColor(status)}>{statusLabel(status)}</Tag> },
            { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160, render: formatTime },
            {
              title: '操作', key: 'actions', width: 340, fixed: 'right',
              render: (_, user) => user.status === 'CANCELLED' ? null : <Space size={8} className="management-row-actions">
                <Button actionKey="users.row.authorize" size="small" onClick={()=>setPermissionUser(user)}>授权</Button>
                {canResetPassword&&user.type!=='STUDENT'&&<Button actionKey="IAM_USER_PASSWORD_SET" size="small" icon={<KeyRound size={14}/>} onClick={()=>setPasswordUser(user)}>重置密码</Button>}
                {user.status !== 'ENABLED' && <StatusAction user={user} status="ENABLED" />}
                {user.status !== 'DISABLED' && <StatusAction user={user} status="DISABLED" />}
                {user.status !== 'LOCKED' && <StatusAction user={user} status="LOCKED" />}
              </Space>
            }
          ]}
        />
      </ProCard>

      <CreateUserDrawer open={createModalOpen} onClose={()=>setCreateModalOpen(false)} onSaved={()=>loadUsers(filters,directory.page)}/>
      {permissionUser&&<UserPermissionTreeModal open user={permissionUser} onClose={()=>setPermissionUser(undefined)}/>}
      {passwordUser&&canResetPassword&&<ResetUserPasswordModal user={passwordUser} onClose={()=>setPasswordUser(undefined)}/>}

    </div>
  );

  function StatusAction({ user, status }: { user: ManagedUser; status: MutableUserStatus }) {
    const label = statusLabel(status);
    return (
        <Popconfirm title={`确认${label}该用户？`} onConfirm={() => void updateStatus(user, status)}>
          <Button actionKey={`users.status.${status.toLowerCase()}`} size="small" danger={status !== 'ENABLED'} aria-label={`${label} ${user.displayName}`}>{label}</Button>
        </Popconfirm>
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
  return status === 'ENABLED' ? 'green' : status === 'LOCKED' ? 'orange' : status === 'CANCELLED' ? 'red' : 'default';
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
