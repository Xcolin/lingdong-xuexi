import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Segmented,
  Select,
  Space,
  Spin,
  message
} from 'antd';
import { Plus, Trash2 } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import { learningTaskApi } from './api';
import type {
  LearningTaskDetails,
  LearningTaskInput,
  LearningTaskSourceType,
  LearningTaskTargetInput,
  LearningTaskTargetType,
  OrganizationOption,
  StudentOption,
  TeacherOption
} from './types';

interface LearningTaskEditorDrawerProps {
  open: boolean;
  currentUser: CurrentUser;
  initialTask: LearningTaskDetails | null;
  onClose: () => void;
  onSaved: () => void | Promise<void>;
}

interface EditorValues extends Omit<LearningTaskInput, 'targets'> {
  targets: LearningTaskTargetInput[];
}

const sourceLabels: Record<LearningTaskSourceType, string> = {
  FAMILY: '家庭任务',
  ORGANIZATION: '机构任务',
  TEACHER: '教师任务'
};

export function LearningTaskEditorDrawer({
  open,
  currentUser,
  initialTask,
  onClose,
  onSaved
}: LearningTaskEditorDrawerProps) {
  const [form] = Form.useForm<EditorValues>();
  const [organizations, setOrganizations] = useState<OrganizationOption[]>([]);
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [teachers, setTeachers] = useState<TeacherOption[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const allowedSources = useMemo(() => sourceOptions(currentUser), [currentUser]);
  const sourceType = Form.useWatch('sourceType', form);

  useEffect(() => {
    if (!open) {
      return;
    }
    const defaultSource = initialTask?.sourceType ?? allowedSources[0]?.value;
    form.setFieldsValue(initialTask ? toEditorValues(initialTask) : {
      sourceType: defaultSource,
      title: '',
      difficultyLevel: 1,
      durationMinutes: 30,
      scheduledDate: '',
      categoryCode: 'GENERAL',
      tagCodes: ['DAILY'],
      remark: '',
      targets: [{ targetType: defaultTargetType(defaultSource), targetId: '' }]
    });
    if (defaultSource) {
      void loadOptions(defaultSource, initialTask?.sourceOrganizationId ?? undefined);
    }
  }, [allowedSources, form, initialTask, open]);

  async function loadOptions(
    nextSource: LearningTaskSourceType,
    sourceOrganizationId?: string
  ): Promise<void> {
    setOptionsLoading(true);
    try {
      const loadedOrganizations = nextSource === 'FAMILY'
        ? []
        : await learningTaskApi.listOrganizations(
          nextSource, nextSource === 'TEACHER' ? 'CLASS' : undefined
        );
      const [loadedStudents, loadedTeachers] = await Promise.all([
        nextSource === 'FAMILY' || sourceOrganizationId
          ? learningTaskApi.listStudents(nextSource, sourceOrganizationId)
          : Promise.resolve([]),
        nextSource === 'ORGANIZATION'
          ? learningTaskApi.listTeachers()
          : Promise.resolve([])
      ]);
      setOrganizations(loadedOrganizations);
      setStudents(loadedStudents);
      setTeachers(loadedTeachers);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setOptionsLoading(false);
    }
  }

  function valuesChanged(changed: Partial<EditorValues>, values: EditorValues): void {
    if (changed.sourceType) {
      const nextSource = changed.sourceType;
      form.setFieldsValue({
        sourceOrganizationId: undefined,
        reviewerUserId: undefined,
        targets: [{ targetType: defaultTargetType(nextSource), targetId: '' }]
      });
      void loadOptions(nextSource);
      return;
    }
    if ('sourceOrganizationId' in changed && values.sourceType) {
      form.setFieldsValue({
        reviewerUserId: undefined,
        targets: [{ targetType: defaultTargetType(values.sourceType), targetId: '' }]
      });
      void loadOptions(values.sourceType, changed.sourceOrganizationId);
    }
  }

  async function submit(values: EditorValues): Promise<void> {
    setSubmitting(true);
    const input: LearningTaskInput = {
      ...values,
      sourceOrganizationId: values.sourceType === 'FAMILY' ? undefined : values.sourceOrganizationId,
      categoryCode: values.categoryCode?.trim() || undefined,
      tagCodes: values.tagCodes ?? [],
      remark: values.remark?.trim() || undefined,
      reviewerUserId: values.reviewerUserId || undefined,
      targets: values.targets.map((target) => ({
        targetType: target.targetType,
        targetId: target.targetId
      }))
    };
    try {
      if (initialTask) {
        await learningTaskApi.update(initialTask.id, input);
        message.success('任务草稿已更新');
      } else {
        await learningTaskApi.create(input);
        message.success('任务草稿已创建');
      }
      await onSaved();
      onClose();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer
      title={initialTask ? '编辑学习任务' : '新建学习任务'}
      open={open}
      onClose={onClose}
      width={720}
      destroyOnClose
      extra={<Button type="primary" loading={submitting} onClick={() => form.submit()}>保存草稿</Button>}
    >
      <Spin spinning={optionsLoading}>
        <Form<EditorValues>
          form={form}
          layout="vertical"
          onValuesChange={valuesChanged}
          onFinish={submit}
          disabled={submitting}
        >
          <Form.Item label="任务来源" name="sourceType" rules={[{ required: true, message: '请选择任务来源' }]}>
            <Segmented
              block
              disabled={Boolean(initialTask) || allowedSources.length === 1}
              options={allowedSources}
            />
          </Form.Item>

          {sourceType !== 'FAMILY' && (
            <Form.Item
              label={sourceType === 'TEACHER' ? '来源班级' : '来源组织'}
              name="sourceOrganizationId"
              rules={[{ required: true, message: '请选择来源组织' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={organizations.map((item) => ({
                  value: item.id,
                  label: `${item.name}（${item.organizationType}）`
                }))}
              />
            </Form.Item>
          )}

          <Form.Item label="任务标题" name="title" rules={[
            { required: true, message: '请输入任务标题' },
            { max: 50, message: '任务标题不能超过 50 个字符' }
          ]}>
            <Input />
          </Form.Item>

          <div className="task-editor-grid">
            <Form.Item label="难度" name="difficultyLevel" rules={[{ required: true }]}>
              <Segmented block options={[
                { value: 1, label: '一级 · 10 分' },
                { value: 2, label: '二级 · 20 分' },
                { value: 3, label: '三级 · 30 分' }
              ]} />
            </Form.Item>
            <Form.Item label="执行时长（分钟）" name="durationMinutes" rules={[{ required: true }]}>
              <InputNumber min={1} max={1440} precision={0} className="full-width" />
            </Form.Item>
            <Form.Item label="计划日期" name="scheduledDate" rules={[{ required: true, message: '请选择计划日期' }]}>
              <Input type="date" />
            </Form.Item>
            <Form.Item label="任务分类" name="categoryCode">
              <Input maxLength={64} />
            </Form.Item>
          </div>

          <Form.Item label="任务标签" name="tagCodes">
            <Select mode="tags" maxCount={20} tokenSeparators={[',']} />
          </Form.Item>

          {sourceType === 'ORGANIZATION' && (
            <Form.Item label="审核教师" name="reviewerUserId">
              <Select
                allowClear
                placeholder="不选择时由当前机构管理员审核"
                options={teachers.map((teacher) => ({
                  value: teacher.userId,
                  label: teacher.displayName
                }))}
              />
            </Form.Item>
          )}

          <Form.List name="targets">
            {(fields, { add, remove }) => (
              <div className="task-target-list">
                <div className="task-target-heading">
                  <span>任务目标</span>
                  <Button
                    type="text"
                    icon={<Plus size={16} />}
                    onClick={() => add({ targetType: defaultTargetType(sourceType), targetId: '' })}
                  >添加目标</Button>
                </div>
                {fields.map((field) => (
                  <Space key={field.key} align="start" className="task-target-row">
                    <Form.Item name={[field.name, 'targetType']} rules={[{ required: true }]}>
                      <Select
                        className="task-target-type"
                        options={targetTypeOptions(sourceType)}
                      />
                    </Form.Item>
                    <Form.Item noStyle shouldUpdate>
                      {() => {
                        const targetType = form.getFieldValue(['targets', field.name, 'targetType']) as LearningTaskTargetType;
                        return (
                          <Form.Item
                            name={[field.name, 'targetId']}
                            rules={[{ required: true, message: '请选择任务目标' }]}
                            className="task-target-value"
                          >
                            <Select
                              showSearch
                              optionFilterProp="label"
                              options={targetOptions(targetType, organizations, students)}
                            />
                          </Form.Item>
                        );
                      }}
                    </Form.Item>
                    <Button
                      type="text"
                      danger
                      aria-label="删除目标"
                      icon={<Trash2 size={16} />}
                      disabled={fields.length === 1}
                      onClick={() => remove(field.name)}
                    />
                  </Space>
                ))}
              </div>
            )}
          </Form.List>

          <Form.Item label="任务备注" name="remark">
            <Input.TextArea rows={3} maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Spin>
    </Drawer>
  );
}

function sourceOptions(currentUser: CurrentUser): Array<{ value: LearningTaskSourceType; label: string }> {
  const roleSources: Array<[string, LearningTaskSourceType]> = [
    ['PARENT', 'FAMILY'],
    ['ORG_ADMIN', 'ORGANIZATION'],
    ['TEACHER', 'TEACHER']
  ];
  return roleSources
    .filter(([role]) => currentUser.roleCodes.includes(role))
    .map(([, source]) => ({ value: source, label: sourceLabels[source] }));
}

function defaultTargetType(sourceType?: LearningTaskSourceType): LearningTaskTargetType {
  return sourceType === 'ORGANIZATION' ? 'ORGANIZATION' : 'STUDENT';
}

function targetTypeOptions(sourceType?: LearningTaskSourceType) {
  if (sourceType === 'FAMILY') {
    return [{ value: 'STUDENT', label: '学生' }];
  }
  return [
    { value: 'ORGANIZATION', label: sourceType === 'TEACHER' ? '当前班级' : '组织/班级' },
    { value: 'STUDENT', label: '学生' }
  ];
}

function targetOptions(
  targetType: LearningTaskTargetType,
  organizations: OrganizationOption[],
  students: StudentOption[]
) {
  return targetType === 'ORGANIZATION'
    ? organizations.map((item) => ({ value: item.id, label: `${item.name}（${item.organizationType}）` }))
    : students.map((item) => ({
      value: item.id,
      label: `${item.studentName}${item.currentClassName ? ` · ${item.currentClassName}` : ''}${item.studentAccountMasked ? ` · ${item.studentAccountMasked}` : ''}`
    }));
}

function toEditorValues(task: LearningTaskDetails): EditorValues {
  return {
    sourceType: task.sourceType,
    sourceOrganizationId: task.sourceOrganizationId ?? undefined,
    title: task.title,
    difficultyLevel: task.difficultyLevel,
    durationMinutes: task.durationMinutes,
    scheduledDate: task.scheduledDate,
    categoryCode: task.categoryCode ?? undefined,
    tagCodes: task.tagCodes,
    remark: task.remark ?? undefined,
    reviewerUserId: task.reviewerUserId,
    targets: task.targets.map((target) => ({
      targetType: target.targetType,
      targetId: target.targetId
    }))
  };
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
