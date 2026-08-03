import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Modal, Row, Select, Space, Spin, Table, Tag, Tree, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { Building2, FolderPlus, GraduationCap, Plus, UserRoundCheck } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import { organizationApi, type CreateOrganizationInput, type CreateOrganizationTypeInput, type OrganizationNode, type OrganizationType } from '../../api/organization';
import { StudentClassAssignmentDrawer } from './StudentClassAssignmentDrawer';
import { TeacherClassAssignmentDrawer } from './TeacherClassAssignmentDrawer';

const typeColumns = [
  { title: '编码', dataIndex: 'code', key: 'code' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  {
    title: '来源',
    dataIndex: 'builtIn',
    key: 'builtIn',
    render: (builtIn: boolean) => <Tag color={builtIn ? 'blue' : 'default'}>{builtIn ? '内置' : '自定义'}</Tag>
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    render: (status: OrganizationType['status']) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>
  },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 88 }
];

interface OrganizationManagementPageProps {
  currentUser?: CurrentUser;
}

export function OrganizationManagementPage({ currentUser }: OrganizationManagementPageProps) {
  const [types, setTypes] = useState<OrganizationType[]>([]);
  const [organizationTree, setOrganizationTree] = useState<OrganizationNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [nodeModalOpen, setNodeModalOpen] = useState(false);
  const [studentClassDrawerOpen, setStudentClassDrawerOpen] = useState(false);
  const [teacherClassDrawerOpen, setTeacherClassDrawerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [typeForm] = Form.useForm<CreateOrganizationTypeInput>();
  const [nodeForm] = Form.useForm<CreateOrganizationInput>();

  const parentOptions = useMemo(() => flattenNodes(organizationTree), [organizationTree]);

  useEffect(() => {
    void loadOrganizationData();
  }, []);

  async function loadOrganizationData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [loadedTypes, loadedTree] = await Promise.all([
        organizationApi.listTypes(),
        organizationApi.listTree()
      ]);
      setTypes(loadedTypes);
      setOrganizationTree(loadedTree);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function createOrganizationType(values: CreateOrganizationTypeInput): Promise<void> {
    setSubmitting(true);
    try {
      await organizationApi.createType({ ...values, sortOrder: values.sortOrder ?? 100 });
      message.success('组织类型已创建');
      setTypeModalOpen(false);
      typeForm.resetFields();
      await loadOrganizationData();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function createOrganizationNode(values: CreateOrganizationInput): Promise<void> {
    setSubmitting(true);
    try {
      await organizationApi.createOrganization({
        ...values,
        parentId: values.parentId || undefined,
        sortOrder: values.sortOrder ?? 100
      });
      message.success('组织节点已创建');
      setNodeModalOpen(false);
      nodeForm.resetFields();
      await loadOrganizationData();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>组织管理</h1>
        <Space wrap>
          {currentUser?.roleCodes.includes('ORG_ADMIN') && (
            <>
              <Button icon={<GraduationCap size={16} />} onClick={() => setStudentClassDrawerOpen(true)}>配置学生班级</Button>
              <Button icon={<UserRoundCheck size={16} />} onClick={() => setTeacherClassDrawerOpen(true)}>配置教师班级</Button>
            </>
          )}
          <Button icon={<Plus size={16} />} onClick={() => setTypeModalOpen(true)}>新增组织类型</Button>
          <Button type="primary" icon={<FolderPlus size={16} />} onClick={() => setNodeModalOpen(true)}>新增组织节点</Button>
        </Space>
      </div>

      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button size="small" onClick={() => void loadOrganizationData()}>重试</Button>} />}

      <Spin spinning={loading}>
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={10}>
            <Card title="组织类型" className="content-panel">
              <Table<OrganizationType>
                rowKey="id"
                columns={typeColumns}
                dataSource={types}
                pagination={false}
                size="middle"
                locale={{ emptyText: '暂无组织类型' }}
              />
            </Card>
          </Col>
          <Col xs={24} xl={14}>
            <Card title="组织树" className="content-panel">
              {organizationTree.length > 0 ? (
                <Tree
                  showLine
                  defaultExpandAll
                  treeData={toTreeData(organizationTree)}
                  className="organization-tree"
                />
              ) : <div className="empty-state">暂无组织节点</div>}
            </Card>
          </Col>
        </Row>
      </Spin>

      <Modal title="新增组织类型" open={typeModalOpen} footer={null} onCancel={() => setTypeModalOpen(false)} destroyOnHidden>
        <Form form={typeForm} layout="vertical" initialValues={{ sortOrder: 100 }} onFinish={createOrganizationType}>
          <Form.Item name="code" label="类型编码" rules={[{ required: true, message: '请输入类型编码' }, { max: 32, message: '类型编码不能超过 32 个字符' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="name" label="类型名称" rules={[{ required: true, message: '请输入类型名称' }, { max: 32, message: '类型名称不能超过 32 个字符' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序" rules={[{ required: true, message: '请输入排序值' }]}>
            <InputNumber min={0} precision={0} className="full-width" />
          </Form.Item>
          <div className="form-actions"><Button onClick={() => setTypeModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit" loading={submitting}>创建类型</Button></div>
        </Form>
      </Modal>

      <Modal title="新增组织节点" open={nodeModalOpen} footer={null} onCancel={() => setNodeModalOpen(false)} destroyOnHidden>
        <Form form={nodeForm} layout="vertical" initialValues={{ sortOrder: 100 }} onFinish={createOrganizationNode}>
          <Form.Item name="code" label="组织编码" rules={[{ required: true, message: '请输入组织编码' }, { max: 64, message: '组织编码不能超过 64 个字符' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="name" label="组织名称" rules={[{ required: true, message: '请输入组织名称' }, { max: 100, message: '组织名称不能超过 100 个字符' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="typeCode" label="组织类型" rules={[{ required: true, message: '请选择组织类型' }]}>
            <Select options={types.filter((item) => item.status === 'ENABLED').map((item) => ({ value: item.code, label: `${item.name}（${item.code}）` }))} />
          </Form.Item>
          <Form.Item name="parentId" label="上级组织">
            <Select allowClear placeholder="不选择则创建根节点" options={parentOptions.map((item) => ({ value: item.id, label: item.label }))} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序" rules={[{ required: true, message: '请输入排序值' }]}>
            <InputNumber min={0} precision={0} className="full-width" />
          </Form.Item>
          <div className="form-actions"><Button onClick={() => setNodeModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit" loading={submitting}>创建节点</Button></div>
        </Form>
      </Modal>

      <StudentClassAssignmentDrawer
        open={studentClassDrawerOpen}
        onClose={() => setStudentClassDrawerOpen(false)}
      />
      <TeacherClassAssignmentDrawer
        open={teacherClassDrawerOpen}
        onClose={() => setTeacherClassDrawerOpen(false)}
      />
    </div>
  );
}

function toTreeData(nodes: OrganizationNode[]): DataNode[] {
  return nodes.map((node) => ({
    key: node.id,
    title: <Space size={6}><Building2 size={15} aria-hidden="true" /><span>{node.name}</span><Tag>{node.typeCode}</Tag></Space>,
    children: toTreeData(node.children)
  }));
}

function flattenNodes(nodes: OrganizationNode[], level = 0): Array<{ id: string; label: string }> {
  return nodes.flatMap((node) => [
    { id: node.id, label: `${'-- '.repeat(level)}${node.name}（${node.code}）` },
    ...flattenNodes(node.children, level + 1)
  ]);
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
