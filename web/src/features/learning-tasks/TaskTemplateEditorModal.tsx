import { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Segmented, Select, message } from 'antd';
import { taskTemplateApi } from './taskTemplateApi';
import type { LearningTaskTemplate, LearningTaskTemplateInput } from './types';

interface TaskTemplateEditorModalProps {
  open: boolean;
  template: LearningTaskTemplate | null;
  initialValues?: Partial<LearningTaskTemplateInput>;
  onClose: () => void;
  onSaved: () => void | Promise<void>;
}

export function TaskTemplateEditorModal({
  open,
  template,
  initialValues,
  onClose,
  onSaved
}: TaskTemplateEditorModalProps) {
  const [form] = Form.useForm<LearningTaskTemplateInput>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(template ? toInput(template) : {
      templateName: '',
      taskTitle: '',
      difficultyLevel: 1,
      durationMinutes: 30,
      categoryCode: 'GENERAL',
      tagCodes: ['DAILY'],
      remark: '',
      ...initialValues
    });
  }, [form, initialValues, open, template]);

  async function submit(values: LearningTaskTemplateInput): Promise<void> {
    setSubmitting(true);
    const input: LearningTaskTemplateInput = {
      ...values,
      templateName: values.templateName.trim(),
      taskTitle: values.taskTitle.trim(),
      categoryCode: values.categoryCode?.trim() || undefined,
      tagCodes: values.tagCodes ?? [],
      remark: values.remark?.trim() || undefined
    };
    try {
      if (template) {
        await taskTemplateApi.update(template.id, template.versionNo, input);
        message.success('个人模板已更新');
      } else {
        await taskTemplateApi.create(input);
        message.success('个人模板已创建');
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
    <Modal
      title={template ? '编辑个人模板' : '新建个人模板'}
      open={open}
      onCancel={onClose}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onClose}>取消</Button>,
        <Button
          key="save"
          type="primary"
          loading={submitting}
          onClick={() => form.submit()}
        >保存</Button>
      ]}
    >
      <Form
        name="personalTaskTemplate"
        form={form}
        layout="vertical"
        onFinish={submit}
        disabled={submitting}
      >
        <Form.Item label="模板名称" name="templateName" rules={[
          { required: true, message: '请输入模板名称' },
          { max: 50, message: '模板名称不能超过 50 个字符' }
        ]}>
          <Input />
        </Form.Item>
        <Form.Item label="任务标题" name="taskTitle" rules={[
          { required: true, message: '请输入任务标题' },
          { max: 50, message: '任务标题不能超过 50 个字符' }
        ]}>
          <Input />
        </Form.Item>
        <div className="task-editor-grid">
          <Form.Item label="难度" name="difficultyLevel" rules={[{ required: true }]}>
            <Segmented block options={[
              { value: 1, label: '一级' },
              { value: 2, label: '二级' },
              { value: 3, label: '三级' }
            ]} />
          </Form.Item>
          <Form.Item label="执行时长（分钟）" name="durationMinutes" rules={[{ required: true }]}>
            <InputNumber min={1} max={1440} precision={0} className="full-width" />
          </Form.Item>
          <Form.Item label="任务分类" name="categoryCode">
            <Input maxLength={64} />
          </Form.Item>
        </div>
        <Form.Item label="任务标签" name="tagCodes">
          <Select mode="tags" maxCount={20} tokenSeparators={[',']} />
        </Form.Item>
        <Form.Item label="任务备注" name="remark">
          <Input.TextArea rows={3} maxLength={200} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function toInput(template: LearningTaskTemplate): LearningTaskTemplateInput {
  return {
    templateName: template.templateName,
    taskTitle: template.taskTitle,
    difficultyLevel: template.difficultyLevel,
    durationMinutes: template.durationMinutes,
    categoryCode: template.categoryCode ?? undefined,
    tagCodes: template.tagCodes,
    remark: template.remark ?? undefined
  };
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
