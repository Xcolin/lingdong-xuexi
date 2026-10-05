import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { Alert, Checkbox, Drawer, Form, Input, InputNumber, message, Select, Space, Switch, Tag, Tooltip, Upload } from 'antd';
import { FormInstance, TableProps } from 'antd';
import { ArrowDown, ArrowUp, Columns3, Download, FilePlus2, Plus, Power, PowerOff, RotateCcw, Search, Star, Trash2, Upload as UploadIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { formatDateTime as formatTime } from '../../utils/datetime';
import {
  importExportTemplateApi,
  type CreateImportExportTemplateInput,
  type ImportExportTemplateOptions,
  type ImportExportTemplateQuery,
  type ImportExportTemplateRecord,
  type ImportExportTemplateStatus,
  type ImportExportTemplateType,
  type ImportTemplateFieldInput
} from '../../api/import-export-templates';
import { ApiRequestError } from '../../api/http';

interface ImportExportTemplateManagementPageProps {
  canManage: boolean;
}

interface FilterValues {
  templateName?: string;
  templateType?: ImportExportTemplateType;
  moduleCode?: string;
  status?: ImportExportTemplateStatus;
}

interface CreateValues {
  templateName: string;
  templateType: ImportExportTemplateType;
  moduleCode: string;
  version: string;
  defaultTemplate: boolean;
  fields: ImportTemplateFieldInput[];
}

interface FieldValues {
  fields: ImportTemplateFieldInput[];
}

const emptyOptions: ImportExportTemplateOptions = {
  templateTypes: [], modules: [], statuses: []
};

export function ImportExportTemplateManagementPage({ canManage }: ImportExportTemplateManagementPageProps) {
  const [templates, setTemplates] = useState<ImportExportTemplateRecord[]>([]);
  const [options, setOptions] = useState<ImportExportTemplateOptions>(emptyOptions);
  const [query, setQuery] = useState<ImportExportTemplateQuery>({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [pendingId, setPendingId] = useState<string>();
  const [errorMessage, setErrorMessage] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File>();
  const [fieldTemplate, setFieldTemplate] = useState<ImportExportTemplateRecord>();
  const [fieldLoading, setFieldLoading] = useState(false);
  const [fieldSubmitting, setFieldSubmitting] = useState(false);
  const [filterForm] = Form.useForm<FilterValues>();
  const [createForm] = Form.useForm<CreateValues>();
  const [fieldForm] = Form.useForm<FieldValues>();
  const createTemplateType = Form.useWatch('templateType', createForm);
  const fieldEditable = Boolean(canManage && fieldTemplate?.status === 'DISABLED');

  useEffect(() => { void loadInitial(); }, []);

  async function loadInitial(): Promise<void> {
    setLoading(true);
    setErrorMessage(undefined);
    try {
      const [loadedOptions, loadedTemplates] = await Promise.all([
        importExportTemplateApi.options(), importExportTemplateApi.list({})
      ]);
      setOptions(loadedOptions);
      setTemplates(loadedTemplates);
      setQuery({});
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function loadList(nextQuery: ImportExportTemplateQuery): Promise<void> {
    setLoading(true);
    setErrorMessage(undefined);
    try {
      setTemplates(await importExportTemplateApi.list(nextQuery));
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function submitFilter(values: FilterValues): Promise<void> {
    const nextQuery = cleanQuery(values);
    setQuery(nextQuery);
    await loadList(nextQuery);
  }

  async function resetFilter(): Promise<void> {
    filterForm.resetFields();
    setQuery({});
    await loadList({});
  }

  function openCreate(): void {
    setSelectedFile(undefined);
    setCreateOpen(true);
  }

  function closeCreate(): void {
    setCreateOpen(false);
    setSelectedFile(undefined);
    createForm.resetFields();
  }

  async function submitCreate(values: CreateValues): Promise<void> {
    if (!selectedFile) {
      message.error('请选择模板文件');
      return;
    }
    setSubmitting(true);
    try {
      const input: CreateImportExportTemplateInput = {
        ...values,
        templateName: values.templateName.trim(),
        version: values.version.trim(),
        defaultTemplate: values.defaultTemplate ?? false,
        file: selectedFile,
        fields: values.templateType === 'IMPORT' ? normalizeFields(values.fields) : []
      };
      await importExportTemplateApi.create(input);
      message.success('模板版本已创建');
      closeCreate();
      await loadList(query);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function changeTemplate(
    template: ImportExportTemplateRecord,
    action: () => Promise<ImportExportTemplateRecord>,
    successText: string
  ): Promise<void> {
    setPendingId(template.id);
    try {
      await action();
      message.success(successText);
      await loadList(query);
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 409) {
        message.warning('模板状态已变化，已刷新最新数据');
        await loadList(query);
      } else {
        message.error(toMessage(error));
      }
    } finally {
      setPendingId(undefined);
    }
  }

  async function download(template: ImportExportTemplateRecord): Promise<void> {
    setPendingId(template.id);
    try {
      const blob = await importExportTemplateApi.download(template.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = template.fileName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setPendingId(undefined);
    }
  }

  async function openFields(template: ImportExportTemplateRecord): Promise<void> {
    setFieldTemplate(template);
    setFieldLoading(true);
    try {
      const fields = await importExportTemplateApi.fields(template.id);
      fieldForm.setFieldsValue({ fields: fields.map(toFieldInput) });
    } catch (error) {
      message.error(toMessage(error));
      setFieldTemplate(undefined);
    } finally {
      setFieldLoading(false);
    }
  }

  function closeFields(): void {
    setFieldTemplate(undefined);
    fieldForm.resetFields();
  }

  async function submitFields(values: FieldValues): Promise<void> {
    if (!fieldTemplate || !fieldEditable) return;
    setFieldSubmitting(true);
    try {
      const fields = normalizeFields(values.fields);
      await importExportTemplateApi.replaceFields(fieldTemplate.id, fieldTemplate.versionNo, fields);
      message.success('字段映射已保存');
      closeFields();
      await loadList(query);
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 409) {
        message.warning('模板版本已变化，已刷新最新数据');
        closeFields();
        await loadList(query);
      } else {
        message.error(toMessage(error));
      }
    } finally {
      setFieldSubmitting(false);
    }
  }

  const columns: NonNullable<TableProps<ImportExportTemplateRecord>['columns']> = [
    { title: '模板名称', dataIndex: 'templateName', key: 'templateName', width: 190 },
    {
      title: '类型', dataIndex: 'templateType', key: 'templateType', width: 90,
      render: (value: ImportExportTemplateType) => value === 'IMPORT' ? '导入' : '导出'
    },
    {
      title: '适用模块', dataIndex: 'moduleCode', key: 'moduleCode', width: 130,
      render: (value: string) => optionName(options.modules, value)
    },
    { title: '版本', dataIndex: 'version', key: 'version', width: 100 },
    {
      title: '模板文件', key: 'file', width: 220,
      render: (_: unknown, item: ImportExportTemplateRecord) => (
        <div className="template-file-meta">
          <span>{item.fileName}</span>
          <small>{formatSize(item.sizeBytes)}</small>
        </div>
      )
    },
    {
      title: '默认', dataIndex: 'defaultTemplate', key: 'defaultTemplate', width: 80,
      render: (value: boolean) => value ? <Tag color="gold">默认</Tag> : <span className="secondary-text">否</span>
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 90,
      render: (value: ImportExportTemplateStatus) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      )
    },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170, render: formatTime },
    {
      title: '操作', key: 'actions', width: canManage ? 190 : 96, fixed: 'right',
      render: (_: unknown, item: ImportExportTemplateRecord) => (
        <Space size={2}>
          <Tooltip title="下载模板">
            <Button actionKey="import-export-templates.import-export-template-management-page.1"
              type="text" icon={<Download size={16} />} aria-label={`下载-${item.templateName}`}
              loading={pendingId === item.id} onClick={() => void download(item)}
            />
          </Tooltip>
          {item.templateType === 'IMPORT' ? (
            <Tooltip title="字段映射">
              <Button actionKey="import-export-templates.import-export-template-management-page.2"
                type="text" icon={<Columns3 size={16} />} aria-label={`字段映射-${item.templateName}`}
                disabled={pendingId === item.id} onClick={() => void openFields(item)}
              />
            </Tooltip>
          ) : null}
          {canManage && item.status === 'ENABLED' && !item.defaultTemplate ? (
            <Tooltip title="设为默认">
              <Button actionKey="import-export-templates.import-export-template-management-page.3"
                type="text" icon={<Star size={16} />} aria-label={`设为默认-${item.templateName}`}
                disabled={pendingId === item.id}
                onClick={() => void changeTemplate(item,
                  () => importExportTemplateApi.setDefault(item.id, item.versionNo), '默认模板已更新')}
              />
            </Tooltip>
          ) : null}
          {canManage ? (
            <Tooltip title={item.status === 'ENABLED' ? '停用模板' : '启用模板'}>
              <Button actionKey="import-export-templates.import-export-template-management-page.4"
                type="text" danger={item.status === 'ENABLED'}
                icon={item.status === 'ENABLED' ? <PowerOff size={16} /> : <Power size={16} />}
                aria-label={`${item.status === 'ENABLED' ? '停用' : '启用'}-${item.templateName}`}
                disabled={pendingId === item.id}
                onClick={() => void changeTemplate(
                  item,
                  () => item.status === 'ENABLED'
                    ? importExportTemplateApi.disable(item.id, item.versionNo)
                    : importExportTemplateApi.enable(item.id, item.versionNo),
                  item.status === 'ENABLED' ? '模板已停用' : '模板已启用'
                )}
              />
            </Tooltip>
          ) : null}
        </Space>
      )
    }
  ];

  return (
    <div className="page-stack import-export-template-page">
      <div className="page-heading">
        <h1>导入导出模板</h1>
        {canManage ? (
          <Button actionKey="IMPORT_EXPORT_TEMPLATE_MANAGE" type="primary" icon={<FilePlus2 size={16} />} onClick={openCreate}>
            新增模板版本
          </Button>
        ) : null}
      </div>

      {errorMessage ? (
        <Alert
          type="error" showIcon message={errorMessage}
          action={<Button actionKey="import-export-templates.import-export-template-management-page.6" size="small" aria-label="重试" onClick={() => void loadInitial()}>重试</Button>}
        />
      ) : null}

      <section className="template-filter-panel" aria-label="模板筛选">
        <Form name="template-filter-form" form={filterForm} layout="inline" className="directory-filters" onFinish={submitFilter}>
          <Form.Item label="模板名称" name="templateName"><Input allowClear /></Form.Item>
          <Form.Item label="模板类型" name="templateType">
            <Select allowClear className="filter-select" options={selectOptions(options.templateTypes)} />
          </Form.Item>
          <Form.Item label="适用模块" name="moduleCode">
            <Select allowClear className="filter-select" options={selectOptions(options.modules)} />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Select allowClear className="filter-select" options={selectOptions(options.statuses)} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button actionKey="import-export-templates.import-export-template-management-page.7" htmlType="submit" icon={<Search size={16} />} aria-label="查询模板">查询</Button>
              <Button actionKey="import-export-templates.import-export-template-management-page.8" icon={<RotateCcw size={16} />} aria-label="重置筛选" onClick={() => void resetFilter()}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </section>

      <section className="template-table-panel" aria-label="模板台账">
        <Table<ImportExportTemplateRecord>
          rowKey="id" size="small" loading={loading} dataSource={templates} columns={columns}
          scroll={{ x: 1230 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
          locale={{ emptyText: '暂无导入导出模板' }}
        />
      </section>

      <Modal actionPrefix="import-export-templates.import-export-template-management-page.modal.1"
        open={createOpen} title="新增模板版本" width="min(880px, 92vw)" styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} className="template-create-modal"
        okText="保存模板" cancelText="取消" confirmLoading={submitting}
        onOk={() => createForm.submit()} onCancel={closeCreate} destroyOnHidden
      >
        <Form<CreateValues>
          name="template-create-form" form={createForm} layout="vertical" preserve={false} onFinish={submitCreate}
          initialValues={{ defaultTemplate: false, fields: [emptyField()] }}
        >
          <Form.Item label="模板名称" name="templateName" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input maxLength={100} />
          </Form.Item>
          <div className="template-create-grid">
            <Form.Item label="模板类型" name="templateType" rules={[{ required: true, message: '请选择模板类型' }]}>
              <Select options={selectOptions(options.templateTypes)} />
            </Form.Item>
            <Form.Item label="适用模块" name="moduleCode" rules={[{ required: true, message: '请选择适用模块' }]}>
              <Select options={selectOptions(options.modules)} />
            </Form.Item>
          </div>
          <Form.Item label="模板版本" name="version" rules={[{ required: true, message: '请输入模板版本' }]}>
            <Input maxLength={32} />
          </Form.Item>
          {createTemplateType === 'IMPORT' ? (
            <div className="template-field-editor">
              <div className="template-field-editor-heading"><strong>字段映射</strong></div>
              <EditableFieldList form={createForm} editable ariaLabel="导入字段映射" />
            </div>
          ) : null}
          <Form.Item label="模板文件" required>
            <Upload
              maxCount={1}
              beforeUpload={(file) => { setSelectedFile(file); return false; }}
              onRemove={() => { setSelectedFile(undefined); return true; }}
            >
              <Button actionKey="import-export-templates.import-export-template-management-page.9" icon={<UploadIcon size={16} />}>选择模板文件</Button>
            </Upload>
          </Form.Item>
          <Form.Item label="设为默认模板" name="defaultTemplate" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={Boolean(fieldTemplate)}
        title={fieldTemplate ? `${fieldTemplate.templateName}字段映射` : '字段映射'}
        width="min(880px, 92vw)"
        loading={fieldLoading}
        destroyOnHidden
        onClose={closeFields}
        extra={fieldEditable ? (
          <Button actionKey="import-export-templates.import-export-template-management-page.10" type="primary" loading={fieldSubmitting} onClick={() => fieldForm.submit()}>
            保存字段映射
          </Button>
        ) : null}
      >
        {fieldTemplate?.status === 'ENABLED' ? (
          <Alert type="info" showIcon message="启用中的模板字段仅可查看，停用后才能修改。" />
        ) : null}
        <Form<FieldValues>
          name="template-field-form"
          form={fieldForm}
          layout="vertical"
          disabled={!fieldEditable}
          preserve={false}
          onFinish={submitFields}
        >
          <EditableFieldList form={fieldForm} editable={fieldEditable} ariaLabel="历史模板字段映射" />
        </Form>
      </Drawer>
    </div>
  );
}

interface EditableFieldListProps {
  form: FormInstance;
  editable: boolean;
  ariaLabel: string;
}

function EditableFieldList({ form, editable, ariaLabel }: EditableFieldListProps) {
  return (
    <Form.List name="fields" rules={[{ validator: async (_, fields) => {
      if (!fields?.length) throw new Error('导入模板至少配置一个字段');
    } }]}> 
      {(fields, { add, remove, move }, { errors }) => <>
        <div className="template-field-table" role="table" aria-label={ariaLabel}>
          {fields.map((field, index) => {
            const { key, ...fieldProps } = field;
            return <div className="template-field-row" role="row" key={key}>
              <Form.Item {...fieldProps} label={`字段编码-${index + 1}`} name={[field.name, 'fieldCode']} rules={[{ required: true, message: '请输入字段编码' }]}>
                <Input maxLength={64} />
              </Form.Item>
              <Form.Item {...fieldProps} label={`表头名称-${index + 1}`} name={[field.name, 'columnName']} rules={[{ required: true, message: '请输入表头名称' }]}>
                <Input maxLength={100} />
              </Form.Item>
              <Form.Item {...fieldProps} label={`数据类型-${index + 1}`} name={[field.name, 'dataType']} rules={[{ required: true, message: '请选择类型' }]}>
                <Select
                  options={fieldTypeOptions}
                  onChange={(value) => {
                    if (value !== 'TEXT') form.setFieldValue(['fields', field.name, 'maxLength'], undefined);
                    if (value === 'BOOLEAN') form.setFieldValue(['fields', field.name, 'dictionaryTypeCode'], undefined);
                  }}
                />
              </Form.Item>
              <Form.Item noStyle shouldUpdate>
                {({ getFieldValue }) => (
                  <Form.Item {...fieldProps} label={`最大长度-${index + 1}`} name={[field.name, 'maxLength']}>
                    <InputNumber min={1} max={1000} disabled={!editable || getFieldValue(['fields', field.name, 'dataType']) !== 'TEXT'} />
                  </Form.Item>
                )}
              </Form.Item>
              <Form.Item noStyle shouldUpdate>
                {({ getFieldValue }) => (
                  <Form.Item {...fieldProps} label={`数据字典编码-${index + 1}`} name={[field.name, 'dictionaryTypeCode']}>
                    <Input maxLength={64} disabled={!editable || getFieldValue(['fields', field.name, 'dataType']) === 'BOOLEAN'} />
                  </Form.Item>
                )}
              </Form.Item>
              <Form.Item {...fieldProps} name={[field.name, 'required']} valuePropName="checked"><Checkbox>必填</Checkbox></Form.Item>
              {editable ? <Space size={0} className="template-field-actions">
                <Tooltip title="上移"><Button actionKey="import-export-templates.import-export-template-management-page.11" type="text" aria-label={`上移字段-${index + 1}`} icon={<ArrowUp size={16} />} disabled={index === 0} onClick={() => move(index, index - 1)} /></Tooltip>
                <Tooltip title="下移"><Button actionKey="import-export-templates.import-export-template-management-page.12" type="text" aria-label={`下移字段-${index + 1}`} icon={<ArrowDown size={16} />} disabled={index === fields.length - 1} onClick={() => move(index, index + 1)} /></Tooltip>
                <Tooltip title="删除字段"><Button actionKey="import-export-templates.import-export-template-management-page.13" type="text" danger aria-label={`删除字段-${index + 1}`} icon={<Trash2 size={16} />} disabled={fields.length === 1} onClick={() => remove(field.name)} /></Tooltip>
              </Space> : null}
            </div>;
          })}
        </div>
        <Form.ErrorList errors={errors} />
        {editable ? <Button actionKey="import-export-templates.import-export-template-management-page.14" type="dashed" icon={<Plus size={16} />} onClick={() => add(emptyField())}>新增字段</Button> : null}
      </>}
    </Form.List>
  );
}

function cleanQuery(values: FilterValues): ImportExportTemplateQuery {
  const templateName = values.templateName?.trim();
  return {
    ...(templateName ? { templateName } : {}),
    ...(values.templateType ? { templateType: values.templateType } : {}),
    ...(values.moduleCode ? { moduleCode: values.moduleCode } : {}),
    ...(values.status ? { status: values.status } : {})
  };
}

function selectOptions(options: { code: string; name: string }[]) {
  return options.map((option) => ({ value: option.code, label: option.name }));
}

function optionName(options: { code: string; name: string }[], code: string): string {
  return options.find((option) => option.code === code)?.name ?? code;
}

function formatSize(sizeBytes: number): string {
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`;
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`;
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}

const fieldTypeOptions = [
  { value: 'TEXT', label: '文本' }, { value: 'INTEGER', label: '整数' },
  { value: 'DECIMAL', label: '小数' }, { value: 'DATE', label: '日期' },
  { value: 'DATETIME', label: '日期时间' }, { value: 'BOOLEAN', label: '布尔' }
];

function emptyField(): ImportTemplateFieldInput {
  return { fieldCode: '', columnName: '', dataType: 'TEXT', required: false, maxLength: 100, sortOrder: 10 };
}

function toFieldInput(field: ImportTemplateFieldInput): ImportTemplateFieldInput {
  return {
    fieldCode: field.fieldCode,
    columnName: field.columnName,
    dataType: field.dataType,
    required: field.required,
    maxLength: field.maxLength,
    dictionaryTypeCode: field.dictionaryTypeCode,
    sortOrder: field.sortOrder
  };
}

function normalizeFields(fields: ImportTemplateFieldInput[]): ImportTemplateFieldInput[] {
  return fields.map((field, index) => ({
    fieldCode: field.fieldCode.trim().toUpperCase(),
    columnName: field.columnName.trim(),
    dataType: field.dataType,
    required: field.required ?? false,
    maxLength: field.dataType === 'TEXT' ? field.maxLength : undefined,
    dictionaryTypeCode: field.dataType === 'BOOLEAN'
      ? undefined
      : field.dictionaryTypeCode?.trim().toUpperCase() || undefined,
    sortOrder: (index + 1) * 10
  }));
}
