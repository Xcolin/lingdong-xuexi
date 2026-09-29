import { useEffect, useState } from 'react';
import { Button, Drawer, Form, Select, Spin, message } from 'antd';
import { classAssignmentApi } from './classAssignmentApi';
import type { OrganizationOption, TeacherOption } from '../learning-tasks/types';

interface TeacherClassAssignmentDrawerProps {
  open: boolean;
  onClose: () => void;
}

interface AssignmentValues {
  classOrganizationId: string;
  teacherUserId: string;
}

/** 机构管理员绑定或解除教师班级关系的最小入口。 */
export function TeacherClassAssignmentDrawer({ open, onClose }: TeacherClassAssignmentDrawerProps) {
  const [form] = Form.useForm<AssignmentValues>();
  const [classes, setClasses] = useState<OrganizationOption[]>([]);
  const [teachers, setTeachers] = useState<TeacherOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setLoading(true);
    void classAssignmentApi.listClasses()
      .then(setClasses)
      .catch((error: unknown) => message.error(toMessage(error)))
      .finally(() => setLoading(false));
  }, [open]);

  async function classChanged(classId: string): Promise<void> {
    form.setFieldValue('teacherUserId', undefined);
    setLoading(true);
    try {
      setTeachers(await classAssignmentApi.listTeachers(classId));
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function update(values: AssignmentValues, active: boolean): Promise<void> {
    setSubmitting(true);
    try {
      if (active) {
        await classAssignmentApi.assignTeacher(values.teacherUserId, values.classOrganizationId);
        message.success('教师班级已绑定');
      } else {
        await classAssignmentApi.removeTeacher(values.teacherUserId, values.classOrganizationId);
        message.success('教师班级已解除');
      }
      form.resetFields();
      onClose();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer title="配置教师班级" open={open} onClose={onClose} width={520} destroyOnClose>
      <Spin spinning={loading}>
        <Form form={form} layout="vertical">
          <Form.Item label="班级" name="classOrganizationId" rules={[{ required: true, message: '请选择班级' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              onChange={(value: string) => void classChanged(value)}
              options={classes.map((item) => ({ value: item.id, label: item.name }))}
            />
          </Form.Item>
          <Form.Item label="教师" name="teacherUserId" rules={[{ required: true, message: '请选择教师' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={teachers.map((teacher) => ({ value: teacher.userId, label: teacher.displayName }))}
            />
          </Form.Item>
          <div className="form-actions">
            <Button onClick={onClose}>取消</Button>
            <Button danger loading={submitting} onClick={() => void form.validateFields().then((values) => update(values, false))}>解除绑定</Button>
            <Button type="primary" loading={submitting} onClick={() => void form.validateFields().then((values) => update(values, true))}>确认绑定</Button>
          </div>
        </Form>
      </Spin>
    </Drawer>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
