import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { Alert, DatePicker, Drawer, Form, Input, InputNumber, message, Modal, Select, Space, Switch, Tabs, Tag, Tooltip } from 'antd';
import { CircleCheck, CircleOff, Link2, Pencil, Plus, RotateCw, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { formatDateTime as formatTime } from '../../utils/datetime';
import {
  attachmentManagementApi,
  type AttachmentFileLedgerRecord,
  type AttachmentFileQuery,
  type AttachmentFileStatus,
  type AttachmentRelationLedgerRecord,
  type AttachmentRuleQuery,
  type AttachmentRuleRecord,
  type AttachmentRuleStatus,
  type CreateAttachmentRuleInput,
  type UpdateAttachmentRuleInput
} from '../../api/attachment-management';

interface AttachmentManagementPageProps {
  canReadRules: boolean;
  canManage: boolean;
  canReadFiles: boolean;
}

interface RuleFormValues {
  moduleCode?: string;
  fileCategory?: string;
  ruleName: string;
  allowedExtensions: string;
  maxFileSizeMb: number;
  maxBatchCount: number;
  previewEnabled: boolean;
}

interface FileFilterValues extends Omit<AttachmentFileQuery, 'createdFrom' | 'createdTo'> {
  createdRange?: [dayjs: { toISOString(): string }, dayjs: { toISOString(): string }];
}

export function AttachmentManagementPage({ canReadRules, canManage, canReadFiles }: AttachmentManagementPageProps) {
  const [activeTab, setActiveTab] = useState(canReadRules ? 'rules' : 'files');
  const [rules, setRules] = useState<AttachmentRuleRecord[]>([]);
  const [files, setFiles] = useState<AttachmentFileLedgerRecord[]>([]);
  const [relations, setRelations] = useState<AttachmentRelationLedgerRecord[]>([]);
  const [relationFile, setRelationFile] = useState<AttachmentFileLedgerRecord>();
  const [editingRule, setEditingRule] = useState<AttachmentRuleRecord>();
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [relationLoading, setRelationLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>();
  const [ruleQuery, setRuleQuery] = useState<AttachmentRuleQuery>({});
  const [fileQuery, setFileQuery] = useState<AttachmentFileQuery>({});
  const [ruleForm] = Form.useForm<RuleFormValues>();
  const [ruleFilterForm] = Form.useForm<AttachmentRuleQuery>();
  const [fileFilterForm] = Form.useForm<FileFilterValues>();

  useEffect(() => {
    if (activeTab === 'rules' && canReadRules) void loadRules(ruleQuery);
    if (activeTab === 'files' && canReadFiles) void loadFiles(fileQuery);
  }, [activeTab, canReadFiles, canReadRules]);

  async function loadRules(query: AttachmentRuleQuery = ruleQuery): Promise<void> {
    setLoading(true);
    setErrorMessage(undefined);
    try {
      setRules(await attachmentManagementApi.listRules(query));
    } catch (error) {
      setRules([]);
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function loadFiles(query: AttachmentFileQuery = fileQuery): Promise<void> {
    setLoading(true);
    setErrorMessage(undefined);
    try {
      setFiles(await attachmentManagementApi.listFiles(query));
    } catch (error) {
      setFiles([]);
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  function openCreateRule(): void {
    setEditingRule(undefined);
    ruleForm.setFieldsValue({
      moduleCode: '', fileCategory: '', ruleName: '', allowedExtensions: '',
      maxFileSizeMb: 10, maxBatchCount: 1, previewEnabled: true
    });
    setRuleModalOpen(true);
  }

  function openEditRule(rule: AttachmentRuleRecord): void {
    setEditingRule(rule);
    ruleForm.setFieldsValue({
      moduleCode: rule.moduleCode, fileCategory: rule.fileCategory, ruleName: rule.ruleName,
      allowedExtensions: rule.allowedExtensions.join(','),
      maxFileSizeMb: rule.maxFileSizeBytes / 1024 / 1024,
      maxBatchCount: rule.maxBatchCount, previewEnabled: rule.previewEnabled
    });
    setRuleModalOpen(true);
  }

  async function saveRule(values: RuleFormValues): Promise<void> {
    const common = {
      ruleName: values.ruleName.trim(),
      allowedExtensions: splitExtensions(values.allowedExtensions),
      maxFileSizeBytes: Math.round(values.maxFileSizeMb * 1024 * 1024),
      maxBatchCount: values.maxBatchCount,
      previewEnabled: values.previewEnabled
    };
    setSubmitting(true);
    try {
      if (editingRule) {
        await attachmentManagementApi.updateRule(editingRule.id, {
          ...common, versionNo: editingRule.versionNo
        } satisfies UpdateAttachmentRuleInput);
        message.success('附件规则已更新');
      } else {
        await attachmentManagementApi.createRule({
          ...common,
          moduleCode: values.moduleCode?.trim() ?? '',
          fileCategory: values.fileCategory?.trim() ?? ''
        } satisfies CreateAttachmentRuleInput);
        message.success('附件规则已新增');
      }
      setRuleModalOpen(false);
      await loadRules();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function changeRuleStatus(rule: AttachmentRuleRecord): Promise<void> {
    setLoading(true);
    try {
      if (rule.status === 'ENABLED') {
        await attachmentManagementApi.disableRule(rule.id, rule.versionNo);
        message.success('附件规则已停用');
      } else {
        await attachmentManagementApi.enableRule(rule.id, rule.versionNo);
        message.success('附件规则已启用');
      }
      await loadRules();
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function queryRules(values: AttachmentRuleQuery): Promise<void> {
    setRuleQuery(values);
    await loadRules(values);
  }

  async function queryFiles(values: FileFilterValues): Promise<void> {
    const query: AttachmentFileQuery = {
      originalName: values.originalName,
      moduleCode: values.moduleCode,
      fileCategory: values.fileCategory,
      status: values.status,
      uploaderId: values.uploaderId,
      createdFrom: values.createdRange?.[0].toISOString(),
      createdTo: values.createdRange?.[1].toISOString()
    };
    setFileQuery(query);
    await loadFiles(query);
  }

  async function openRelations(file: AttachmentFileLedgerRecord): Promise<void> {
    setRelationFile(file);
    setRelations([]);
    setRelationLoading(true);
    try {
      setRelations(await attachmentManagementApi.listRelations(file.id));
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setRelationLoading(false);
    }
  }

  const rulePanel = (
    <div className="attachment-table-panel">
      <Form name="attachment-rule-filter" form={ruleFilterForm} layout="inline" className="directory-filters" onFinish={queryRules}>
        <Form.Item label="规则名称" name="ruleName"><Input allowClear maxLength={100} /></Form.Item>
        <Form.Item label="模块编码" name="moduleCode"><Input allowClear maxLength={64} /></Form.Item>
        <Form.Item label="文件分类" name="fileCategory"><Input allowClear maxLength={64} /></Form.Item>
        <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={ruleStatusOptions} /></Form.Item>
        <Form.Item><Button actionKey="attachment-management.attachment-management-page.1" htmlType="submit" icon={<Search size={16} />}>查询规则</Button></Form.Item>
      </Form>
      <Table<AttachmentRuleRecord>
        rowKey="id" size="small" loading={loading} dataSource={rules} scroll={{ x: 1120 }}
        pagination={{ pageSize: 20, hideOnSinglePage: true }} locale={{ emptyText: '暂无附件规则' }}
        columns={[
          { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 180 },
          { title: '模块编码', dataIndex: 'moduleCode', key: 'moduleCode', width: 190 },
          { title: '文件分类', dataIndex: 'fileCategory', key: 'fileCategory', width: 130 },
          { title: '允许格式', dataIndex: 'allowedExtensions', key: 'allowedExtensions', width: 180, render: (items: string[]) => items.join('、') },
          { title: '单文件上限', dataIndex: 'maxFileSizeBytes', key: 'maxFileSizeBytes', width: 120, render: formatSize },
          { title: '单批数量', dataIndex: 'maxBatchCount', key: 'maxBatchCount', width: 90 },
          { title: '预览', dataIndex: 'previewEnabled', key: 'previewEnabled', width: 72, render: (value: boolean) => value ? '允许' : '关闭' },
          { title: '状态', dataIndex: 'status', key: 'status', width: 88, render: ruleStatusTag },
          ...(canManage ? [{
            title: '操作', key: 'actions', width: 104, fixed: 'right' as const,
            render: (_: unknown, item: AttachmentRuleRecord) => <Space size={2}>
              <Tooltip title="编辑"><Button actionKey="attachment-management.attachment-management-page.2" type="text" icon={<Pencil size={16} />} aria-label={`编辑-${item.ruleName}`} onClick={() => openEditRule(item)} /></Tooltip>
              <Tooltip title={item.status === 'ENABLED' ? '停用' : '启用'}>
                <Button actionKey="attachment-management.attachment-management-page.3" type="text" danger={item.status === 'ENABLED'}
                  icon={item.status === 'ENABLED' ? <CircleOff size={16} /> : <CircleCheck size={16} />}
                  aria-label={`${item.status === 'ENABLED' ? '停用' : '启用'}-${item.ruleName}`}
                  onClick={() => void changeRuleStatus(item)} />
              </Tooltip>
            </Space>
          }] : [])
        ]}
      />
    </div>
  );

  const filePanel = (
    <div className="attachment-table-panel">
      <Form name="attachment-file-filter" form={fileFilterForm} layout="inline" className="directory-filters" onFinish={queryFiles}>
        <Form.Item label="文件名称" name="originalName"><Input allowClear maxLength={255} /></Form.Item>
        <Form.Item label="模块编码" name="moduleCode"><Input allowClear maxLength={64} /></Form.Item>
        <Form.Item label="文件分类" name="fileCategory"><Input allowClear maxLength={64} /></Form.Item>
        <Form.Item label="状态" name="status"><Select allowClear className="filter-select" options={fileStatusOptions} /></Form.Item>
        <Form.Item label="上传人标识" name="uploaderId"><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item label="创建时间" name="createdRange"><DatePicker.RangePicker showTime /></Form.Item>
        <Form.Item><Button actionKey="attachment-management.attachment-management-page.4" htmlType="submit" icon={<Search size={16} />}>查询文件</Button></Form.Item>
      </Form>
      <Table<AttachmentFileLedgerRecord>
        rowKey="id" size="small" loading={loading} dataSource={files} scroll={{ x: 1420 }}
        pagination={{ pageSize: 20, hideOnSinglePage: true }} locale={{ emptyText: '暂无附件文件记录' }}
        columns={[
          { title: '文件名称', dataIndex: 'originalName', key: 'originalName', width: 210, ellipsis: true },
          { title: '模块编码', dataIndex: 'moduleCode', key: 'moduleCode', width: 190 },
          { title: '文件分类', dataIndex: 'fileCategory', key: 'fileCategory', width: 120 },
          { title: '内容类型', dataIndex: 'contentType', key: 'contentType', width: 150 },
          { title: '大小', dataIndex: 'sizeBytes', key: 'sizeBytes', width: 100, render: formatSize },
          { title: '上传人', key: 'uploader', width: 180, render: (_: unknown, item: AttachmentFileLedgerRecord) => <div className="attachment-uploader"><span>{item.uploaderName}</span><small>{item.uploaderId}</small></div> },
          { title: '摘要', dataIndex: 'contentSha256Present', key: 'contentSha256Present', width: 84, render: (value: boolean) => value ? '已记录' : '未记录' },
          { title: '状态', dataIndex: 'status', key: 'status', width: 88, render: fileStatusTag },
          { title: '上传时间', dataIndex: 'uploadedAt', key: 'uploadedAt', width: 170, render: formatTime },
          { title: '操作', key: 'actions', width: 78, fixed: 'right', render: (_: unknown, item: AttachmentFileLedgerRecord) =>
            <Tooltip title="查看业务关系"><Button actionKey="attachment-management.attachment-management-page.5" type="text" icon={<Link2 size={16} />} aria-label={`查看关系-${item.originalName}`} onClick={() => void openRelations(item)} /></Tooltip> }
        ]}
      />
    </div>
  );

  return <div className="page-stack">
    <div className="page-heading">
      <h1>附件管理</h1>
      {canManage && activeTab === 'rules' && <Button actionKey="ATTACHMENT_RULE_MANAGE" type="primary" icon={<Plus size={16} />} onClick={openCreateRule}>新增附件规则</Button>}
    </div>
    {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button actionKey="attachment-management.attachment-management-page.7" size="small" icon={<RotateCw size={14} />} onClick={() => void (activeTab === 'rules' ? loadRules() : loadFiles())}>重试</Button>} />}
    <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
      canReadRules ? { key: 'rules', label: '附件规则', children: rulePanel } : null,
      canReadFiles ? { key: 'files', label: '文件台账', children: filePanel } : null
    ].filter((item): item is NonNullable<typeof item> => item !== null)} />

    <Modal title={editingRule ? '编辑附件规则' : '新增附件规则'} open={ruleModalOpen} footer={null}
      onCancel={() => setRuleModalOpen(false)} destroyOnHidden width="min(880px, 92vw)" styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }}>
      <Form name="attachment-rule-editor" form={ruleForm} layout="vertical" onFinish={saveRule}>
        {!editingRule && <div className="responsive-form-grid">
          <Form.Item label="模块编码" name="moduleCode" rules={[{ required: true, message: '请输入模块编码' }, { max: 64 }]}><Input maxLength={64} /></Form.Item>
          <Form.Item label="文件分类" name="fileCategory" rules={[{ required: true, message: '请输入文件分类' }, { max: 64 }]}><Input maxLength={64} /></Form.Item>
        </div>}
        <div className="responsive-form-grid">
          <Form.Item label="规则名称" name="ruleName" rules={[{ required: true, message: '请输入规则名称' }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
          <Form.Item label="允许扩展名" name="allowedExtensions" rules={[{ required: true, message: '请输入允许扩展名' }]}><Input placeholder="jpg,png,pdf" /></Form.Item>
          <Form.Item label="单文件上限（MB）" name="maxFileSizeMb" rules={[{ required: true }]}><InputNumber min={0.01} max={10240} precision={2} className="full-width" /></Form.Item>
          <Form.Item label="单批数量" name="maxBatchCount" rules={[{ required: true }]}><InputNumber min={1} max={999} precision={0} className="full-width" /></Form.Item>
          <Form.Item label="允许预览" name="previewEnabled" valuePropName="checked"><Switch /></Form.Item>
        </div>
        <div className="form-actions"><Button actionKey="attachment-management.attachment-management-page.8" onClick={() => setRuleModalOpen(false)}>取消</Button><Button actionKey="attachment-management.attachment-management-page.9" type="primary" htmlType="submit" loading={submitting}>保存规则</Button></div>
      </Form>
    </Modal>

    <Drawer title={relationFile ? `业务关系 · ${relationFile.originalName}` : '业务关系'} width="min(1100px, 92vw)"
      open={Boolean(relationFile)} onClose={() => setRelationFile(undefined)}>
      <Table<AttachmentRelationLedgerRecord> rowKey="id" size="small" loading={relationLoading} dataSource={relations}
        scroll={{ x: 880 }} pagination={false} locale={{ emptyText: '暂无业务关系记录' }} columns={[
          { title: '业务标识', dataIndex: 'businessId', key: 'businessId', width: 190 },
          { title: '模块编码', dataIndex: 'moduleCode', key: 'moduleCode', width: 190 },
          { title: '关系类型', dataIndex: 'relationType', key: 'relationType', width: 120 },
          { title: '可见范围', dataIndex: 'visibleScope', key: 'visibleScope', width: 170 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 80, render: relationStatusTag },
          { title: '建立时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatTime },
          { title: '解除时间', dataIndex: 'releasedAt', key: 'releasedAt', width: 170, render: formatTime }
        ]} />
    </Drawer>
  </div>;
}

const ruleStatusOptions = [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }];
const fileStatusOptions = [{ value: 'UPLOADING', label: '上传中' }, { value: 'AVAILABLE', label: '可用' }, { value: 'RETIRED', label: '已退役' }];

function splitExtensions(value: string): string[] {
  return value.split(/[,，\s]+/).map((item) => item.trim()).filter(Boolean);
}

function ruleStatusTag(value: AttachmentRuleStatus) {
  return <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>;
}

function fileStatusTag(value: AttachmentFileStatus) {
  const labels: Record<AttachmentFileStatus, string> = { UPLOADING: '上传中', AVAILABLE: '可用', RETIRED: '已退役' };
  const colors: Record<AttachmentFileStatus, string> = { UPLOADING: 'processing', AVAILABLE: 'success', RETIRED: 'default' };
  return <Tag color={colors[value]}>{labels[value]}</Tag>;
}

function relationStatusTag(value: AttachmentRelationLedgerRecord['status']) {
  return <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value === 'ACTIVE' ? '活动' : '已解除'}</Tag>;
}

function formatSize(value: number): string {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(value % (1024 * 1024) === 0 ? 0 : 2)} MB`;
  if (value >= 1024) return `${(value / 1024).toFixed(value % 1024 === 0 ? 0 : 1)} KB`;
  return `${value} B`;
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
