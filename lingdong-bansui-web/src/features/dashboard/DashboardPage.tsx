import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useState } from 'react';
import { Alert, Descriptions, Popconfirm, Space, Tabs, Tag, message } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { ClipboardCheck, LogOut, MonitorX, RefreshCw } from 'lucide-react';
import { authApi, type AccountSecurityEvent, type CurrentUser, type DeviceSession } from '../../api/auth';
import { ParentAccountLifecyclePanel } from './ParentAccountLifecyclePanel';
import { DashboardTaskReviews } from './DashboardTaskReviews';
import { OrganizationActivityTrend } from './OrganizationActivityTrend';

interface DashboardPageProps {
  currentUser: CurrentUser;
  accountSecurityManagementEnabled: boolean;
  parentAccountLifecycleEnabled?: boolean;
  onSessionEnded: () => void;
  attendanceAvailable?: boolean;
  onOpenAttendance?: () => void;
  learningTaskManagementEnabled?: boolean;
}

export function DashboardPage({ currentUser, accountSecurityManagementEnabled, parentAccountLifecycleEnabled = false, attendanceAvailable = false, onOpenAttendance, onSessionEnded, learningTaskManagementEnabled = false }: DashboardPageProps) {
  const [devices, setDevices] = useState<DeviceSession[]>([]);
  const [events, setEvents] = useState<AccountSecurityEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (accountSecurityManagementEnabled) void loadSecurityData();
  }, [accountSecurityManagementEnabled]);

  async function loadSecurityData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [deviceRows, eventRows] = await Promise.all([
        authApi.listDevices(),
        authApi.listSecurityEvents()
      ]);
      setDevices(deviceRows);
      setEvents(eventRows);
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
      await loadSecurityData();
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

  async function markEventRead(eventId: string): Promise<void> {
    try {
      await authApi.markSecurityEventRead(eventId);
      setEvents((current) => current.map((event) => event.id === eventId
        ? { ...event, status: 'READ', readAt: new Date().toISOString() }
        : event));
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  async function markAllEventsRead(): Promise<void> {
    try {
      await authApi.markAllSecurityEventsRead();
      const readAt = new Date().toISOString();
      setEvents((current) => current.map((event) => ({ ...event, status: 'READ', readAt })));
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>工作台</h1>
        {attendanceAvailable && <Button actionKey="dashboard.dashboard-page.1" icon={<ClipboardCheck size={16} />} onClick={onOpenAttendance}>考勤台账</Button>}
        {accountSecurityManagementEnabled && <Button actionKey="dashboard.dashboard-page.2" icon={<RefreshCw size={16} />} onClick={() => void loadSecurityData()}>刷新</Button>}
      </div>
      {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
      <ProCard className="content-panel" bordered={false}>
        <div className="identity-grid">
          <Descriptions column={{ xs: 1, sm: 2 }} size="small">
            <Descriptions.Item label="姓名">{currentUser.displayName}</Descriptions.Item>
            <Descriptions.Item label="账号">{currentUser.username}</Descriptions.Item>
            <Descriptions.Item label="客户端">{currentUser.clientType}</Descriptions.Item>
            <Descriptions.Item label="角色"><Space wrap>{currentUser.roleCodes.map((role) => <Tag key={role}>{role}</Tag>)}</Space></Descriptions.Item>
          </Descriptions>
          <div className="identity-actions">
            <Button actionKey="dashboard.dashboard-page.3" danger icon={<LogOut size={16} />} onClick={() => void authApi.signOutCurrent().finally(onSessionEnded)}>退出当前会话</Button>
          </div>
        </div>
      </ProCard>
      <Tabs className="page-sections" items={[
        ...(learningTaskManagementEnabled && !currentUser.roleCodes.includes('SYS_AUDITOR')
          && currentUser.roleCodes.some(role => ['PARENT', 'TEACHER', 'ORG_ADMIN'].includes(role))
          && currentUser.permissionCodes.includes('TASK_ASSIGNMENT_REVIEW')
          ? [{ key: 'reviews', label: '待审核任务', children: <DashboardTaskReviews key={currentUser.userId} userId={currentUser.userId} /> }] : []),
        ...(learningTaskManagementEnabled && !currentUser.roleCodes.includes('SYS_AUDITOR') && currentUser.roleCodes.includes('ORG_ADMIN')
          ? [{ key: 'activity', label: '活跃趋势', children: <OrganizationActivityTrend key={currentUser.userId} userId={currentUser.userId} /> }] : []),
        ...(accountSecurityManagementEnabled ? [
          { key: 'events', label: '安全事件', children: (<ProCard className="content-panel" title="账号安全事件" bordered={false}>
          {events.some((event) => event.riskLevel === 'WARNING' && event.status === 'UNREAD') && (
            <Alert type="warning" showIcon message="检测到新的 Web 设备登录" />
          )}
          <Table<AccountSecurityEvent>
            rowKey="id"
            loading={loading}
            dataSource={events}
            pagination={{ pageSize: 8, showSizeChanger: false, hideOnSinglePage: true }}
            locale={{ emptyText: '暂无安全事件' }}
            columns={[
              { title: '事件', key: 'eventType', render: (_, event) => eventTitle(event) },
              { title: '设备', dataIndex: 'deviceName', key: 'deviceName' },
              { title: '发生时间', dataIndex: 'occurredAt', key: 'occurredAt', render: formatTime },
              { title: '状态', key: 'status', width: 120, render: (_, event) => event.status === 'UNREAD'
                ? <Button actionKey="dashboard.dashboard-page.4" type="link" onClick={() => void markEventRead(event.id)}>标记已读</Button>
                : <Tag>已读</Tag> }
            ]}
          />
          <div className="panel-footer"><Button actionKey="dashboard.dashboard-page.5" onClick={() => void markAllEventsRead()}>全部标记已读</Button></div>
        </ProCard>) },
          { key: 'devices', label: '设备会话', children: (<ProCard className="content-panel" title="设备会话" bordered={false}>
          <Table<DeviceSession>
            rowKey="id"
            loading={loading}
            dataSource={devices}
            pagination={{ pageSize: 8, showSizeChanger: false, hideOnSinglePage: true }}
            locale={{ emptyText: '暂无活动设备' }}
            columns={[
              { title: '设备名称', dataIndex: 'deviceName', key: 'deviceName' },
              { title: '客户端', dataIndex: 'clientType', key: 'clientType', width: 110 },
              { title: '最近活动', dataIndex: 'lastActiveAt', key: 'lastActiveAt', render: formatTime },
              {
                title: '操作', key: 'action', width: 110,
                render: (_, device) => device.current
                  ? <Tag color="green">当前设备</Tag>
                  : <Popconfirm title="确认下线此设备？" onConfirm={() => void signOutDevice(device.id)}><Button actionKey="dashboard.dashboard-page.6" danger type="text" icon={<MonitorX size={16} />} aria-label={`下线 ${device.deviceName}`} /></Popconfirm>
              }
            ]}
          />
          <div className="panel-footer"><Popconfirm title="确认下线全部设备？" onConfirm={() => void signOutAllDevices()}><Button actionKey="dashboard.dashboard-page.7" danger>下线全部设备</Button></Popconfirm></div>
        </ProCard>) }
        ] : []),
        ...(parentAccountLifecycleEnabled && currentUser.roleCodes.includes('PARENT')
          ? [{ key: 'account', label: '账号设置', children: <ParentAccountLifecyclePanel onSessionEnded={onSessionEnded} /> }] : [])
      ]} />
    </div>
  );
}

function eventTitle(event: AccountSecurityEvent): string {
  if (event.eventType === 'NEW_DEVICE_LOGIN') return `新设备登录（${event.clientType === 'WEB' ? 'Web' : '小程序'}）`;
  if (event.eventType === 'DEVICE_REVOKED') return '设备已下线';
  return '全部设备已下线';
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}
