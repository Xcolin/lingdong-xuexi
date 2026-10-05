import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useEffect, useState } from 'react';
import { Drawer, Form, Input, InputNumber, Select, message } from 'antd';
import { Save } from 'lucide-react';
import { organizationApi, type OrganizationNode, type UpdateOrganizationInput } from '../../api/organization';

interface OrganizationNodeEditorDrawerProps {
  open: boolean;
  node: OrganizationNode | null;
  divisionOptions: Array<{ value: string; label: string }>;
  /** 选项加载失败时不提交该字段，避免把既有行政区划误清空。 */
  divisionOptionsLoaded: boolean;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
}

/** 编辑组织名称、排序与行政区划挂接；编码与类型为数据边界不可改。 */
export function OrganizationNodeEditorDrawer({ open, node, divisionOptions, divisionOptionsLoaded, onClose, onSaved }: OrganizationNodeEditorDrawerProps) {
  const [form] = Form.useForm<UpdateOrganizationInput>();
  const [submitting, setSubmitting] = useState(false);

  // destroyOnClose 下抽屉内容随动画挂载，打开动画结束后再回填，避免表单字段尚未挂载导致回显丢失。
  useEffect(() => {
    if (open && node) {
      form.setFieldsValue({
        name: node.name, sortOrder: node.sortOrder, versionNo: node.versionNo,
        adminDivisionCode: node.adminDivisionCode ?? undefined
      });
    }
  }, [form, node, open]);

  function afterOpenChange(visible: boolean): void {
    if (visible && node) {
      form.setFieldsValue({
        name: node.name, sortOrder: node.sortOrder, versionNo: node.versionNo,
        adminDivisionCode: node.adminDivisionCode ?? undefined
      });
    }
  }

  async function submit(values: UpdateOrganizationInput): Promise<void> {
    if (!node) return;
    setSubmitting(true);
    try {
      const payload: UpdateOrganizationInput = {
        name: values.name.trim(), sortOrder: values.sortOrder, versionNo: node.versionNo
      };
      if (divisionOptionsLoaded) {
        // 空串显式清除挂接；未加载选项时不携带该字段（后端按 null 保持既有值）。
        payload.adminDivisionCode = values.adminDivisionCode ?? '';
      }
      await organizationApi.updateOrganization(node.id, payload);
      message.success('组织节点已更新');
      onClose();
      await onSaved();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  // 已挂接但字典中已停用的编码仍需回显，追加为兜底选项。
  const options = divisionOptions.some((item) => item.value === node?.adminDivisionCode)
    ? divisionOptions
    : node?.adminDivisionCode
      ? [...divisionOptions, { value: node.adminDivisionCode, label: `${node.adminDivisionCode}（已停用）` }]
      : divisionOptions;

  return (
    <Drawer title="编辑组织节点" open={open} onClose={onClose} width="min(460px, 92vw)" destroyOnClose afterOpenChange={afterOpenChange}>
      <Form form={form} layout="vertical" onFinish={submit}>
        <Form.Item label="组织编码"><Input value={node?.code} disabled /></Form.Item>
        <Form.Item label="组织类型"><Input value={node?.typeCode} disabled /></Form.Item>
        <Form.Item name="name" label="组织名称" rules={[{ required: true, message: '请输入组织名称' }, { max: 100, message: '组织名称不能超过 100 个字符' }]}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item name="adminDivisionCode" label="行政区划" extra="选项来自数据字典的行政区划，清除后保存即取消挂接">
          <Select allowClear showSearch optionFilterProp="label" placeholder="可选择该组织对应的行政区划" options={options} />
        </Form.Item>
        <Form.Item name="sortOrder" label="排序" rules={[{ required: true, message: '请输入排序值' }]}>
          <InputNumber min={0} precision={0} className="full-width" />
        </Form.Item>
        <div className="form-actions">
          <Button actionKey="organizations.organization-node-editor-drawer.1" onClick={onClose}>取消</Button>
          <Button actionKey="ORG_NODE_UPDATE" type="primary" htmlType="submit" loading={submitting} icon={<Save size={16} />}>保存</Button>
        </div>
      </Form>
    </Drawer>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
