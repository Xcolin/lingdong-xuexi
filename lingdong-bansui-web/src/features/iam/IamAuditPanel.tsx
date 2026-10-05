import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useState } from 'react';
import { Form, Input, Select, Space, Tag, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { RotateCcw, Search } from 'lucide-react';
import { formatDateTime as formatTime } from '../../utils/datetime';
import {
  iamApi,
  type IamAuditEventType,
  type IamAuditQuery,
  type IamAuditTargetType,
  type IamChangeAudit
} from '../../api/iam';

type AuditFilter = Omit<IamAuditQuery, 'page' | 'pageSize'>;

const eventLabels: Record<IamAuditEventType, string> = {
  USER_CREATE: '创建用户', USER_STATUS_CHANGE: '用户状态变更',
  USER_ORGANIZATION_ASSOCIATE: '用户关联组织', USER_ROLE_ASSIGN: '用户授予角色',
  ROLE_CREATE: '创建角色', PERMISSION_CREATE: '创建权限',
  ROLE_PERMISSION_CONFIGURE: '配置角色权限', ROLE_PERMISSION_REMOVE: '撤销角色权限',
  USER_PERMISSION_CONFIGURE: '配置用户权限', USER_PERMISSION_REMOVE: '撤销用户权限',
  ROLE_DATA_SCOPE_ADD: '新增角色数据范围', ORGANIZATION_ADMIN_ASSIGN: '配置组织管理员',
  MENU_CREATE: '新增菜单', MENU_UPDATE: '编辑菜单', MENU_REORDER: '菜单排序'
};

const targetLabels: Record<IamAuditTargetType, string> = {
  USER: '用户', ROLE: '角色', PERMISSION: '权限', ROLE_PERMISSION: '角色权限',
  USER_PERMISSION: '用户权限', ROLE_DATA_SCOPE: '角色数据范围',
  ORGANIZATION_ADMIN: '组织管理员', USER_ORGANIZATION: '用户组织关系', USER_ROLE: '用户角色关系', MENU: '菜单'
};

export function IamAuditPanel() {
  const [form] = Form.useForm<AuditFilter>();
  const [items, setItems] = useState<IamChangeAudit[]>([]);
  const [query, setQuery] = useState<IamAuditQuery>({ page: 1, pageSize: 10 });
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => { void load(query); }, [query]);

  async function load(nextQuery: IamAuditQuery): Promise<void> {
    setLoading(true);
    try {
      const page = await iamApi.listAudits(nextQuery);
      setItems(page.items);
      setTotal(page.total);
    } catch (error) {
      setItems([]);
      setTotal(0);
      message.error(error instanceof Error ? error.message : '审计日志查询失败');
    } finally {
      setLoading(false);
    }
  }

  function submit(values: AuditFilter): void {
    setQuery({ ...normalized(values), page: 1, pageSize: query.pageSize });
  }

  function reset(): void {
    form.resetFields();
    setQuery({ page: 1, pageSize: 10 });
  }

  return (
    <ProCard className="content-panel" title="权限变更日志" bordered={false}>
      <Form form={form} layout="inline" onFinish={submit}>
        <Form.Item label="审计事件" name="eventType">
          <Select allowClear style={{ width: 180 }} options={Object.entries(eventLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
        <Form.Item label="对象类型" name="targetType">
          <Select allowClear style={{ width: 160 }} options={Object.entries(targetLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
        <Form.Item label="操作者标识" name="operatorId"><Input style={{ width: 190 }} inputMode="numeric" /></Form.Item>
        <Form.Item label="目标标识" name="targetId"><Input style={{ width: 190 }} inputMode="numeric" /></Form.Item>
        <Form.Item label="开始时间" name="startedAt"><Input type="datetime-local" /></Form.Item>
        <Form.Item label="结束时间" name="endedAt"><Input type="datetime-local" /></Form.Item>
        <Form.Item>
          <Space>
            <Button actionKey="IAM_AUDIT_READ" type="primary" htmlType="submit" icon={<Search size={16} />}>查询审计日志</Button>
            <Button actionKey="iam.iam-audit-panel.2" icon={<RotateCcw size={16} />} onClick={reset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
      <Table<IamChangeAudit>
        rowKey="id"
        loading={loading}
        dataSource={items}
        scroll={{ x: 1120 }}
        locale={{ emptyText: '暂无权限变更日志' }}
        pagination={{
          current: query.page, pageSize: query.pageSize, total, showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          onChange: (page, pageSize) => setQuery((current) => ({ ...current, page, pageSize }))
        }}
        columns={[
          { title: '发生时间', dataIndex: 'occurredAt', key: 'occurredAt', width: 180, render: formatTime },
          { title: '事件', dataIndex: 'eventType', key: 'eventType', width: 170, render: (value: IamAuditEventType) => eventLabels[value] },
          { title: '对象', dataIndex: 'targetType', key: 'targetType', width: 130, render: (value: IamAuditTargetType) => <Tag>{targetLabels[value]}</Tag> },
          { title: '目标标识', dataIndex: 'targetId', key: 'targetId', width: 180 },
          { title: '关联标识', dataIndex: 'relatedId', key: 'relatedId', width: 180, render: emptyText },
          { title: '组织标识', dataIndex: 'organizationId', key: 'organizationId', width: 180, render: emptyText },
          { title: '变更前', dataIndex: 'beforeValue', key: 'beforeValue', width: 120, render: emptyText },
          { title: '变更后', dataIndex: 'afterValue', key: 'afterValue', width: 150, render: emptyText },
          { title: '操作者标识', dataIndex: 'operatorId', key: 'operatorId', width: 180, render: emptyText }
        ]}
      />
    </ProCard>
  );
}

function normalized(values: AuditFilter): AuditFilter {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined && value !== '')) as AuditFilter;
}

function emptyText(value: string | null): string {
  return value ?? '-';
}
