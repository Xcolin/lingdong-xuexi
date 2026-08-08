import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Input, Modal, QRCode, Space, Table, Tag, Tooltip, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { QrCode, RefreshCw, Search } from 'lucide-react';
import { studentLoginApi, type StudentDirectoryItem, type StudentDirectoryPage, type StudentLoginQrTicket } from './api';

const PAGE_SIZE = 20;

/** 主家长和直接机构管理员生成其数据范围内学生的一次性登录二维码。 */
export function StudentLoginManagementPage() {
  const [directory, setDirectory] = useState<StudentDirectoryPage>({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 });
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedStudent, setSelectedStudent] = useState<StudentDirectoryItem | null>(null);
  const [ticket, setTicket] = useState<StudentLoginQrTicket | null>(null);
  const [ticketLoading, setTicketLoading] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  const loadStudents = useCallback(async (nextKeyword: string, page: number) => {
    setLoading(true);
    setErrorMessage(null);
    try {
      setDirectory(await studentLoginApi.list(nextKeyword || undefined, page, PAGE_SIZE));
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStudents('', 1);
  }, [loadStudents]);

  const issueTicket = useCallback(async (student: StudentDirectoryItem) => {
    setTicketLoading(true);
    try {
      const issued = await studentLoginApi.issueQrTicket(student.id);
      setTicket(issued);
      setRemainingSeconds(secondsUntil(issued.expiresAt));
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setTicketLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!selectedStudent || !ticket) return;
    const interval = window.setInterval(() => setRemainingSeconds(secondsUntil(ticket.expiresAt)), 1000);
    const timeout = window.setTimeout(
      () => void issueTicket(selectedStudent),
      Math.max(0, new Date(ticket.expiresAt).getTime() - Date.now()) + 200
    );
    return () => {
      window.clearInterval(interval);
      window.clearTimeout(timeout);
    };
  }, [issueTicket, selectedStudent, ticket]);

  function openQr(student: StudentDirectoryItem): void {
    setSelectedStudent(student);
    setTicket(null);
    void issueTicket(student);
  }

  function closeQr(): void {
    setSelectedStudent(null);
    setTicket(null);
    setRemainingSeconds(0);
  }

  return (
    <div className="page-stack">
      <div className="page-heading"><h1>学生登录</h1></div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<Button size="small" onClick={() => void loadStudents(keyword, directory.page)}>重试</Button>} />}
      <ProCard className="content-panel" bordered={false}>
        <Space className="student-login-toolbar">
          <Input
            allowClear
            value={keyword}
            placeholder="学生姓名"
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => void loadStudents(keyword.trim(), 1)}
          />
          <Button type="primary" icon={<Search size={16} />} onClick={() => void loadStudents(keyword.trim(), 1)}>查询</Button>
        </Space>
        <Table<StudentDirectoryItem>
          rowKey="id"
          loading={loading}
          dataSource={directory.items}
          locale={{ emptyText: '暂无可管理学生' }}
          pagination={{
            current: directory.page,
            pageSize: directory.pageSize,
            total: directory.total,
            showSizeChanger: false,
            onChange: (page) => void loadStudents(keyword.trim(), page)
          }}
          columns={[
            { title: '学生姓名', dataIndex: 'studentName', key: 'studentName' },
            { title: '年级', dataIndex: 'gradeCode', key: 'gradeCode', width: 140, render: (value: string | null) => value || '-' },
            { title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (status: StudentDirectoryItem['status']) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag> },
            {
              title: '操作', key: 'action', width: 96,
              render: (_, student) => (
                <Tooltip title="登录二维码">
                  <Button
                    type="text"
                    icon={<QrCode size={18} />}
                    aria-label={`生成 ${student.studentName} 的登录二维码`}
                    disabled={student.status !== 'ENABLED'}
                    onClick={() => openQr(student)}
                  />
                </Tooltip>
              )
            }
          ]}
        />
      </ProCard>

      <Modal
        title={selectedStudent ? `${selectedStudent.studentName}的登录二维码` : '学生登录二维码'}
        open={Boolean(selectedStudent)}
        onCancel={closeQr}
        footer={<Button onClick={closeQr}>关闭</Button>}
        destroyOnHidden
      >
        <div className="student-login-qr">
          {ticket && <QRCode type="svg" value={ticket.qrContent} size={248} bordered={false} status={remainingSeconds > 0 ? 'active' : 'expired'} />}
          <div className="student-login-qr-status">
            {ticket ? `二维码将在 ${remainingSeconds} 秒后自动刷新` : '正在生成二维码'}
          </div>
          <Button
            icon={<RefreshCw size={16} />}
            loading={ticketLoading}
            disabled={!selectedStudent}
            onClick={() => selectedStudent && void issueTicket(selectedStudent)}
          >刷新二维码</Button>
        </div>
      </Modal>
    </div>
  );
}

function secondsUntil(expiresAt: string): number {
  return Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 1000));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
