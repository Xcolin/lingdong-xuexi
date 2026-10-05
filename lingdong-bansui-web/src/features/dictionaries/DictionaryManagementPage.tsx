import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { Alert, Checkbox, Form, Input, InputNumber, message, Modal, Select, Space, Tag, Tooltip } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { Pencil, Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  dictionaryApi,
  type CreateDictionaryItemInput,
  type CreateDictionaryTypeInput,
  type DictionaryItem,
  type DictionaryStatus,
  type DictionaryType,
  type UpdateDictionaryItemInput,
  type UpdateDictionaryTypeInput
} from '../../api/dictionaries';

type TypeFormValues = CreateDictionaryTypeInput & { status?: DictionaryStatus };
type ItemFormValues = CreateDictionaryItemInput & { status?: DictionaryStatus };

// 与后端 normalizeCode/normalizeItemCode 规则保持一致：3至64位、仅字母/数字/下划线；
// 字典类型编码必须字母开头，字典项编码允许数字开头。
const CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{2,63}$/;
const CODE_RULE_MESSAGE = '编码仅允许3至64位字母、数字和下划线，且必须以字母开头';
const ITEM_CODE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_]{2,63}$/;
const ITEM_CODE_RULE_MESSAGE = '编码仅允许3至64位字母、数字和下划线';
const NAME_MAX_LENGTH = 50;

export function DictionaryManagementPage({ canManage }: { canManage: boolean }) {
  const [types, setTypes] = useState<DictionaryType[]>([]);
  const [items, setItems] = useState<DictionaryItem[]>([]);
  const [selectedTypeId, setSelectedTypeId] = useState<string>();
  const [loadingTypes, setLoadingTypes] = useState(true);
  const [loadingItems, setLoadingItems] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>();
  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<DictionaryType>();
  const [editingItem, setEditingItem] = useState<DictionaryItem>();
  const [typeForm] = Form.useForm<TypeFormValues>();
  const [itemForm] = Form.useForm<ItemFormValues>();

  useEffect(() => { void loadTypes(); }, []);
  useEffect(() => {
    if (selectedTypeId) void loadItems(selectedTypeId);
    else setItems([]);
  }, [selectedTypeId]);

  async function loadTypes(preferredTypeId?: string): Promise<void> {
    setLoadingTypes(true);
    setErrorMessage(undefined);
    try {
      const loaded = await dictionaryApi.listTypes();
      setTypes(loaded);
      setSelectedTypeId((current) => preferredTypeId ?? current ?? loaded[0]?.id);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoadingTypes(false);
    }
  }

  async function loadItems(typeId: string): Promise<void> {
    setLoadingItems(true);
    try {
      setItems(await dictionaryApi.listItems(typeId));
    } catch (error) {
      setItems([]);
      setErrorMessage(toMessage(error));
    } finally {
      setLoadingItems(false);
    }
  }

  function openCreateType(): void {
    setEditingType(undefined);
    typeForm.setFieldsValue({ code: '', name: '', sortOrder: 0, status: 'ENABLED' });
    setTypeModalOpen(true);
  }

  function openEditType(type: DictionaryType): void {
    setEditingType(type);
    typeForm.setFieldsValue({ code: type.code, name: type.name, sortOrder: type.sortOrder, status: type.status });
    setTypeModalOpen(true);
  }

  function openCreateItem(): void {
    setEditingItem(undefined);
    itemForm.setFieldsValue({ code: '', name: '', sortOrder: 0, defaultItem: false, status: 'ENABLED' });
    setItemModalOpen(true);
  }

  function openEditItem(item: DictionaryItem): void {
    setEditingItem(item);
    itemForm.setFieldsValue({
      code: item.code,
      name: item.name,
      sortOrder: item.sortOrder,
      defaultItem: item.defaultItem,
      status: item.status
    });
    setItemModalOpen(true);
  }

  async function saveType(values: TypeFormValues): Promise<void> {
    await submit(async () => {
      const saved = editingType
        ? await dictionaryApi.updateType(editingType.id, toUpdateTypeInput(values))
        : await dictionaryApi.createType(toCreateTypeInput(values));
      setTypeModalOpen(false);
      setEditingType(undefined);
      typeForm.resetFields();
      await loadTypes(saved.id);
    }, editingType ? '字典类型已更新' : '字典类型已创建');
  }

  async function saveItem(values: ItemFormValues): Promise<void> {
    if (!selectedTypeId) return;
    await submit(async () => {
      if (editingItem) {
        await dictionaryApi.updateItem(editingItem.id, toUpdateItemInput(values));
      } else {
        await dictionaryApi.createItem(selectedTypeId, toCreateItemInput(values));
      }
      setItemModalOpen(false);
      setEditingItem(undefined);
      itemForm.resetFields();
      await loadItems(selectedTypeId);
    }, editingItem ? '字典项已更新' : '字典项已创建');
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

  const selectedType = types.find((type) => type.id === selectedTypeId);
  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>数据字典</h1>
        {canManage && (
          <Space wrap>
            <Button actionKey="DICTIONARY_MANAGE" icon={<Plus size={16} />} onClick={openCreateType}>新增类型</Button>
            <Button actionKey="dictionaries.dictionary-management-page.2" type="primary" icon={<Plus size={16} />} disabled={!selectedTypeId} onClick={openCreateItem}>新增字典项</Button>
          </Space>
        )}
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="dictionaries.dictionary-management-page.3" size="small" onClick={() => void loadTypes()}>重试</Button>} />}
      <div className="dictionary-workspace">
        <ProCard className="content-panel" title="字典类型" bordered={false}>
          <Table<DictionaryType>
            rowKey="id" size="small" loading={loadingTypes} dataSource={types} pagination={false}
            rowClassName={(record) => record.id === selectedTypeId ? 'table-row-selected' : ''}
            onRow={(record) => ({ onClick: () => setSelectedTypeId(record.id) })}
            locale={{ emptyText: '暂无字典类型' }}
            columns={[
              { title: '名称', dataIndex: 'name', key: 'name' },
              { title: '编码', dataIndex: 'code', key: 'code' },
              { title: '状态', dataIndex: 'status', key: 'status', width: 76, render: (status) => <StatusTag status={status} /> },
              {
                title: '操作', key: 'actions', width: 64, hidden: !canManage,
                render: (_, record) => (
                  <Tooltip title="编辑类型">
                    <Button actionKey="dictionaries.dictionary-management-page.4"
                      type="text"
                      icon={<Pencil size={16} />}
                      aria-label={`编辑字典类型-${record.name}`}
                      onClick={(event) => { event.stopPropagation(); openEditType(record); }}
                    />
                  </Tooltip>
                )
              }
            ]}
          />
        </ProCard>
        <ProCard className="content-panel" title={selectedType ? `字典项 · ${selectedType.name}` : '字典项'} bordered={false}>
          <Table<DictionaryItem>
            rowKey="id" size="small" loading={loadingItems} dataSource={items} pagination={false}
            locale={{ emptyText: selectedTypeId ? '暂无字典项' : '请先选择字典类型' }}
            columns={[
              { title: '名称', dataIndex: 'name', key: 'name' },
              { title: '编码', dataIndex: 'code', key: 'code' },
              { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 72 },
              { title: '默认', dataIndex: 'defaultItem', key: 'defaultItem', width: 72, render: (value) => value ? <Tag color="blue">默认</Tag> : '-' },
              { title: '状态', dataIndex: 'status', key: 'status', width: 76, render: (status) => <StatusTag status={status} /> },
              {
                title: '操作', key: 'actions', width: 64, hidden: !canManage,
                render: (_, record) => (
                  <Tooltip title="编辑字典项">
                    <Button actionKey="dictionaries.dictionary-management-page.5" type="text" icon={<Pencil size={16} />} aria-label={`编辑字典项-${record.name}`} onClick={() => openEditItem(record)} />
                  </Tooltip>
                )
              }
            ]}
          />
        </ProCard>
      </div>
      <Modal title={editingType ? '修改字典类型' : '新增字典类型'} open={typeModalOpen} footer={null} onCancel={() => setTypeModalOpen(false)} destroyOnHidden width="min(640px, 92vw)">
        <Form form={typeForm} layout="vertical" onFinish={saveType}>
          {!editingType && <Form.Item label="类型编码" name="code" rules={[{ required: true, message: '请输入类型编码' }, { pattern: CODE_PATTERN, message: CODE_RULE_MESSAGE }]}><Input autoComplete="off" maxLength={64} /></Form.Item>}
          <Form.Item label="类型名称" name="name" rules={[{ required: true, message: '请输入类型名称' }, { whitespace: true, message: '名称不能为空白字符' }]}><Input autoComplete="off" maxLength={NAME_MAX_LENGTH} showCount /></Form.Item>
          <Form.Item label="排序" name="sortOrder" rules={[{ required: true, message: '请输入排序' }]}><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item>
          {editingType && <Form.Item label="状态" name="status" rules={[{ required: true }]}><StatusSelect /></Form.Item>}
          <FormActions submitting={submitting} onCancel={() => setTypeModalOpen(false)} />
        </Form>
      </Modal>
      <Modal title={editingItem ? '修改字典项' : '新增字典项'} open={itemModalOpen} footer={null} onCancel={() => setItemModalOpen(false)} destroyOnHidden width="min(640px, 92vw)">
        <Form form={itemForm} layout="vertical" onFinish={saveItem}>
          {!editingItem && <Form.Item label="字典项编码" name="code" rules={[{ required: true, message: '请输入字典项编码' }, { pattern: ITEM_CODE_PATTERN, message: ITEM_CODE_RULE_MESSAGE }]}><Input autoComplete="off" maxLength={64} /></Form.Item>}
          <Form.Item label="字典项名称" name="name" rules={[{ required: true, message: '请输入字典项名称' }, { whitespace: true, message: '名称不能为空白字符' }]}><Input autoComplete="off" maxLength={NAME_MAX_LENGTH} showCount /></Form.Item>
          <Form.Item label="排序" name="sortOrder" rules={[{ required: true, message: '请输入排序' }]}><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item>
          {editingItem && <Form.Item label="状态" name="status" rules={[{ required: true }]}><StatusSelect /></Form.Item>}
          <Form.Item name="defaultItem" valuePropName="checked"><Checkbox>设为默认项</Checkbox></Form.Item>
          <FormActions submitting={submitting} onCancel={() => setItemModalOpen(false)} />
        </Form>
      </Modal>
    </div>
  );
}

function StatusTag({ status }: { status: 'ENABLED' | 'DISABLED' }) {
  return <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>;
}
function StatusSelect({ value, onChange, id }: {
  value?: DictionaryStatus;
  onChange?: (value: DictionaryStatus) => void;
  id?: string;
}) {
  return (
    <Select
      id={id}
      value={value}
      onChange={onChange}
      options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]}
    />
  );
}
function FormActions({ submitting, onCancel }: { submitting: boolean; onCancel: () => void }) {
  return <div className="form-actions"><Button actionKey="dictionaries.dictionary-management-page.6" onClick={onCancel}>取消</Button><Button actionKey="dictionaries.dictionary-management-page.7" type="primary" htmlType="submit" loading={submitting}>保存</Button></div>;
}
function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}

function toCreateTypeInput(values: TypeFormValues): CreateDictionaryTypeInput {
  return { code: values.code.trim(), name: values.name.trim(), sortOrder: values.sortOrder };
}

function toUpdateTypeInput(values: TypeFormValues): UpdateDictionaryTypeInput {
  return { name: values.name.trim(), sortOrder: values.sortOrder, status: values.status ?? 'ENABLED' };
}

function toCreateItemInput(values: ItemFormValues): CreateDictionaryItemInput {
  return { code: values.code.trim(), name: values.name.trim(), sortOrder: values.sortOrder, defaultItem: values.defaultItem };
}

function toUpdateItemInput(values: ItemFormValues): UpdateDictionaryItemInput {
  return {
    name: values.name.trim(),
    sortOrder: values.sortOrder,
    status: values.status ?? 'ENABLED',
    defaultItem: values.defaultItem
  };
}
