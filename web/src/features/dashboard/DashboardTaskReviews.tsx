import { useEffect, useState } from 'react';
import { Alert, Button, Spin } from 'antd';
import { authApi } from '../../api/auth';
import { capabilityApi } from '../../api/capability';
import { TaskReviewQueue } from '../learning-tasks/TaskReviewQueue';

/** 工作台复用本人审核队列；恢复窗口时重新核验实时角色与权限。 */
export function DashboardTaskReviews({ userId }: { userId: string }) {
  const [allowed, setAllowed] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string>();
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setAllowed(false); setBusy(true); setError(undefined);
    void Promise.all([authApi.currentUser(), capabilityApi.web()]).then(([user, capability]) => {
      if (!active) return;
      setAllowed(user.userId === userId && user.clientType === 'WEB'
        && capability.learningTaskManagementEnabled && !user.roleCodes.includes('SYS_AUDITOR')
        && user.roleCodes.some(role => ['PARENT', 'TEACHER', 'ORG_ADMIN'].includes(role))
        && user.permissionCodes.includes('TASK_ASSIGNMENT_REVIEW'));
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '待审核权限核验失败'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [userId, reload]);
  useEffect(() => {
    const refresh = () => { setAllowed(false); setReload(value => value + 1); };
    window.addEventListener('focus', refresh);
    return () => window.removeEventListener('focus', refresh);
  }, []);
  return <section aria-label="待审核任务">
    <h2>待审核任务</h2>
    {busy && <Spin />}
    {error && <Alert type="error" message={error} action={<Button onClick={() => setReload(value => value + 1)}>重试</Button>} />}
    {!busy && !error && !allowed && <p>当前无任务审核权限或功能未开启。</p>}
    {!busy && allowed && <TaskReviewQueue key={`${userId}-${reload}`} />}
  </section>;
}
