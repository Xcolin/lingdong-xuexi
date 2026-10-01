import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useEffect, useState } from 'react';
import { Alert, Spin } from 'antd';
import { authApi } from '../../api/auth';
import { capabilityApi } from '../../api/capability';
import { dashboardApi, type ActivityTrendPoint } from '../../api/dashboard';

/** 机构管理员看板复杂统计：复用服务端活跃度聚合查询，不新增指标定义。 */
export function OrganizationActivityTrend({ userId }: { userId: string }) {
  const [allowed, setAllowed] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string>();
  const [items, setItems] = useState<ActivityTrendPoint[]>([]);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    setAllowed(false); setBusy(true); setError(undefined);
    void Promise.all([authApi.currentUser(), capabilityApi.web()]).then(([user, capability]) => {
      if (!active) return;
      setAllowed(user.userId === userId && user.clientType === 'WEB'
        && capability.learningTaskManagementEnabled
        && user.roleCodes.includes('ORG_ADMIN'));
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '看板权限核验失败'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [userId, reload]);

  useEffect(() => {
    if (!allowed) return;
    let active = true;
    setBusy(true); setError(undefined);
    void dashboardApi.activityTrends().then((trends) => {
      if (active) setItems(trends.items);
    }).catch(cause => {
      if (active) setError(cause instanceof Error ? cause.message : '活跃度统计加载失败');
    }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [allowed, reload]);

  const maxActive = Math.max(1, ...items.map(item => item.activeStudents));
  return <section aria-label="学员活跃度趋势">
    <h2>学员活跃度趋势</h2>
    {busy && <Spin />}
    {error && <Alert type="error" showIcon message={error}
      action={<Button actionKey="dashboard.organization-activity-trend.1" aria-label="重试" onClick={() => setReload(value => value + 1)}>重试</Button>} />}
    {!busy && !error && !allowed
      && <p>当前无机构管理员看板权限或学习任务功能未开启。</p>}
    {!busy && !error && allowed && items.length === 0
      && <p>近 30 天暂无学员活跃数据</p>}
    {!busy && !error && allowed && items.length > 0 && <div className="activity-trend" role="img"
      aria-label={`近 ${items.length} 天学员活跃度，最高单日 ${maxActive} 人`}>
      {items.map(item => <div key={item.date} className="activity-trend-column"
        title={`${item.date}：${item.activeStudents} 人`}>
        <div className="activity-trend-bar"
          style={{ height: `${Math.max(4, Math.round(item.activeStudents * 96 / maxActive))}px` }} />
        <span className="activity-trend-date">{item.date.slice(5)}</span>
      </div>)}
    </div>}
  </section>;
}
