import { Button, Alert, Spin, List, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi, type CurrentUser } from '../../api/auth';
import { capabilityApi, type ClientCapabilities } from '../../api/capability';
import { systemTasksApi, type SystemTaskPage } from '../../api/system-tasks';
import { canAccessSystemTasks, systemTaskDestination } from '../system-tasks/SystemTaskWorkbench';
import { formatDateTime } from '../../utils/datetime';
import { TaskReviewQueue } from '../learning-tasks/TaskReviewQueue';

export function DashboardTaskReviews({ userId }: { userId: string }) {
  const navigate = useNavigate();
  const [access, setAccess] = useState<{ user: CurrentUser; caps: ClientCapabilities }>();
  const [tasks, setTasks] = useState<SystemTaskPage>();
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string>();
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setAccess(undefined); setTasks(undefined); setBusy(true); setError(undefined);
    void Promise.all([authApi.currentUser(), capabilityApi.web()]).then(async ([user, caps]) => {
      if (!active) return;
      if (user.userId !== userId || user.clientType !== 'WEB') return;
      setAccess({ user, caps });
      if (canAccessSystemTasks(user, caps)) {
        const result = await systemTasksApi.list({ page: 1, pageSize: 5, status: 'PENDING_REVIEW' });
        if (active) setTasks(result);
      }
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '待审核任务加载失败'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [userId, reload]);
  useEffect(() => {
    const refresh = () => { setAccess(undefined); setTasks(undefined); setReload(value => value + 1); };
    window.addEventListener('focus', refresh);
    return () => window.removeEventListener('focus', refresh);
  }, []);
  const learning = access?.caps.learningTaskManagementEnabled && access.user.permissionCodes.includes('TASK_ASSIGNMENT_REVIEW');
  return <section aria-label="待审核任务">
    <h2>待审核任务</h2>
    {busy && <Spin />}
    {error && <Alert type="error" message={error} action={<Button onClick={() => setReload(value => value + 1)}>重试</Button>} />}
    {!busy && tasks && access && <><h3>系统任务（{tasks.total}）</h3><List dataSource={tasks.items} locale={{ emptyText: '暂无待审核系统任务' }} renderItem={task => {
      const review = (task.submittedById ?? task.submittedBy) !== access.user.userId && (task.type === 'ORGANIZATION_ONBOARDING' ? access.user.permissionCodes.includes('ORGANIZATION_ONBOARDING_REVIEW') : !!systemTaskDestination(task, access.user, access.caps));
      return <List.Item actions={[<Button key="open" onClick={() => navigate(`/system-tasks?taskId=${encodeURIComponent(task.id)}`)}>{review ? '审核' : '查看'}</Button>]}><List.Item.Meta title={<><Tag>{task.type === 'ORGANIZATION_ONBOARDING' ? '机构入驻' : task.type}</Tag>{task.title}</>} description={`申请人：${task.submittedBy || '—'} · ${formatDateTime(task.submittedAt)}`} /></List.Item>;
    }} /><Button onClick={() => navigate('/system-tasks')}>全部系统任务</Button></>}
    {!busy && learning && <TaskReviewQueue key={`${userId}-${reload}`} />}
    {!busy && !error && !learning && !tasks && <p>当前无任务审核权限或功能未开启。</p>}
  </section>;
}
