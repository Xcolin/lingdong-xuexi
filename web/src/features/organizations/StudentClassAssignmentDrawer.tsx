import { useEffect, useState } from 'react';
import { Button, Drawer, Form, Select, Spin, message } from 'antd';
import { classAssignmentApi } from './classAssignmentApi';
import type { OrganizationOption, StudentOption } from '../learning-tasks/types';

interface StudentClassAssignmentDrawerProps {
  open: boolean;
  onClose: () => void;
}

interface AssignmentValues {
  studentId: string;
  classOrganizationId: string;
}

/** 机构管理员配置学生唯一活动班级的最小入口。 */
export function StudentClassAssignmentDrawer({ open, onClose }: StudentClassAssignmentDrawerProps) {
  const [form] = Form.useForm<AssignmentValues>();
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [classes, setClasses] = useState<OrganizationOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setLoading(true);
    void Promise.all([classAssignmentApi.listStudents(), classAssignmentApi.listClasses()])
      .then(([loadedStudents, loadedClasses]) => {
        setStudents(loadedStudents);
        setClasses(loadedClasses);
      })
      .catch((error: unknown) => message.error(toMessage(error)))
      .finally(() => setLoading(false));
  }, [open]);

  async function submit(values: AssignmentValues): Promise<void> {
    setSubmitting(true);
    try {
      await classAssignmentApi.assignStudent(values.studentId, values.classOrganizationId);
      message.success('学生当前班级已更新');
      form.resetFields();
      onClose();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer title="配置学生当前班级" open={open} onClose={onClose} width={520} destroyOnClose>
      <Spin spinning={loading}>
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item label="学生" name="studentId" rules={[{ required: true, message: '请选择学生' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={students.map((student) => ({
                value: student.id,
                label: `${student.studentName}${student.currentClassName ? ` · 当前 ${student.currentClassName}` : ''}`
              }))}
            />
          </Form.Item>
          <Form.Item label="班级" name="classOrganizationId" rules={[{ required: true, message: '请选择班级' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={classes.map((item) => ({ value: item.id, label: item.name }))}
            />
          </Form.Item>
          <div className="form-actions">
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" htmlType="submit" loading={submitting}>确认配置</Button>
          </div>
        </Form>
      </Spin>
    </Drawer>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
