import { Alert, Button, Checkbox, Form, Input, Modal, Select, Spin, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { exportJobApi, type CreateExportJobInput, type ExportJobOptions, type ExportJobType } from '../../api/export-jobs';

interface CreateExportJobModalProps {
  accessRevision?: string;
  open: boolean;
  canCreateOrdinary: boolean;
  canSubmitSensitive: boolean;
  canExportDictionary?: boolean;
  canExportTemplate?: boolean;
  canExportInterface?: boolean;
  canExportCache?: boolean;
  canExportSystemTasks?: boolean;
  canExportRewards?: boolean;
  canExportExceptions?: boolean;
  canExportAttachments?: boolean;
  defaultExportType?: ExportJobType;
  defaultStudentId?: string;
  onCancel: () => void;
  onCreated: () => void;
}

interface FormValues {
  attachmentModuleCode?: string;
  attachmentUploaderId?: string;
  attachmentFileCategory?: string;
  exceptionClassId?: string;
  exceptionType?: CreateExportJobInput['exceptionType'];
  exceptionStatus?: CreateExportJobInput['exceptionStatus'];
  rewardExchangeStatus?: CreateExportJobInput['rewardExchangeStatus'];
  systemTaskType?: string;
  systemTaskStatus?: string;
  cacheDomain?: string;
  cacheStatus?: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED';
  interfaceCallerName?: string;
  interfaceStatus?: 'ENABLED' | 'DISABLED';
  interfaceOwnerId?: string;
  templateType?: 'IMPORT' | 'EXPORT';
  templateModuleCode?: string;
  templateStatus?: 'ENABLED' | 'DISABLED';
  exportType: ExportJobType;
  studentId?: string;
  startedAt?: string;
  endedAt?: string;
  eventType?: string;
  dictionaryTypeCode?: string;
  dictionaryStatus?: 'ENABLED' | 'DISABLED';
  columns: string[];
  reason: string;
}

const eventTypeOptions = [
  ['USER_CREATE', '用户新增'], ['USER_STATUS_CHANGE', '用户状态变更'],
  ['USER_ORGANIZATION_ASSOCIATE', '用户关联组织'], ['USER_ROLE_ASSIGN', '用户分配角色'],
  ['ROLE_CREATE', '角色新增'], ['PERMISSION_CREATE', '权限新增'],
  ['ROLE_PERMISSION_CONFIGURE', '角色权限配置'], ['ROLE_PERMISSION_REMOVE', '角色权限移除'],
  ['USER_PERMISSION_CONFIGURE', '用户权限配置'], ['USER_PERMISSION_REMOVE', '用户权限移除'],
  ['ROLE_DATA_SCOPE_ADD', '角色数据范围新增'], ['ORGANIZATION_ADMIN_ASSIGN', '组织管理员配置']
].map(([value, label]) => ({ value, label }));

export function CreateExportJobModal(props: CreateExportJobModalProps) {
  const [form] = Form.useForm<FormValues>();
  const [options, setOptions] = useState<ExportJobOptions>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();
  const optionRequest = useRef(0);
  const allowedTypes = useMemo(() => [
    ...(props.canCreateOrdinary ? [{ value: 'GROWTH_POINT_LEDGER' as const, label: '积分台账' }] : []),
    ...(props.canSubmitSensitive ? [{ value: 'IAM_CHANGE_AUDIT' as const, label: '权限变更审计' }] : []),
    ...(props.canExportDictionary ? [{ value: 'DICTIONARY_LEDGER' as const, label: '数据字典台账' }] : []),
    ...(props.canExportTemplate ? [{ value: 'TEMPLATE_LEDGER' as const, label: '导入导出模板台账' }] : []),
    ...(props.canExportInterface ? [{ value: 'INTERFACE_SERVICE_LEDGER' as const, label: '接口服务台账' }] : []),
    ...(props.canExportCache ? [{ value: 'CACHE_OPERATION_LOG' as const, label: '缓存操作日志' }] : []),
    ...(props.canExportSystemTasks ? [{ value: 'SYSTEM_TASK_LEDGER' as const, label: '系统任务审批台账' }] : []),
    ...(props.canExportRewards ? [{ value: 'REWARD_EXCHANGE_LEDGER' as const, label: '奖励兑换报表' }] : []),
    ...(props.canExportAttachments ? [{ value: 'ATTACHMENT_LEDGER' as const, label: '附件管理台账' }] : []),
    ...(props.canExportExceptions ? [{ value: 'EXCEPTION_REPORT_LEDGER' as const, label: '异常报备台账' }] : [])
  ], [props.canCreateOrdinary, props.canSubmitSensitive, props.canExportDictionary, props.canExportTemplate, props.canExportInterface, props.canExportCache, props.canExportSystemTasks, props.canExportRewards, props.canExportExceptions, props.canExportAttachments]);

  useEffect(() => {
    if (!props.open || allowedTypes.length === 0) {
      optionRequest.current++;
      setOptions(undefined);
      return;
    }
    const type = allowedTypes.find(item => item.value === props.defaultExportType)?.value ?? allowedTypes[0].value;
    form.resetFields();
    form.setFieldsValue({ exportType: type });
    void loadOptions(type);
    return () => { optionRequest.current++; };
  }, [props.open, props.defaultExportType, props.defaultStudentId, props.accessRevision, allowedTypes, form]);

  async function loadOptions(type: ExportJobType) {
    const request = ++optionRequest.current;
    setLoading(true);
    setError(undefined);
    setOptions(undefined);
    try {
      const loaded = await exportJobApi.options(type);
      if (request !== optionRequest.current) return;
      setOptions(loaded);
      form.setFieldsValue({
        studentId: ['GROWTH_POINT_LEDGER', 'REWARD_EXCHANGE_LEDGER'].includes(type) && loaded.students.some(student => student.id === props.defaultStudentId) ? props.defaultStudentId : undefined,
        columns: loaded.columns.filter(column => column.defaultSelected).map(column => column.code)
      });
    } catch (cause) {
      if (request === optionRequest.current) setError(toMessage(cause));
    } finally {
      if (request === optionRequest.current) setLoading(false);
    }
  }

  async function submit(values: FormValues) {
    if (!allowedTypes.some(item => item.value === values.exportType) || options?.exportType !== values.exportType) return;
    setSubmitting(true);
    setError(undefined);
    try {
      const input: CreateExportJobInput = {
        exportType: values.exportType,
        attachmentModuleCode: values.exportType === 'ATTACHMENT_LEDGER' ? values.attachmentModuleCode?.trim() || undefined : undefined,
        attachmentUploaderId: values.exportType === 'ATTACHMENT_LEDGER' ? values.attachmentUploaderId?.trim() || undefined : undefined,
        attachmentFileCategory: values.exportType === 'ATTACHMENT_LEDGER' ? values.attachmentFileCategory?.trim() || undefined : undefined,
        studentId: ['GROWTH_POINT_LEDGER', 'REWARD_EXCHANGE_LEDGER'].includes(values.exportType) ? values.studentId : undefined,
        rewardExchangeStatus: values.exportType === 'REWARD_EXCHANGE_LEDGER' ? values.rewardExchangeStatus : undefined,
        exceptionClassId: values.exportType === 'EXCEPTION_REPORT_LEDGER' ? values.exceptionClassId : undefined,
        exceptionType: values.exportType === 'EXCEPTION_REPORT_LEDGER' ? values.exceptionType : undefined,
        exceptionStatus: values.exportType === 'EXCEPTION_REPORT_LEDGER' ? values.exceptionStatus : undefined,
        startedAt: normalizeDateTime(values.startedAt),
        endedAt: normalizeDateTime(values.endedAt),
        eventType: values.exportType === 'IAM_CHANGE_AUDIT' ? values.eventType : undefined,
        dictionaryTypeCode: values.exportType === 'DICTIONARY_LEDGER' ? values.dictionaryTypeCode?.trim() || undefined : undefined,
        dictionaryStatus: values.exportType === 'DICTIONARY_LEDGER' ? values.dictionaryStatus : undefined,
        templateType: values.exportType === 'TEMPLATE_LEDGER' ? values.templateType : undefined,
        templateModuleCode: values.exportType === 'TEMPLATE_LEDGER' ? values.templateModuleCode?.trim() || undefined : undefined,
        templateStatus: values.exportType === 'TEMPLATE_LEDGER' ? values.templateStatus : undefined,
        interfaceCallerName: values.exportType === 'INTERFACE_SERVICE_LEDGER' ? values.interfaceCallerName?.trim() || undefined : undefined,
        interfaceStatus: values.exportType === 'INTERFACE_SERVICE_LEDGER' ? values.interfaceStatus : undefined,
        interfaceOwnerId: values.exportType === 'INTERFACE_SERVICE_LEDGER' ? values.interfaceOwnerId?.trim() || undefined : undefined,
        cacheDomain: values.exportType === 'CACHE_OPERATION_LOG' ? values.cacheDomain : undefined,
        cacheStatus: values.exportType === 'CACHE_OPERATION_LOG' ? values.cacheStatus : undefined,
        systemTaskType: values.exportType === 'SYSTEM_TASK_LEDGER' ? values.systemTaskType : undefined,
        systemTaskStatus: values.exportType === 'SYSTEM_TASK_LEDGER' ? values.systemTaskStatus : undefined,
        columns: values.columns,
        reason: values.reason.trim()
      };
      const created = await exportJobApi.create(input);
      message.success(created.status === 'PENDING_REVIEW' ? '敏感导出已提交审核' : '导出作业已进入队列');
      props.onCreated();
    } catch (cause) {
      setError(toMessage(cause));
    } finally {
      setSubmitting(false);
    }
  }

  const exportType = Form.useWatch('exportType', form);
  return <Modal
    open={props.open}
    title="新建数据导出"
    className="export-job-create-modal"
    okText="提交导出"
    confirmLoading={submitting}
    okButtonProps={{ disabled: loading || !options || !allowedTypes.some(item => item.value === exportType) }}
    onOk={() => form.submit()}
    onCancel={props.onCancel}
    destroyOnHidden
  >
    <Form<FormValues> form={form} layout="vertical" preserve={false} onFinish={(values) => void submit(values)}>
      <Form.Item label="导出类型" name="exportType" rules={[{ required: true, message: '请选择导出类型' }]}>
        <Select disabled={allowedTypes.length === 1} options={allowedTypes} onChange={(type) => void loadOptions(type)} />
      </Form.Item>
      {loading ? <div className="export-options-loading"><Spin size="small" /></div> : null}
      {error ? <Alert type="error" showIcon message={error} /> : null}
      {options ? <>
        <Alert
          type={options.sensitive ? 'warning' : 'info'}
          showIcon
          message={`${options.templateName}（${options.templateVersion}）`}
          description={options.sensitive ? '该导出包含敏感审计信息，提交后由系统审核员审批。' : undefined}
        />
        {['GROWTH_POINT_LEDGER', 'REWARD_EXCHANGE_LEDGER'].includes(exportType) ? <Form.Item
          label="学生" name="studentId" rules={[{ required: true, message: '请选择学生' }]}
        ><Select options={options.students.map(student => ({ value: student.id, label: student.name }))} /></Form.Item> : null}
        {exportType === 'ATTACHMENT_LEDGER' ? <>
          <Form.Item label="模块编码" name="attachmentModuleCode"><Input placeholder="全部模块" /></Form.Item>
          <Form.Item label="上传人 ID" name="attachmentUploaderId" rules={[{ validator: (_, value?: string) => {
            const id = value?.trim();
            return !id || (/^[1-9]\d{18}$/.test(id) && id <= '9223372036854775807') ? Promise.resolve() : Promise.reject(new Error('请输入有效的 19 位上传人 ID'));
          } }]}><Input placeholder="按上传人 ID 筛选" /></Form.Item>
          <Form.Item label="文件分类" name="attachmentFileCategory"><Input placeholder="全部文件分类" /></Form.Item>
        </> : null}
        {exportType === 'EXCEPTION_REPORT_LEDGER' ? <>
          <Form.Item label="班级" name="exceptionClassId"><Select allowClear placeholder="全部授权有效班级" options={(options.exceptionClasses ?? []).map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
          <Form.Item label="异常类型" name="exceptionType"><Select allowClear placeholder="全部类型" options={Object.entries({ ATTENDANCE: '出勤异常', LEARNING_STATUS: '学习状态异常', MENTAL_STATE: '心态异常' }).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item label="报备状态" name="exceptionStatus"><Select allowClear placeholder="全部状态" options={[{ value: 'SUBMITTED', label: '待处理' }, { value: 'HANDLED', label: '已处理' }]} /></Form.Item>
          <Alert type="info" message="按报备时间筛选；教师仅本人报备，机构管理员仅授权有效班级。姓名脱敏，不包含异常正文和处理意见。" />
        </> : null}
        {exportType === 'REWARD_EXCHANGE_LEDGER' ? <>
          <Form.Item label="兑换状态" name="rewardExchangeStatus"><Select allowClear placeholder="全部状态" options={Object.entries({ PENDING_APPROVAL: '待审批', PENDING_VERIFICATION: '待核销', REJECTED: '已驳回', AUTO_REJECTED: '超时驳回', EXPIRED: '已过期', VERIFIED: '已核销' }).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Alert type="info" message="仅可导出当前主家长名下学生的兑换记录，按申请时间筛选；奖励和所需积分使用申请时的记录。" />
        </> : null}
        {exportType === 'DICTIONARY_LEDGER' ? <>
          <Form.Item label="字典类型编码" name="dictionaryTypeCode" rules={[{ max: 64, message: '编码不能超过 64 个字符' }]}>
            <Input maxLength={64} placeholder="留空导出全部类型" />
          </Form.Item>
          <Form.Item label="字典项状态" name="dictionaryStatus">
            <Select allowClear placeholder="全部状态" options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} />
          </Form.Item>
          <Alert type="info" message="时间范围按字典项更新时间筛选；不选状态时包含停用记录。" />
        </> : null}
        {exportType === 'TEMPLATE_LEDGER' ? <>
          <Form.Item label="模板类型" name="templateType">
            <Select allowClear placeholder="全部类型" options={[{ value: 'IMPORT', label: '导入' }, { value: 'EXPORT', label: '导出' }]} />
          </Form.Item>
          <Form.Item label="适用模块编码" name="templateModuleCode" rules={[{ max: 64, message: '编码不能超过 64 个字符' }]}>
            <Input maxLength={64} placeholder="留空导出全部模块" />
          </Form.Item>
          <Form.Item label="模板状态" name="templateStatus">
            <Select allowClear placeholder="全部状态" options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} />
          </Form.Item>
          <Alert type="info" message="时间范围按模板更新时间筛选；不选状态时包含停用记录。" />
        </> : null}
        {exportType === 'INTERFACE_SERVICE_LEDGER' ? <>
          <Form.Item label="调用方" name="interfaceCallerName" rules={[{ max: 100, message: '调用方不能超过 100 个字符' }]}>
            <Input maxLength={100} placeholder="按名称包含匹配，留空导出全部调用方" />
          </Form.Item>
          <Form.Item label="接口状态" name="interfaceStatus">
            <Select allowClear placeholder="全部状态" options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} />
          </Form.Item>
          <Form.Item label="责任人标识" name="interfaceOwnerId" rules={[{ pattern: /^[1-9][0-9]{18}$/, message: '请输入有效的 19 位责任人标识' }]}>
            <Input maxLength={19} placeholder="与接口服务管理页的责任人标识一致" />
          </Form.Item>
          <Alert type="info" message="时间范围按接口服务更新时间筛选；不选状态时包含停用记录。台账不包含密钥、接口地址或调用报文。" />
        </> : null}
        {exportType === 'CACHE_OPERATION_LOG' ? <>
          <Form.Item label="缓存域" name="cacheDomain"><Select allowClear placeholder="全部模块" options={Object.entries({ PERMISSION: '权限', DICTIONARY: '数据字典', ORGANIZATION: '组织', FEATURE_TOGGLE: '功能开关', USER_SESSION: '用户会话', BUSINESS_STATISTICS: '业务统计', ALL: '全部已注册缓存' }).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item label="操作状态" name="cacheStatus"><Select allowClear placeholder="全部状态" options={Object.entries({ PENDING: '待执行', SUCCEEDED: '成功', FAILED: '失败', REJECTED: '已驳回' }).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Alert type="info" message="按申请时间闭区间筛选；未执行记录的执行人留空，用户会话清除表示强制退出。导出不执行缓存操作。" />
        </> : null}
        {exportType === 'SYSTEM_TASK_LEDGER' ? <>
          <Form.Item label="任务类型" name="systemTaskType"><Select allowClear placeholder="全部可见类型" options={(options.systemTaskTypes ?? []).map(value => ({ value, label: ({ GLOBAL_FEATURE_TOGGLE: '全局功能开关', ORGANIZATION_DISABLE: '组织停用', ORGANIZATION_MOVE: '组织迁移', ORGANIZATION_DELETE: '组织删除', CACHE_CLEAR: '缓存清除', INTERFACE_SERVICE_CHANGE: '接口服务变更', SENSITIVE_DATA_EXPORT: '敏感导出' } as Record<string,string>)[value] ?? value }))} /></Form.Item>
          <Form.Item label="任务状态" name="systemTaskStatus"><Select allowClear placeholder="全部可见状态" options={Object.entries({ DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已批准', REJECTED: '已驳回', EFFECTIVE: '已生效' }).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Alert type="info" message="按任务创建时间闭区间筛选；管理员仅本人任务，审核员仅已提交且有领域权限的任务。角色或领域权限变化后需重新申请导出。" />
        </> : null}
        <div className="export-time-grid">
          <Form.Item label={exportType === 'ATTACHMENT_LEDGER' ? '创建开始时间' : '开始时间'} name="startedAt"><Input type="datetime-local" /></Form.Item>
          <Form.Item label={exportType === 'ATTACHMENT_LEDGER' ? '创建结束时间' : '结束时间'} name="endedAt"><Input type="datetime-local" /></Form.Item>
        </div>
        {exportType === 'IAM_CHANGE_AUDIT' ? <Form.Item label="事件类型" name="eventType">
          <Select allowClear options={eventTypeOptions} />
        </Form.Item> : null}
        <Form.Item label="导出列" name="columns" rules={[{ required: true, message: '请至少选择一列' }]}>
          <Checkbox.Group className="export-column-grid" options={options.columns.map(column => ({ value: column.code, label: column.header }))} />
        </Form.Item>
        <Form.Item label="申请原因" name="reason" rules={[
          { required: true, whitespace: true, message: '请填写申请原因' },
          { max: 500, message: '申请原因不能超过 500 个字符' }
        ]}><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
      </> : null}
    </Form>
  </Modal>;
}

function normalizeDateTime(value?: string): string | undefined {
  return value ? `${value}:00` : undefined;
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '导出选项加载失败';
}
