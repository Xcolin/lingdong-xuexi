import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { ProCard } from '@ant-design/pro-components';
import { Alert, Checkbox, Drawer, Form, Input, Modal, Popconfirm, Select, Space, Tag, Tooltip, message } from 'antd';
import {
  CircleCheck, CircleOff, KeyRound, Link2, LockKeyhole, Pencil,
  Search, UserPlus, UsersRound
} from 'lucide-react';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { organizationApi, type OrganizationNode } from '../../api/organization';
import {
  teachersApi, type CreateTeacherInput, type Teacher, type TeacherBatchOperation,
  type TeacherPage, type TeacherQuery, type TeacherStatus, type UpdateTeacherProfileInput
} from '../../api/teachers';

const PAGE_SIZE = 20;
const EMPTY_PAGE: TeacherPage = { items: [], page: 1, pageSize: PAGE_SIZE, total: 0 };

interface TeacherManagementPageProps {
  permissionCodes: string[];
}

interface FilterValues {
  keyword?: string;
  schoolId?: string;
  classOrganizationId?: string;
  status?: TeacherStatus;
}

interface ProfileFormValues {
  displayName: string;
  mobile?: string;
  clearMobile?: boolean;
}

interface PasswordFormValues {
  newPassword: string;
}

interface BatchFormValues {
  operation: TeacherBatchOperation;
  classOrganizationId?: string;
}

const statusOptions: Array<{ value: TeacherStatus; label: string }> = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' },
  { value: 'LOCKED', label: '锁定' }
];

const batchOptions: Array<{ value: TeacherBatchOperation; label: string }> = [
  { value: 'ENABLE', label: '批量启用' },
  { value: 'DISABLE', label: '批量停用' },
  { value: 'LOCK', label: '批量锁定' },
  { value: 'BIND_CLASS', label: '批量绑定班级' },
  { value: 'UNBIND_CLASS', label: '批量解绑班级' }
];

export function TeacherManagementPage({ permissionCodes }: TeacherManagementPageProps) {
  const canCreate = permissionCodes.includes('TEACHER_CREATE');
  const canUpdate = permissionCodes.includes('TEACHER_UPDATE');
  const canChangeStatus = permissionCodes.includes('TEACHER_STATUS_CHANGE');
  const canResetPassword = permissionCodes.includes('TEACHER_PASSWORD_RESET');
  const canManageClass = permissionCodes.includes('TEACHER_CLASS_ASSIGN');
  const canBatch = permissionCodes.includes('TEACHER_BATCH_MANAGE');
  const availableBatchOptions = batchOptions.filter((option) =>
    option.value === 'BIND_CLASS' || option.value === 'UNBIND_CLASS'
      ? canManageClass : canChangeStatus);
  const canUseBatch = canBatch && availableBatchOptions.length > 0;
  const [directory, setDirectory] = useState<TeacherPage>(EMPTY_PAGE);
  const [nodes, setNodes] = useState<OrganizationNode[]>([]);
  const [filters, setFilters] = useState<FilterValues>({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>();
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [editingTeacher, setEditingTeacher] = useState<Teacher>();
  const [passwordTeacher, setPasswordTeacher] = useState<Teacher>();
  const [classTeacher, setClassTeacher] = useState<Teacher>();
  const [selectedClassIds, setSelectedClassIds] = useState<string[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [createSchoolId, setCreateSchoolId] = useState<string>();
  const [batchOperation, setBatchOperation] = useState<TeacherBatchOperation>('ENABLE');
  const [filterForm] = Form.useForm<FilterValues>();
  const [createForm] = Form.useForm<CreateTeacherInput>();
  const [profileForm] = Form.useForm<ProfileFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const [batchForm] = Form.useForm<BatchFormValues>();

  const flatNodes = useMemo(() => flattenNodes(nodes), [nodes]);
  const schools = flatNodes.filter((node) => node.typeCode === 'SCHOOL' && node.effectiveStatus === 'ENABLED');
  const classes = flatNodes.filter((node) => node.typeCode === 'CLASS' && node.effectiveStatus === 'ENABLED');
  const filterClasses = filters.schoolId
    ? classes.filter((node) => belongsToSchool(node, filters.schoolId!, schools)) : classes;
  const createClasses = createSchoolId
    ? classes.filter((node) => belongsToSchool(node, createSchoolId, schools)) : [];
  const classDrawerOptions = classTeacher
    ? classes.filter((node) => belongsToSchool(node, classTeacher.schoolId, schools)) : [];

  useEffect(() => {
    void Promise.all([loadTeachers({}, 1), organizationApi.listTree()
      .then(setNodes)
      .catch((error: unknown) => setErrorMessage(toMessage(error)))]);
  }, []);

  async function loadTeachers(nextFilters: FilterValues, page: number): Promise<void> {
    setLoading(true);
    setErrorMessage(undefined);
    try {
      const query: TeacherQuery = { ...nextFilters, page, pageSize: PAGE_SIZE };
      setDirectory(await teachersApi.list(query));
      setSelectedIds([]);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function search(values: FilterValues): Promise<void> {
    const next = { ...values, keyword: values.keyword?.trim() || undefined };
    setFilters(next);
    await loadTeachers(next, 1);
  }

  async function submit(action: () => Promise<void>, successText: string): Promise<void> {
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

  async function createTeacher(values: CreateTeacherInput): Promise<void> {
    await submit(async () => {
      await teachersApi.create({
        ...values,
        mobile: values.mobile?.trim() || undefined,
        classOrganizationIds: values.classOrganizationIds ?? []
      });
      setCreateOpen(false);
      setCreateSchoolId(undefined);
      createForm.resetFields();
      await loadTeachers(filters, 1);
    }, '教师已创建');
  }

  function openProfile(teacher: Teacher): void {
    setEditingTeacher(teacher);
    profileForm.setFieldsValue({ displayName: teacher.displayName, mobile: undefined, clearMobile: false });
  }

  async function updateProfile(values: ProfileFormValues): Promise<void> {
    if (!editingTeacher) return;
    const input: UpdateTeacherProfileInput = {
      displayName: values.displayName,
      mobile: values.mobile?.trim() || undefined,
      clearMobile: values.clearMobile === true
    };
    await submit(async () => {
      await teachersApi.updateProfile(editingTeacher.id, input);
      setEditingTeacher(undefined);
      profileForm.resetFields();
      await loadTeachers(filters, directory.page);
    }, '教师资料已更新');
  }

  async function changeStatus(teacher: Teacher, status: TeacherStatus): Promise<void> {
    await submit(async () => {
      await teachersApi.changeStatus(teacher.id, status);
      await loadTeachers(filters, directory.page);
    }, `教师已${statusLabel(status)}`);
  }

  async function resetPassword(values: PasswordFormValues): Promise<void> {
    if (!passwordTeacher) return;
    await submit(async () => {
      await teachersApi.resetPassword(passwordTeacher.id, values.newPassword);
      setPasswordTeacher(undefined);
      passwordForm.resetFields();
    }, '教师密码已重置，既有会话已失效');
  }

  function openClasses(teacher: Teacher): void {
    setClassTeacher(teacher);
    setSelectedClassIds(teacher.classOrganizationIds);
  }

  function openBatch(): void {
    const operation = availableBatchOptions[0]?.value;
    if (!operation) return;
    setBatchOperation(operation);
    batchForm.setFieldsValue({ operation, classOrganizationId: undefined });
    setBatchOpen(true);
  }

  async function saveClasses(): Promise<void> {
    if (!classTeacher) return;
    const before = new Set(classTeacher.classOrganizationIds);
    const after = new Set(selectedClassIds);
    const additions = selectedClassIds.filter((id) => !before.has(id));
    const removals = classTeacher.classOrganizationIds.filter((id) => !after.has(id));
    await submit(async () => {
      for (const classId of additions) await teachersApi.bindClass(classTeacher.id, classId);
      for (const classId of removals) await teachersApi.unbindClass(classTeacher.id, classId);
      setClassTeacher(undefined);
      await loadTeachers(filters, directory.page);
    }, '教师班级范围已更新');
  }

  async function executeBatch(values: BatchFormValues): Promise<void> {
    await submit(async () => {
      const result = await teachersApi.batch({
        operation: values.operation,
        teacherUserIds: selectedIds,
        classOrganizationId: values.classOrganizationId
      });
      if (result.failureCount > 0) {
        Modal.warning({
          title: '批量操作已完成',
          content: `成功 ${result.successCount} 项，失败 ${result.failureCount} 项。失败项请检查状态或数据范围。`
        });
      }
      setBatchOpen(false);
      batchForm.resetFields();
      setBatchOperation(availableBatchOptions[0]?.value ?? 'ENABLE');
      await loadTeachers(filters, directory.page);
    }, '批量操作已执行');
  }

  return (
    <div className="page-stack teacher-management-page">
      <div className="page-heading">
        <h1>教师管理</h1>
        <Space wrap>
          {canUseBatch && <Button actionKey="teachers.teacher-management-page.1" icon={<UsersRound size={16} />} disabled={selectedIds.length === 0} onClick={openBatch}>批量操作</Button>}
          {canCreate && <Button actionKey="teachers.teacher-management-page.2" type="primary" icon={<UserPlus size={16} />} onClick={() => setCreateOpen(true)}>新增教师</Button>}
        </Space>
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="teachers.teacher-management-page.3" size="small" onClick={() => void loadTeachers(filters, directory.page)}>重试</Button>} />}
      <ProCard className="content-panel" bordered={false}>
        <Form form={filterForm} layout="inline" className="directory-filters" onFinish={search}>
          <Form.Item label="账号或姓名" name="keyword"><Input allowClear /></Form.Item>
          <Form.Item label="学校" name="schoolId">
            <Select allowClear className="filter-select" options={schools.map(toOption)} onChange={(schoolId) => {
              filterForm.setFieldValue('classOrganizationId', undefined);
              setFilters((current) => ({ ...current, schoolId }));
            }} />
          </Form.Item>
          <Form.Item label="班级" name="classOrganizationId"><Select allowClear className="filter-select" options={filterClasses.map(toOption)} /></Form.Item>
          <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={statusOptions} /></Form.Item>
          <Form.Item><Button actionKey="teachers.teacher-management-page.4" type="primary" htmlType="submit" icon={<Search size={16} />}>查询</Button></Form.Item>
        </Form>
        <Table<Teacher>
          rowKey="id" loading={loading} dataSource={directory.items} scroll={{ x: 1060 }}
          locale={{ emptyText: '暂无匹配教师' }}
          rowSelection={canUseBatch ? { selectedRowKeys: selectedIds, onChange: (keys) => setSelectedIds(keys.map(String)), preserveSelectedRowKeys: true } : undefined}
          pagination={{ current: directory.page, pageSize: directory.pageSize, total: directory.total, showSizeChanger: false, onChange: (page) => void loadTeachers(filters, page) }}
          columns={[
            { title: '账号', dataIndex: 'username', key: 'username', width: 150 },
            { title: '姓名', dataIndex: 'displayName', key: 'displayName', width: 120 },
            { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 135, render: (value) => value ?? '-' },
            { title: '学校', dataIndex: 'schoolName', key: 'schoolName', width: 160 },
            { title: '班级数', key: 'classes', width: 86, render: (_, teacher) => teacher.classOrganizationIds.length },
            { title: '状态', dataIndex: 'status', key: 'status', width: 86, render: (status) => <StatusTag status={status} /> },
            { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatTime },
            {
              title: '操作', key: 'actions', fixed: 'right', width: 220,
              render: (_, teacher) => <Space size={2}>
                {canUpdate && <ActionButton actionKey="teachers.profile.edit" title="编辑资料" label={`编辑教师-${teacher.displayName}`} icon={<Pencil size={16} />} onClick={() => openProfile(teacher)} />}
                {canResetPassword && <ActionButton actionKey="teachers.password.reset" title="重置密码" label={`重置密码-${teacher.displayName}`} icon={<KeyRound size={16} />} onClick={() => setPasswordTeacher(teacher)} />}
                {canManageClass && <ActionButton actionKey="teachers.classes.configure" title="班级范围" label={`班级范围-${teacher.displayName}`} icon={<Link2 size={16} />} onClick={() => openClasses(teacher)} />}
                {canChangeStatus && <StatusActions teacher={teacher} onChange={changeStatus} />}
              </Space>
            }
          ]}
        />
      </ProCard>

      <Modal title="新增教师" open={createOpen} footer={null} onCancel={() => setCreateOpen(false)} destroyOnHidden>
        <Form form={createForm} layout="vertical" initialValues={{ classOrganizationIds: [] }} onFinish={createTeacher}>
          <Form.Item label="教师账号" name="username" rules={[{ required: true }, { max: 64 }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="教师姓名" name="displayName" rules={[{ required: true }, { max: 20 }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="手机号" name="mobile" rules={[{ max: 32 }]}><Input autoComplete="off" /></Form.Item>
          <Form.Item label="初始密码" name="initialPassword" rules={[{ required: true }, { pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/, message: '请输入 8 至 20 位字母和数字组合' }]}><Input.Password autoComplete="new-password" /></Form.Item>
          <Form.Item label="所属学校" name="schoolId" rules={[{ required: true }]}><Select options={schools.map(toOption)} onChange={(value) => { setCreateSchoolId(value); createForm.setFieldValue('classOrganizationIds', []); }} /></Form.Item>
          {canManageClass && <Form.Item label="初始班级" name="classOrganizationIds"><Select mode="multiple" options={createClasses.map(toOption)} disabled={!createSchoolId} /></Form.Item>}
          <FormActions submitting={submitting} onCancel={() => setCreateOpen(false)} submitText="创建教师" />
        </Form>
      </Modal>

      <Modal title="编辑教师资料" open={Boolean(editingTeacher)} footer={null} onCancel={() => setEditingTeacher(undefined)} destroyOnHidden>
        <Form form={profileForm} layout="vertical" onFinish={updateProfile}>
          <Form.Item label="教师姓名" name="displayName" rules={[{ required: true }, { max: 20 }]}><Input /></Form.Item>
          <Form.Item label="新手机号" name="mobile" extra="留空保持现有手机号不变"><Input disabled={Form.useWatch('clearMobile', profileForm) === true} /></Form.Item>
          <Form.Item name="clearMobile" valuePropName="checked"><Checkbox>清空现有手机号</Checkbox></Form.Item>
          <FormActions submitting={submitting} onCancel={() => setEditingTeacher(undefined)} />
        </Form>
      </Modal>

      <Modal title="重置教师密码" open={Boolean(passwordTeacher)} footer={null} onCancel={() => setPasswordTeacher(undefined)} destroyOnHidden>
        <Form form={passwordForm} layout="vertical" onFinish={resetPassword}>
          <Form.Item label="新密码" name="newPassword" rules={[{ required: true }, { pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/, message: '请输入 8 至 20 位字母和数字组合' }]}><Input.Password autoComplete="new-password" /></Form.Item>
          <FormActions submitting={submitting} onCancel={() => setPasswordTeacher(undefined)} submitText="重置密码" />
        </Form>
      </Modal>

      <Drawer title={classTeacher ? `${classTeacher.displayName}的班级范围` : '教师班级范围'} open={Boolean(classTeacher)} width={420} onClose={() => setClassTeacher(undefined)} extra={<Button actionKey="teachers.teacher-management-page.5" type="primary" loading={submitting} onClick={() => void saveClasses()}>保存</Button>}>
        <Checkbox.Group className="teacher-class-checkboxes" value={selectedClassIds} onChange={(values) => setSelectedClassIds(values.map(String))} options={classDrawerOptions.map(toOption)} />
        {classDrawerOptions.length === 0 && <Alert type="info" showIcon message="该学校暂无可用班级" />}
      </Drawer>

      <Modal title="批量操作" open={batchOpen} footer={null} onCancel={() => setBatchOpen(false)} destroyOnHidden>
        <Form form={batchForm} layout="vertical" onFinish={executeBatch}>
          <Alert type="info" showIcon message={`已选择 ${selectedIds.length} 位教师`} />
          <Form.Item label="操作类型" name="operation" rules={[{ required: true }]}><Select options={availableBatchOptions} onChange={setBatchOperation} /></Form.Item>
          {(batchOperation === 'BIND_CLASS' || batchOperation === 'UNBIND_CLASS') && <Form.Item label="班级" name="classOrganizationId" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={classes.map(toOption)} /></Form.Item>}
          <FormActions submitting={submitting} onCancel={() => setBatchOpen(false)} submitText="执行批量操作" />
        </Form>
      </Modal>
    </div>
  );
}

function StatusActions({ teacher, onChange }: { teacher: Teacher; onChange: (teacher: Teacher, status: TeacherStatus) => Promise<void> }) {
  return <Space size={2}>
    {teacher.status !== 'ENABLED' && <ConfirmStatus teacher={teacher} status="ENABLED" icon={<CircleCheck size={16} />} onChange={onChange} />}
    {teacher.status !== 'DISABLED' && <ConfirmStatus teacher={teacher} status="DISABLED" icon={<CircleOff size={16} />} onChange={onChange} />}
    {teacher.status !== 'LOCKED' && <ConfirmStatus teacher={teacher} status="LOCKED" icon={<LockKeyhole size={16} />} onChange={onChange} />}
  </Space>;
}

function ConfirmStatus({ teacher, status, icon, onChange }: { teacher: Teacher; status: TeacherStatus; icon: ReactNode; onChange: (teacher: Teacher, status: TeacherStatus) => Promise<void> }) {
  const label = statusLabel(status);
  return <Tooltip title={label}><Popconfirm title={`确认${label}该教师？`} onConfirm={() => void onChange(teacher, status)}><Button actionKey={`teachers.status.${status.toLowerCase()}`} type="text" icon={icon} aria-label={`${label}-${teacher.displayName}`} /></Popconfirm></Tooltip>;
}

function ActionButton({ actionKey, title, label, icon, onClick }: { actionKey: string; title: string; label: string; icon: ReactNode; onClick: () => void }) {
  return <Tooltip title={title}><Button actionKey={actionKey} type="text" icon={icon} aria-label={label} onClick={onClick} /></Tooltip>;
}

function FormActions({ submitting, onCancel, submitText = '保存' }: { submitting: boolean; onCancel: () => void; submitText?: string }) {
  return <div className="form-actions"><Button actionKey="teachers.teacher-management-page.8" onClick={onCancel}>取消</Button><Button actionKey="teachers.teacher-management-page.9" type="primary" htmlType="submit" loading={submitting}>{submitText}</Button></div>;
}

function StatusTag({ status }: { status: TeacherStatus }) {
  return <Tag color={status === 'ENABLED' ? 'green' : status === 'LOCKED' ? 'orange' : 'default'}>{statusLabel(status)}</Tag>;
}

function statusLabel(status: TeacherStatus): string {
  return statusOptions.find((item) => item.value === status)?.label ?? status;
}

function flattenNodes(nodes: OrganizationNode[]): OrganizationNode[] {
  return nodes.flatMap((node) => [node, ...flattenNodes(node.children ?? [])]);
}

function belongsToSchool(node: OrganizationNode, schoolId: string, schools: OrganizationNode[]): boolean {
  const school = schools.find((item) => item.id === schoolId);
  return school ? node.path.startsWith(school.path) : node.parentId === schoolId;
}

function toOption(node: OrganizationNode) {
  return { value: node.id, label: node.name };
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
