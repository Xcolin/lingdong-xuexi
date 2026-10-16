import { Button as PlainButton } from 'antd';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useEffect, useState } from 'react';
import { Drawer, Form, Select, Spin, message } from 'antd';
import { classAssignmentApi } from './classAssignmentApi';
import {teacherOptionLabel} from '../learning-tasks/teacherOptionLabel';
import type { OrganizationOption, TeacherOption } from '../learning-tasks/types';

interface TeacherClassAssignmentDrawerProps {
  open: boolean;
  onClose: () => void;
  fixedClassId?: string;
  fixedClassName?: string;
  schoolId?: string;
  onSaved?: () => void;
}

interface AssignmentValues {
  schoolOrganizationId?:string;
  classOrganizationId: string;
  teacherUserId: string;
}

/** 机构管理员绑定或解除教师班级关系的最小入口。 */
export function TeacherClassAssignmentDrawer({ open, onClose, fixedClassId, fixedClassName, schoolId, onSaved }: TeacherClassAssignmentDrawerProps) {
  const [form] = Form.useForm<AssignmentValues>();
  const selectedSchoolId=Form.useWatch('schoolOrganizationId',form)??schoolId;
  const [classes, setClasses] = useState<OrganizationOption[]>([]);
  const [teachers, setTeachers] = useState<TeacherOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    form.resetFields();
    form.setFieldsValue({classOrganizationId:fixedClassId,schoolOrganizationId:schoolId});setTeachers([]);setClasses([]);
    setLoading(true);
    void (fixedClassId ? Promise.resolve([{ id: fixedClassId, name: fixedClassName ?? '当前班级', organizationType: 'CLASS', parentId: null, organizationPath: '' }]) : classAssignmentApi.listClasses(schoolId,'TEACHER_CLASS_ASSIGN'))
      .then(async (items) => { setClasses(items); if (fixedClassId) setTeachers(await classAssignmentApi.listTeachers(fixedClassId)); })
      .catch((error: unknown) => message.error(toMessage(error)))
      .finally(() => setLoading(false));
  }, [open, fixedClassId, fixedClassName, schoolId]);

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
      onSaved?.();
      onClose();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer title="配置教师班级" open={open} onClose={onClose} width="min(520px, 92vw)" destroyOnClose>
      <Spin spinning={loading}>
        <Form form={form} layout="vertical">
          {!fixedClassId&&!schoolId&&<Form.Item label="学校" name="schoolOrganizationId" rules={[{required:true,message:'请选择学校'}]}><Select onChange={()=>{form.setFieldsValue({classOrganizationId:undefined,teacherUserId:undefined});setTeachers([]);}} options={Array.from(new Map(classes.filter(item=>item.schoolOrganizationId).map(item=>[item.schoolOrganizationId!,{value:item.schoolOrganizationId!,label:item.schoolName??item.schoolOrganizationId!}])).values())}/></Form.Item>}
          <Form.Item label="班级" name="classOrganizationId" rules={[{ required: true, message: '请选择班级' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!!fixedClassId||!selectedSchoolId}
              onChange={(value: string) => void classChanged(value)}
              options={classes.filter(item=>!!fixedClassId||item.schoolOrganizationId===selectedSchoolId).map((item) => ({ value: item.id, label: item.name }))}
            />
          </Form.Item>
          <Form.Item label="教师" name="teacherUserId" rules={[{ required: true, message: '请选择教师' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={teachers.map((teacher) => ({ value: teacher.userId, label: teacherOptionLabel(teacher) }))}
            />
          </Form.Item>
          <div className="form-actions">
            <PlainButton  onClick={onClose}>取消</PlainButton>
            <Button actionKey="TEACHER_CLASS_ASSIGN" danger loading={submitting} onClick={() => void form.validateFields().then((values) => update(values, false))}>解除绑定</Button>
            <Button actionKey="TEACHER_CLASS_ASSIGN" type="primary" loading={submitting} onClick={() => void form.validateFields().then((values) => update(values, true))}>确认绑定</Button>
          </div>
        </Form>
      </Spin>
    </Drawer>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
