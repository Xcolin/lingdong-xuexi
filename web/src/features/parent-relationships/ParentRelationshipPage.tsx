import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { ArrowRightLeft, Link2, UserMinus, UserPlus } from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import {
  parentRelationshipApi,
  type ParentRelationship,
  type ParentRelationshipMember,
  type ParentStudentOption
} from './api';

interface ParentRelationshipPageProps {
  currentUser: CurrentUser;
}

type InvitationMode = 'SECONDARY' | 'TRANSFER';

/** 家长当前关系的 Web 管理页面，副家长只读取关系，不渲染写命令。 */
export function ParentRelationshipPage({ currentUser }: ParentRelationshipPageProps) {
  const [students, setStudents] = useState<ParentStudentOption[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<string>();
  const [relationship, setRelationship] = useState<ParentRelationship | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [invitationMode, setInvitationMode] = useState<InvitationMode | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<{ mobile: string }>();

  const selectedStudent = useMemo(
    () => students.find((student) => student.studentId === selectedStudentId) ?? null,
    [selectedStudentId, students]
  );
  const canManage = selectedStudent?.relationshipRole === 'PRIMARY_GUARDIAN'
    && relationship?.primaryParentUserId === currentUser.userId;

  const loadRelationship = useCallback(async (studentId: string) => {
    setLoading(true);
    setErrorMessage(null);
    try {
      setRelationship(await parentRelationshipApi.get(studentId));
    } catch (error) {
      setRelationship(null);
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void parentRelationshipApi.listStudents()
      .then((loaded) => {
        setStudents(loaded);
        const firstStudentId = loaded[0]?.studentId;
        setSelectedStudentId(firstStudentId);
        if (firstStudentId) return loadRelationship(firstStudentId);
        setLoading(false);
      })
      .catch((error) => {
        setErrorMessage(toMessage(error));
        setLoading(false);
      });
  }, [loadRelationship]);

  function selectStudent(studentId: string): void {
    setSelectedStudentId(studentId);
    void loadRelationship(studentId);
  }

  async function submitInvitation(values: { mobile: string }): Promise<void> {
    if (!selectedStudentId || !invitationMode) return;
    setSubmitting(true);
    try {
      const invitation = invitationMode === 'SECONDARY'
        ? await parentRelationshipApi.createSecondaryInvitation(selectedStudentId, values.mobile)
        : await parentRelationshipApi.createPrimaryTransferInvitation(selectedStudentId, values.mobile);
      message.success(`邀请已发送至 ${invitation.maskedMobile}，请在 5 分钟内完成确认`);
      setInvitationMode(null);
      form.resetFields();
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  function confirmUnbindSecondary(member: ParentRelationshipMember): void {
    if (!selectedStudentId) return;
    Modal.confirm({
      title: '确认解除副家长',
      content: `解除后，${member.displayName || '该副家长'}将立即失去该学生的数据访问权限。`,
      okText: '确认解除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await parentRelationshipApi.unbindSecondary(selectedStudentId, member.userId);
        message.success('副家长关系已解除');
        await loadRelationship(selectedStudentId);
      }
    });
  }

  function confirmUnbindPrimary(): void {
    if (!selectedStudentId) return;
    Modal.confirm({
      title: '确认解除主家长关系',
      content: relationship?.secondaryParent
        ? '解除后，当前副家长将自动晋升为主家长。'
        : '解除后，该学生将暂时没有主家长，家庭写操作会停止。',
      okText: '确认解除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await parentRelationshipApi.unbindPrimary(selectedStudentId);
        message.success('主家长关系已解除');
        await loadRelationship(selectedStudentId);
      }
    });
  }

  const members = relationship
    ? [relationship.primaryParent, relationship.secondaryParent].filter(
      (member): member is ParentRelationshipMember => Boolean(member)
    )
    : [];

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>家长关系</h1>
        <Select
          className="parent-relationship-student-select"
          aria-label="选择学生"
          value={selectedStudentId}
          placeholder="选择学生"
          options={students.map((student) => ({ value: student.studentId, label: student.studentName }))}
          onChange={selectStudent}
        />
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
      {selectedStudent?.relationshipRole === 'SECONDARY_GUARDIAN'
        && <Alert type="info" showIcon message="副家长只读" description="可查看学生相关信息，关系变更由主家长操作。" />}
      <ProCard className="content-panel parent-relationship-panel" bordered={false}>
        {!selectedStudentId && !loading ? <Empty description="暂无关联学生" /> : <>
          {canManage && <Space className="parent-relationship-actions" wrap>
            <Tooltip title={relationship?.secondaryParent ? '需先解除现有副家长关系' : undefined}>
              <Button
                type="primary" icon={<UserPlus size={16} />}
                disabled={Boolean(relationship?.secondaryParent)}
                onClick={() => setInvitationMode('SECONDARY')}
              >邀请副家长</Button>
            </Tooltip>
            <Button icon={<ArrowRightLeft size={16} />} onClick={() => setInvitationMode('TRANSFER')}>
              转移监护权
            </Button>
            <Button danger icon={<UserMinus size={16} />} onClick={confirmUnbindPrimary}>
              解除我的主家长关系
            </Button>
          </Space>}
          <Table<ParentRelationshipMember>
            rowKey="userId"
            loading={loading}
            dataSource={members}
            pagination={false}
            locale={{ emptyText: '暂无家长关系' }}
            columns={[
              { title: '关系', dataIndex: 'relationshipRole', width: 140, render: (role) => (
                <Tag color={role === 'PRIMARY_GUARDIAN' ? 'green' : 'blue'}>
                  {role === 'PRIMARY_GUARDIAN' ? '主家长' : '副家长'}
                </Tag>
              ) },
              { title: '姓名', dataIndex: 'displayName', render: (value) => value || '-' },
              { title: '手机号', dataIndex: 'mobileMasked', render: (value) => value || '-' },
              { title: '状态', key: 'status', width: 110, render: () => <Tag color="green">有效</Tag> },
              { title: '操作', key: 'action', width: 140, render: (_, member) => (
                canManage && member.relationshipRole === 'SECONDARY_GUARDIAN'
                  ? <Button danger type="text" icon={<Link2 size={16} />}
                      onClick={() => confirmUnbindSecondary(member)}>解除副家长</Button>
                  : null
              ) }
            ]}
          />
        </>}
      </ProCard>

      <Modal
        title={invitationMode === 'SECONDARY' ? '邀请副家长' : '转移监护权'}
        open={Boolean(invitationMode)}
        okText="发送邀请"
        cancelText="取消"
        confirmLoading={submitting}
        onOk={() => form.submit()}
        onCancel={() => { setInvitationMode(null); form.resetFields(); }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(values) => void submitInvitation(values)}>
          <Form.Item name="mobile" label="目标家长手机号" rules={[
            { required: true, message: '请输入手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
          ]}>
            <Input inputMode="tel" autoComplete="tel" maxLength={11} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
