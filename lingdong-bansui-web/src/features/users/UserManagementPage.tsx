import {lookupOrganizationTree} from '../../api/organizationLookup';
import { Button as PlainButton } from 'antd';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ConfiguredAction } from '../../components/ConfiguredAction';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useRef, useState } from 'react';
import { Alert, Drawer, Form, Input, Radio, Select, Space, Tag, TreeSelect, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { KeyRound, Pencil, Search, UserPlus } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import {organizationApi,type OrganizationNode} from '../../api/organization';
import {ResetUserPasswordModal} from './ResetUserPasswordModal';
import { usersApi, type ManagedUser, type MutableUserStatus, type UserDirectoryPage, type UserStatus, type UserType } from '../../api/users';
import { formatDateTime as formatTime } from '../../utils/datetime';

import {UserRoleAssociationModal} from './UserRoleAssociationModal';
import {EditUserDrawer} from './EditUserDrawer';
import {CreateUserDrawer} from './CreateUserDrawer';
import {UserPermissionTreeModal} from '../iam/UserPermissionTreeModal';

const PAGE_SIZE = 10;

const userTypeOptions: Array<{ value: UserType; label: string }> = [
  { value: 'PERSONAL', label: '普通账号' },
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
  identityRoleCode?:string;
  organizationId?:string;
  keyword?: string;
  type?: UserType;
  status?: UserStatus;
}

function organizationFilters(nodes:OrganizationNode[]):{title:string;value:string;children:ReturnType<typeof organizationFilters>}[]{return nodes.map(n=>({title:n.name,value:n.id,children:organizationFilters(n.children??[])}));}
function organizationFilterWidth(nodes:OrganizationNode[]):number {
  return Math.max(220,...nodes.flatMap(node=>[
    Math.ceil([...node.name].reduce((width,char)=>width+(/[^\x00-\xff]/.test(char)?14:8),0))+64,
    organizationFilterWidth(node.children??[])
  ]));
}
export function UserManagementPage({currentUser}:{currentUser:Pick<CurrentUser,'roleCodes'|'permissionCodes'>}) {
  const pendingUserRef = useRef(false);
  const [pendingUser, setPendingUser] = useState<string>();
  const [roleUser,setRoleUser]=useState<ManagedUser>();
  const canAssociateRoles=currentUser.permissionCodes.includes('IAM_USER_ROLE_ASSIGN');
  const [editingUser,setEditingUser]=useState<ManagedUser>();
  const canEdit=currentUser.permissionCodes.includes('IAM_USER_UPDATE');
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
  const [identityUser,setIdentityUser]=useState<ManagedUser>();
  const [filterForm] = Form.useForm<FilterValues>();

  async function reloadOrganizations(){setOrganizationError(undefined);try{setOrganizationNodes(lookupOrganizationTree(await usersApi.scopeOrganizations('IAM_USER_LIST')));}catch(error){setOrganizationError(toMessage(error));}}


  useEffect(() => {
    void loadUsers({}, 1);
    let active=true;
    void usersApi.scopeOrganizations('IAM_USER_LIST').then(lookupOrganizationTree).then(nodes=>{if(active)setOrganizationNodes(nodes);}).catch(error=>{if(active)setOrganizationError(toMessage(error));});
    return()=>{active=false;};
  }, []);

  async function loadUsers(nextFilters: FilterValues, page: number, pageSize = directory.pageSize): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await usersApi.list({ ...nextFilters, page, pageSize });
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
      identityRoleCode:values.identityRoleCode,
      status: values.status,
      organizationId:values.organizationId
    };
    setFilters(nextFilters);
    await loadUsers(nextFilters, 1);
  }

  async function updateStatus(user: ManagedUser, status: MutableUserStatus): Promise<void> {
    if (pendingUserRef.current || user.status === 'CANCELLED' || !currentUser.permissionCodes.includes('IAM_USER_STATUS_CHANGE')) return;
    pendingUserRef.current = true;
    setPendingUser(user.id);
    try {
      await usersApi.updateStatus(user.id, status);
      message.success(`账号已${statusLabel(status)}`);
      await loadUsers(filters, directory.page);
    } catch (error) {
      message.error(toMessage(error));
    } finally { pendingUserRef.current = false; setPendingUser(undefined); }
  }

  return (
    <div className="page-stack management-page user-management-page">
      {organizationError&&<Alert type="error" showIcon message={`组织机构加载失败：${organizationError}`} action={<PlainButton  size="small" onClick={()=>void reloadOrganizations()}>重试</PlainButton>}/>}
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<PlainButton  size="small" onClick={() => void loadUsers(filters, directory.page)}>重试</PlainButton>} />}

      <ProCard className="content-panel management-query" bordered={false}>
        <Form name="user-directory" form={filterForm} layout="inline" className="directory-filters management-query-form" onFinish={search}>
          <Form.Item label="账号或名称" name="keyword"><Input allowClear /></Form.Item>
          <Form.Item label="账号类型" name="type"><Select allowClear options={userTypeOptions} className="filter-select" /></Form.Item>
          <Form.Item label="组织身份" name="identityRoleCode"><Select allowClear options={identityOptions} className="filter-select" /></Form.Item>
          <Form.Item label="账号状态" name="status"><Select allowClear options={statusOptions} className="filter-select" /></Form.Item>
          <Form.Item label="所属组织机构" name="organizationId" className="organization-name-filter" style={{flex:'0 0 auto',maxWidth:'none',width:organizationFilterWidth(organizationNodes)}}><TreeSelect allowClear showSearch treeNodeFilterProp="title" treeData={organizationFilters(organizationNodes)} style={{width:'100%'}} /></Form.Item>
          <Form.Item><Space><Button actionKey="IAM_USER_LIST" type="primary" htmlType="submit" icon={<Search size={16} />}>查询</Button>
          <PlainButton  onClick={() => { filterForm.resetFields(); void search({}); }}>重置</PlainButton></Space></Form.Item>
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
            showSizeChanger: true,
            onChange: (page, pageSize) => void loadUsers(filters, page, pageSize)
          }}
          columns={[
            { title: '账号', dataIndex: 'username', key: 'username', width: 120, ellipsis: true },
            { title: '名称', dataIndex: 'displayName', key: 'displayName', width: 120, ellipsis: true },
            { title: '机构名称', dataIndex: 'organizationNames', key: 'organizationNames', width: 200, ellipsis: true, render: (names?: string[]) => names?.length ? names.join('、') : '—' },
            {title:'邮箱',dataIndex:'email',key:'email',width:220,ellipsis:true,render:(email?:string|null)=>email||'—'},
            { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 130, render: (mobile: string | null) => mobile ?? '-' },
            { title: '账号类型', dataIndex: 'type', key: 'type', width: 100, render: (type: UserType) => userTypeLabel(type) },
            { title: '组织身份', key: 'identities', width: 240, render: (_,user) => user.roleIdentities?.length ? <PlainButton type="text" className="user-identity-button" aria-label={`${user.displayName}组织身份`} onClick={()=>setIdentityUser(user)}><Space size={4} wrap>{[...new Map(user.roleIdentities.map(identity=>[identity.roleCode,identity])).values()].map(identity=><Tag key={identity.roleCode} color={identity.roleCode.endsWith('ADMIN')?'blue':identity.roleCode==='TEACHER'?'cyan':'green'}>{identity.roleName}</Tag>)}</Space></PlainButton> : '—' },
            { title: '状态', dataIndex: 'status', key: 'status', width: 80, render: (status: UserStatus) => <Tag color={statusColor(status)}>{statusLabel(status)}</Tag> },
            { title: '启停用', key: 'enabled', width: 150, render: (_, user) => user.status === 'CANCELLED' ? '—' : <ConfiguredAction actionKey="IAM_USER_STATUS_CHANGE" permissionCodes={currentUser.permissionCodes}><Radio.Group size="small" aria-label={`${user.displayName}启停用`} value={user.status === 'DISABLED' ? 'DISABLED' : 'ENABLED'} disabled={pendingUser === user.id} onChange={event => void updateStatus(user, event.target.value)} options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} /></ConfiguredAction> },
            { title: '锁定', key: 'locked', width: 150, render: (_, user) => user.status === 'CANCELLED' ? '—' : <ConfiguredAction actionKey="IAM_USER_STATUS_CHANGE" permissionCodes={currentUser.permissionCodes}><Radio.Group size="small" aria-label={`${user.displayName}锁定`} value={user.status === 'LOCKED' ? 'LOCKED' : 'ENABLED'} disabled={pendingUser === user.id || user.status === 'DISABLED'} onChange={event => void updateStatus(user, event.target.value)} options={[{ label: '解锁', value: 'ENABLED' }, { label: '锁定', value: 'LOCKED' }]} /></ConfiguredAction> },
            { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160, render: formatTime },
            {
              title: '操作', key: 'actions', width: 380, fixed: 'right',
              render: (_, user) => user.status === 'CANCELLED' ? null : <Space size={8} className="management-row-actions">
                {canEdit&&<Button actionKey="IAM_USER_UPDATE" size="small" icon={<Pencil size={14}/>} onClick={()=>setEditingUser(user)}>编辑</Button>}
                {canAssociateRoles&&<Button actionKey="IAM_USER_ROLE_ASSIGN" size="small" preserveLabel onClick={()=>setRoleUser(user)}>关联角色</Button>}
                <Button actionKey="IAM_USER_PERMISSION_CONFIGURE" size="small" onClick={()=>setPermissionUser(user)}>授权</Button>
                {canResetPassword&&user.type!=='STUDENT'&&<Button actionKey="IAM_USER_PASSWORD_SET" size="small" icon={<KeyRound size={14}/>} onClick={()=>setPasswordUser(user)}>重置密码</Button>}
              </Space>
            }
          ]}
        />
      </ProCard>

      <CreateUserDrawer open={createModalOpen} onClose={()=>setCreateModalOpen(false)} onSaved={()=>loadUsers(filters,directory.page)}/>
      <Drawer title={`${identityUser?.displayName??''} · 组织身份`} open={!!identityUser} onClose={()=>setIdentityUser(undefined)} width={560}>
        <Table rowKey={identity=>`${identity.organizationId??'account'}:${identity.roleCode}`} dataSource={identityUser?.roleIdentities??[]} pagination={false} columns={[{title:'授权组织',dataIndex:'organizationName',render:(name:string|null)=>name??'账号级'},{title:'身份 / 角色',dataIndex:'roleName'},{title:'角色编码',dataIndex:'roleCode'}]}/>
      </Drawer>
      {roleUser&&<UserRoleAssociationModal user={roleUser} onClose={()=>setRoleUser(undefined)} onChanged={()=>loadUsers(filters,directory.page)}/>}
      {permissionUser&&<UserPermissionTreeModal open user={permissionUser} onClose={()=>setPermissionUser(undefined)}/>}
      {editingUser&&canEdit&&<EditUserDrawer user={editingUser} onClose={()=>setEditingUser(undefined)} onSaved={()=>loadUsers(filters,directory.page)}/>}
      {passwordUser&&canResetPassword&&<ResetUserPasswordModal user={passwordUser} onClose={()=>setPasswordUser(undefined)}/>}

    </div>
  );

}

function userTypeLabel(type: UserType): string {
  if(type === 'PERSONAL') return '普通账号';
  return userTypeOptions.find((item) => item.value === type)?.label ?? type;
}
const identityOptions=[
  {value:'PARENT',label:'家长'},{value:'PARENT_ADMIN',label:'家庭管理员'},
  {value:'ORG_ADMIN',label:'机构管理员'},{value:'TEACHER',label:'教师'},
  {value:'STUDENT',label:'学生'},{value:'SYS_ADMIN',label:'系统管理员'},
  {value:'SYS_AUDITOR',label:'系统审核员'}
];

function statusLabel(status: UserStatus): string {
  return statusOptions.find((item) => item.value === status)?.label ?? status;
}

function statusColor(status: UserStatus): string {
  return status === 'ENABLED' ? 'green' : status === 'LOCKED' ? 'orange' : status === 'CANCELLED' ? 'red' : 'default';
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
