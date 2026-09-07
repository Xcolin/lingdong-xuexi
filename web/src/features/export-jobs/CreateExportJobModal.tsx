import { Alert, Button, Checkbox, Form, Input, Modal, Select, Spin, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { exportJobApi, type CreateExportJobInput, type ExportJobOptions, type ExportJobType } from '../../api/export-jobs';

interface CreateExportJobModalProps {
  open: boolean;
  canCreateOrdinary: boolean;
  canSubmitSensitive: boolean;
  defaultExportType?: ExportJobType;
  defaultStudentId?: string;
  onCancel: () => void;
  onCreated: () => void;
}

interface FormValues {
  exportType: ExportJobType;
  studentId?: string;
  startedAt?: string;
  endedAt?: string;
  eventType?: string;
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
  const allowedTypes = useMemo(() => [
    ...(props.canCreateOrdinary ? [{ value: 'GROWTH_POINT_LEDGER' as const, label: '积分台账' }] : []),
    ...(props.canSubmitSensitive ? [{ value: 'IAM_CHANGE_AUDIT' as const, label: '权限变更审计' }] : [])
  ], [props.canCreateOrdinary, props.canSubmitSensitive]);

  useEffect(() => {
    if (!props.open || allowedTypes.length === 0) return;
    const type = props.defaultExportType ?? allowedTypes[0].value;
    form.resetFields();
    form.setFieldsValue({ exportType: type });
    void loadOptions(type);
  }, [props.open, props.defaultExportType, props.defaultStudentId, allowedTypes, form]);

  async function loadOptions(type: ExportJobType) {
    setLoading(true);
    setError(undefined);
    setOptions(undefined);
    try {
      const loaded = await exportJobApi.options(type);
      setOptions(loaded);
      form.setFieldsValue({
        studentId: type === 'GROWTH_POINT_LEDGER' ? props.defaultStudentId : undefined,
        columns: loaded.columns.filter(column => column.defaultSelected).map(column => column.code)
      });
    } catch (cause) {
      setError(toMessage(cause));
    } finally {
      setLoading(false);
    }
  }

  async function submit(values: FormValues) {
    setSubmitting(true);
    setError(undefined);
    try {
      const input: CreateExportJobInput = {
        exportType: values.exportType,
        studentId: values.exportType === 'GROWTH_POINT_LEDGER' ? values.studentId : undefined,
        startedAt: normalizeDateTime(values.startedAt),
        endedAt: normalizeDateTime(values.endedAt),
        eventType: values.exportType === 'IAM_CHANGE_AUDIT' ? values.eventType : undefined,
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
    okButtonProps={{ disabled: loading || !options }}
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
        {exportType === 'GROWTH_POINT_LEDGER' ? <Form.Item
          label="学生" name="studentId" rules={[{ required: true, message: '请选择学生' }]}
        ><Select options={options.students.map(student => ({ value: student.id, label: student.name }))} /></Form.Item> : null}
        <div className="export-time-grid">
          <Form.Item label="开始时间" name="startedAt"><Input type="datetime-local" /></Form.Item>
          <Form.Item label="结束时间" name="endedAt"><Input type="datetime-local" /></Form.Item>
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
