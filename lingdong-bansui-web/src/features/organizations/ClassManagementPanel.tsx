import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useMemo, useState } from 'react';
import { Alert, Form, Input, InputNumber, Select, Space, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Edit3, Plus, Power, PowerOff, RefreshCw } from 'lucide-react';
import {
  classApi, type ClassOrganization, type CreateClassInput, type UpdateClassInput
} from '../../api/classes';

interface ClassFormValues {
  schoolOrganizationId?: string;
  name: string;
  sortOrder: number;
}

/** 机构管理员班级列表及低风险维护操作。 */
export function ClassManagementPanel() {
  const [form] = Form.useForm<ClassFormValues>();
  const [schools, setSchools] = useState<ClassOrganization[]>([]);
  const [classes, setClasses] = useState<ClassOrganization[]>([]);
  const [editing, setEditing] = useState<ClassOrganization | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const schoolNameById = useMemo(
    () => new Map(schools.map((school) => [school.id, school.name])),
    [schools]
  );

  useEffect(() => { void loadData(); }, []);

  async function loadData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [loadedSchools, loadedClasses] = await Promise.all([
        classApi.listSchools(), classApi.listClasses()
      ]);
      setSchools(loadedSchools);
      setClasses(loadedClasses);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  function openCreate(): void {
    setEditing(null);
    form.setFieldsValue({ schoolOrganizationId: undefined, name: '', sortOrder: 100 });
    setModalOpen(true);
  }

  function openEdit(item: ClassOrganization): void {
    setEditing(item);
    form.setFieldsValue({ name: item.name, sortOrder: item.sortOrder });
    setModalOpen(true);
  }

  async function submit(values: ClassFormValues): Promise<void> {
    setSubmitting(true);
    try {
      if (editing) {
        const input: UpdateClassInput = {
          name: values.name.trim(), sortOrder: values.sortOrder, versionNo: editing.versionNo
        };
        await classApi.updateClass(editing.id, input);
        message.success('班级信息已更新');
      } else {
        const input: CreateClassInput = {
          schoolOrganizationId: values.schoolOrganizationId ?? '',
          name: values.name.trim(), sortOrder: values.sortOrder
        };
        await classApi.createClass(input);
        message.success('班级已创建');
      }
      setModalOpen(false);
      form.resetFields();
      await loadData();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  function confirmStatus(item: ClassOrganization): void {
    const disabling = item.status === 'ENABLED';
    Modal.confirm({ actionPrefix: 'organizations.class-management-panel.confirm.1',
      title: disabling ? '确认停用班级' : '确认启用班级',
      content: disabling
        ? `停用“${item.name}”后，班内未完成的机构和教师任务将失效。`
        : `确认重新启用“${item.name}”吗？已失效任务不会恢复。`,
      okText: disabling ? '确认停用' : '确认启用',
      cancelText: '取消',
      okButtonProps: { danger: disabling },
      onOk: async () => {
        if (disabling) {
          await classApi.disableClass(item.id, item.versionNo);
          message.success('班级已停用');
        } else {
          await classApi.enableClass(item.id, item.versionNo);
          message.success('班级已启用');
        }
        await loadData();
      }
    });
  }

  const columns: ColumnsType<ClassOrganization> = [
    { title: '班级名称', dataIndex: 'name', key: 'name' },
    {
      title: '所属学校', dataIndex: 'parentId', key: 'parentId',
      render: (parentId: string | null) => parentId
        ? schoolNameById.get(parentId) ?? `学校标识 ${parentId}` : '-'
    },
    { title: '编码', dataIndex: 'code', key: 'code' },
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 90,
      render: (status: ClassOrganization['status']) => (
        <Tag color={status === 'ENABLED' ? 'green' : 'default'}>
          {status === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      )
    },
    {
      title: '操作', key: 'actions', width: 190,
      render: (_, item) => <Space size="small">
        <Button actionKey="organizations.class-management-panel.1" size="small" icon={<Edit3 size={14} />} onClick={() => openEdit(item)}>编辑</Button>
        <Button actionKey="organizations.class-management-panel.2"
          size="small"
          danger={item.status === 'ENABLED'}
          icon={item.status === 'ENABLED' ? <PowerOff size={14} /> : <Power size={14} />}
          onClick={() => confirmStatus(item)}
        >{item.status === 'ENABLED' ? '停用' : '启用'}</Button>
      </Space>
    }
  ];

  return <section className="content-section" aria-labelledby="class-management-heading">
    <div className="section-heading">
      <h2 id="class-management-heading">班级管理</h2>
      <Space>
        <Button actionKey="organizations.class-management-panel.3" icon={<RefreshCw size={15} />} onClick={() => void loadData()} loading={loading}>刷新</Button>
        <Button actionKey="organizations.class-management-panel.4" type="primary" icon={<Plus size={15} />} onClick={openCreate}>新增班级</Button>
      </Space>
    </div>
    {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="organizations.class-management-panel.5" size="small" onClick={() => void loadData()}>重试</Button>} />}
    <Table<ClassOrganization>
      rowKey="id" columns={columns} dataSource={classes} loading={loading}
      size="small" pagination={{ pageSize: 20, hideOnSinglePage: true }}
      locale={{ emptyText: '暂无班级' }} scroll={{ x: 760 }}
    />
    <Modal
      title={editing ? '编辑班级' : '新增班级'} open={modalOpen} footer={null}
      onCancel={() => setModalOpen(false)} destroyOnHidden
    >
      <Form form={form} layout="vertical" initialValues={{ sortOrder: 100 }} onFinish={submit}>
        {!editing && <Form.Item
          name="schoolOrganizationId" label="所属学校"
          rules={[{ required: true, message: '请选择所属学校' }]}
        >
          <Select
            showSearch optionFilterProp="label"
            options={schools.map((school) => ({
              value: school.id, label: `${school.name}（${school.code}）`
            }))}
          />
        </Form.Item>}
        <Form.Item
          name="name" label="班级名称"
          rules={[{ required: true, whitespace: true, message: '请输入班级名称' }, { max: 50 }]}
        ><Input maxLength={50} autoComplete="off" /></Form.Item>
        <Form.Item
          name="sortOrder" label="排序"
          rules={[{ required: true, message: '请输入排序值' }]}
        ><InputNumber min={0} precision={0} className="full-width" /></Form.Item>
        <div className="form-actions">
          <Button actionKey="organizations.class-management-panel.6" onClick={() => setModalOpen(false)}>取消</Button>
          <Button actionKey="organizations.class-management-panel.7" type="primary" htmlType="submit" loading={submitting}>
            {editing ? '保存修改' : '创建班级'}
          </Button>
        </div>
      </Form>
    </Modal>
  </section>;
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
