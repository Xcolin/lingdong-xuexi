import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Popconfirm, Space, Table, Tag, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { LogOut, MonitorX, RefreshCw } from 'lucide-react';
import { authApi, type CurrentUser, type DeviceSession } from '../../api/auth';

interface DashboardPageProps {
  currentUser: CurrentUser;
  onSessionEnded: () => void;
}

export function DashboardPage({ currentUser, onSessionEnded }: DashboardPageProps) {
  const [devices, setDevices] = useState<DeviceSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    void loadDevices();
  }, []);

  async function loadDevices(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      setDevices(await authApi.listDevices());
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function signOutDevice(sessionId: string): Promise<void> {
    try {
      await authApi.signOutDevice(sessionId);
      message.success('设备会话已下线');
      await loadDevices();
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  async function signOutAllDevices(): Promise<void> {
    try {
      await authApi.signOutAllDevices();
      authApi.clearLocalSession();
      onSessionEnded();
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  return (
    <div className="page-stack">
      <div className="page-heading"><h1>工作台</h1><Button icon={<RefreshCw size={16} />} onClick={() => void loadDevices()}>刷新</Button></div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
      <ProCard className="content-panel" title="当前身份" bordered={false}>
        <div className="identity-grid">
          <Descriptions column={{ xs: 1, sm: 2 }} size="small">
            <Descriptions.Item label="姓名">{currentUser.displayName}</Descriptions.Item>
            <Descriptions.Item label="账号">{currentUser.username}</Descriptions.Item>
            <Descriptions.Item label="客户端">{currentUser.clientType}</Descriptions.Item>
            <Descriptions.Item label="角色"><Space wrap>{currentUser.roleCodes.map((role) => <Tag key={role}>{role}</Tag>)}</Space></Descriptions.Item>
          </Descriptions>
          <div className="identity-actions">
            <Button danger icon={<LogOut size={16} />} onClick={() => void authApi.signOutCurrent().finally(onSessionEnded)}>退出当前会话</Button>
          </div>
        </div>
      </ProCard>
      <ProCard className="content-panel" title="设备会话" bordered={false}>
        <Table<DeviceSession>
          rowKey="id"
          loading={loading}
          dataSource={devices}
          pagination={false}
          locale={{ emptyText: '暂无活动设备' }}
          columns={[
            { title: '设备名称', dataIndex: 'deviceName', key: 'deviceName' },
            { title: '客户端', dataIndex: 'clientType', key: 'clientType', width: 110 },
            { title: '最近活动', dataIndex: 'lastActiveAt', key: 'lastActiveAt', render: formatTime },
            {
              title: '操作', key: 'action', width: 94,
              render: (_, device) => <Popconfirm title="确认下线此设备？" onConfirm={() => void signOutDevice(device.id)}><Button danger type="text" icon={<MonitorX size={16} />} aria-label={`下线 ${device.deviceName}`} /></Popconfirm>
            }
          ]}
        />
        <div className="panel-footer"><Popconfirm title="确认下线全部设备？" onConfirm={() => void signOutAllDevices()}><Button danger>下线全部设备</Button></Popconfirm></div>
      </ProCard>
    </div>
  );
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
