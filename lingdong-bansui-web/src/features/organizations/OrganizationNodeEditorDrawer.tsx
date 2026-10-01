import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useEffect, useState } from 'react';
import { Drawer, Form, Input, InputNumber, message } from 'antd';
import { Save } from 'lucide-react';
import { organizationApi, type OrganizationNode, type UpdateOrganizationInput } from '../../api/organization';

interface OrganizationNodeEditorDrawerProps {
  open: boolean;
  node: OrganizationNode | null;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
}

/** 只允许编辑不改变数据边界的组织名称与排序字段。 */
export function OrganizationNodeEditorDrawer({ open, node, onClose, onSaved }: OrganizationNodeEditorDrawerProps) {
  const [form] = Form.useForm<UpdateOrganizationInput>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open && node) {
      form.setFieldsValue({ name: node.name, sortOrder: node.sortOrder, versionNo: node.versionNo });
    }
  }, [form, node, open]);

  async function submit(values: UpdateOrganizationInput): Promise<void> {
    if (!node) return;
    setSubmitting(true);
    try {
      await organizationApi.updateOrganization(node.id, {
        name: values.name.trim(), sortOrder: values.sortOrder, versionNo: node.versionNo
      });
      message.success('组织节点已更新');
      onClose();
      await onSaved();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer title="编辑组织节点" open={open} onClose={onClose} width={460} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={submit}>
        <Form.Item label="组织编码"><Input value={node?.code} disabled /></Form.Item>
        <Form.Item label="组织类型"><Input value={node?.typeCode} disabled /></Form.Item>
        <Form.Item name="name" label="组织名称" rules={[{ required: true, message: '请输入组织名称' }, { max: 100, message: '组织名称不能超过 100 个字符' }]}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item name="sortOrder" label="排序" rules={[{ required: true, message: '请输入排序值' }]}>
          <InputNumber min={0} precision={0} className="full-width" />
        </Form.Item>
        <div className="form-actions">
          <Button actionKey="organizations.organization-node-editor-drawer.1" onClick={onClose}>取消</Button>
          <Button actionKey="organizations.organization-node-editor-drawer.2" type="primary" htmlType="submit" loading={submitting} icon={<Save size={16} />}>保存</Button>
        </div>
      </Form>
    </Drawer>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
