import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useEffect, useMemo, useState } from 'react';
import { Drawer, Form, Input, Segmented, Select, Spin, message } from 'antd';
import {
  classAssignmentApi,
  type StudentOrganizationClassOption,
  type StudentOrganizationRelationshipSummary
} from './classAssignmentApi';

interface StudentOrganizationLifecycleDrawerProps {
  open: boolean;
  onClose: () => void;
}

interface RelationshipValues {
  studentId: string;
  classOrganizationId?: string;
  reason: string;
}

type OperationMode = 'TRANSFER' | 'DEACTIVATE';

/** 机构管理员维护学员校内转班和转出关系。 */
export function StudentOrganizationLifecycleDrawer({
  open,
  onClose
}: StudentOrganizationLifecycleDrawerProps) {
  const [form] = Form.useForm<RelationshipValues>();
  const [mode, setMode] = useState<OperationMode>('TRANSFER');
  const [students, setStudents] = useState<StudentOrganizationRelationshipSummary[]>([]);
  const [classes, setClasses] = useState<StudentOrganizationClassOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const selectedStudentId = Form.useWatch('studentId', form);
  const selectedStudent = useMemo(
    () => students.find((student) => student.studentId === selectedStudentId),
    [selectedStudentId, students]
  );

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    void Promise.all([
      classAssignmentApi.listRelationships(),
      classAssignmentApi.listRelationshipClasses()
    ])
      .then(([relationships, loadedClasses]) => {
        setStudents(relationships);
        setClasses(loadedClasses);
      })
      .catch((error: unknown) => message.error(toMessage(error)))
      .finally(() => setLoading(false));
  }, [open]);

  async function submit(values: RelationshipValues): Promise<void> {
    if (mode === 'DEACTIVATE') {
      confirmDeactivation(values);
      return;
    }
    if (!values.classOrganizationId) return;
    setSubmitting(true);
    try {
      await classAssignmentApi.transferStudent(
        values.studentId, values.classOrganizationId, values.reason
      );
      message.success('学生班级关系已更新');
      await reloadAndReset();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  function confirmDeactivation(values: RelationshipValues): void {
    if (!selectedStudent) return;
    Modal.confirm({ actionPrefix: 'organizations.student-organization-lifecycle-drawer.confirm.1',
      title: '确认转出该学生？',
      content: '转出后机构和班级活动关系立即停用，历史数据继续保留。',
      okText: '确认转出',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setSubmitting(true);
        try {
          await classAssignmentApi.deactivateStudent(
            values.studentId,
            selectedStudent.enrollmentOrganizationId,
            values.reason
          );
          message.success('学生机构关系已停用');
          await reloadAndReset();
        } catch (error) {
          message.error(toMessage(error));
          throw error;
        } finally {
          setSubmitting(false);
        }
      }
    });
  }

  async function reloadAndReset(): Promise<void> {
    setStudents(await classAssignmentApi.listRelationships());
    form.resetFields();
  }

  function changeMode(value: string | number): void {
    setMode(value as OperationMode);
    form.setFieldValue('classOrganizationId', undefined);
  }

  return (
    <Drawer title="学员机构关系" open={open} onClose={onClose} width={560} destroyOnClose>
      <Spin spinning={loading}>
        <Segmented
          block
          value={mode}
          onChange={changeMode}
          options={[
            { label: '校内转班', value: 'TRANSFER' },
            { label: '离校或转学转出', value: 'DEACTIVATE' }
          ]}
        />
        <Form form={form} layout="vertical" onFinish={submit} className="section-form">
          <Form.Item label="学生" name="studentId" rules={[{ required: true, message: '请选择学生' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={students.map((student) => ({
                value: student.studentId,
                label: `${student.studentName}${student.currentClassOrganizationName
                  ? ` · 当前 ${student.currentClassOrganizationName}` : ' · 未分班'}`
              }))}
            />
          </Form.Item>
          {mode === 'TRANSFER' && (
            <Form.Item
              label="目标班级"
              name="classOrganizationId"
              rules={[{ required: true, message: '请选择目标班级' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={classes.map((item) => ({ value: item.id, label: item.name }))}
              />
            </Form.Item>
          )}
          <Form.Item
            label="变更原因"
            name="reason"
            rules={[
              { required: true, whitespace: true, message: '请输入变更原因' },
              { max: 200, message: '变更原因不能超过 200 个字符' }
            ]}
          >
            <Input.TextArea rows={3} maxLength={200} showCount />
          </Form.Item>
          <div className="form-actions">
            <Button actionKey="organizations.student-organization-lifecycle-drawer.1" onClick={onClose}>取消</Button>
            <Button actionKey="organizations.student-organization-lifecycle-drawer.2"
              type="primary"
              danger={mode === 'DEACTIVATE'}
              htmlType="submit"
              loading={submitting}
            >
              {mode === 'TRANSFER' ? '确认转班' : '确认转出'}
            </Button>
          </div>
        </Form>
      </Spin>
    </Drawer>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
