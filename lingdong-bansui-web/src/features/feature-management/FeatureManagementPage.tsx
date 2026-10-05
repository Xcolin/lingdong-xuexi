import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Descriptions, Input, Modal, Tabs } from 'antd';
import { authApi, type CurrentUser } from '../../api/auth';
import { capabilityApi, type ClientCapabilities } from '../../api/capability';
import { ApiRequestError } from '../../api/http';
import { featureManagementApi as api, type FeatureToggleChange, type GlobalFeatureToggle } from '../../api/feature-management';
type Access = { user: CurrentUser; caps: ClientCapabilities };
const status = (value: string | null) => value === null ? '未知' : ({ ENABLED: '已启用', DISABLED: '已停用', PENDING_REVIEW: '待审核', EFFECTIVE: '已生效', REJECTED: '已驳回', APPROVED: '已批准', VOIDED: '已作废', DRAFT: '草稿' }[value] || value);
const reviewer = (user: CurrentUser) => user.permissionCodes.includes('FEATURE_TOGGLE_REVIEW');
const manager = (user: CurrentUser) => user.permissionCodes.includes('FEATURE_TOGGLE_MANAGE');
const reader = (user: CurrentUser) => user.permissionCodes.includes('FEATURE_TOGGLE_READ');
const blocked = (row: GlobalFeatureToggle) => row.status === 'DISABLED' && (!row.enableAllowed || ['GEO_ATTENDANCE', 'STUDENT_LOCATION_TRACK'].includes(row.featureCode));
export function FeatureManagementPage({ currentUser, onAccessChange }: { currentUser: CurrentUser; onAccessChange?: (user: CurrentUser, caps: ClientCapabilities) => void }) {
  const [section, setSection] = useState('toggles');
  const [access, setAccess] = useState<Access | null>(null);
  const [toggles, setToggles] = useState<GlobalFeatureToggle[]>([]);
  const [changes, setChanges] = useState<FeatureToggleChange[]>([]);
  const [queue, setQueue] = useState<FeatureToggleChange[]>([]);
  const [page, setPage] = useState(1), [queuePage, setQueuePage] = useState(1);
  const [total, setTotal] = useState(0), [queueTotal, setQueueTotal] = useState(0);
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [selected, setSelected] = useState<GlobalFeatureToggle | null>(null);
  const [review, setReview] = useState<{ item: FeatureToggleChange; action: 'approve' | 'reject' } | null>(null);
  const [detail, setDetail] = useState<FeatureToggleChange | null>(null);
  const [description, setDescription] = useState(''), [comment, setComment] = useState(''), [confirming, setConfirming] = useState(false), [formError, setFormError] = useState('');
  const generation = useRef(0);
  const mounted = useRef(true);
  const latestLoad = useRef<() => Promise<void>>(async () => {});
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const clear = useCallback(() => { setAccess(null); setToggles([]); setChanges([]); setQueue([]); setTotal(0); setQueueTotal(0); setSelected(null); setReview(null); setDetail(null); setDescription(''); setComment(''); setConfirming(false); setFormError(''); }, []);
  const validate = useCallback(async () => {
    const [user, caps] = await Promise.all([authApi.currentUser(), capabilityApi.web()]);
    if (user.userId !== currentUser.userId || user.clientType !== 'WEB' || caps.client !== 'WEB') throw new Error('当前会话无功能开关读取权限');
    return { user, caps };
  }, [currentUser.userId]);
  const load = useCallback(async () => {
    const request = ++generation.current;
    clear(); setLoading(true); setError('');
    try {
      const fresh = await validate();
      if (request !== generation.current) return;
      onAccessChange?.(fresh.user, fresh.caps);
      if (!reader(fresh.user)) throw new Error('当前会话无功能开关读取权限');
      const [list, history, pending] = await Promise.all([api.toggles(), api.changes({ page, pageSize: 20 }), reviewer(fresh.user) ? api.reviewQueue({ page: queuePage, pageSize: 20 }) : Promise.resolve({ items: [], total: 0 })]);
      if (request !== generation.current) return;
      setAccess(fresh); setToggles(list); setChanges(history.items); setTotal(history.total); setQueue(pending.items); setQueueTotal(pending.total);
    } catch (cause) { if (request === generation.current) { clear(); setError(cause instanceof Error ? cause.message : '功能开关查询失败'); } }
    finally { if (request === generation.current) setLoading(false); }
  }, [clear, validate, page, queuePage, onAccessChange]);
  latestLoad.current = load;
  useEffect(() => { void load(); const focus = () => { void load(); }; window.addEventListener('focus', focus); return () => { ++generation.current; window.removeEventListener('focus', focus); }; }, [load]);
  async function mutate() {
    if (review?.action === 'reject' && !comment.trim()) { setFormError('请填写驳回意见'); return; }
    const request = ++generation.current;
    setLoading(true); setError(''); setNotice('');
    try {
      const fresh = await validate();
      if (request !== generation.current) return;
      onAccessChange?.(fresh.user, fresh.caps);
      if (!reader(fresh.user)) throw new Error('当前会话无功能开关读取权限');
      if (selected) {
        if (!manager(fresh.user) || blocked(selected)) throw new Error('当前会话无功能开关管理权限');
        if (!description.trim() || !confirming) throw new Error('请填写申请说明并确认提交');
        await api.submit({ featureCode: selected.featureCode, targetStatus: selected.status === 'ENABLED' ? 'DISABLED' : 'ENABLED', expectedVersion: selected.versionNo, title: `${selected.status === 'ENABLED' ? '停用' : '启用'}${selected.featureName}`, description: description.trim(), confirmed: true });
      } else if (review) {
        if (!reviewer(fresh.user) || review.item.submittedByUserId === fresh.user.userId) throw new Error('当前会话无功能开关审批权限');
        await api[review.action](review.item.taskId, { comment: comment.trim() });
      } else return;
      // 写入已完成时不能因期间发生过查询而遗漏刷新；重新鉴权，不采用旧写响应恢复页面。
      if (request !== generation.current) { if (mounted.current) await latestLoad.current(); return; }
      setNotice(selected ? '申请已提交，等待审核后生效' : '审批完成，已刷新当前开关与客户端能力');
      await load();
    } catch (cause) {
      if (request !== generation.current) { if (mounted.current) await latestLoad.current(); return; }
      const conflict = cause instanceof ApiRequestError && (cause.status === 409 || cause.code === 'FEATURE_TOGGLE_CONFLICT');
      if (conflict) { await load(); setNotice('开关版本已变化，已刷新当前状态，请重新发起申请。'); }
      else { clear(); setError(cause instanceof Error ? cause.message : '操作失败，请刷新重试'); }
    } finally { if (request === generation.current) setLoading(false); }
  }
  const columns = [
    { title: '申请标题', dataIndex: 'title', width: 240, ellipsis: true }, { title: '功能', dataIndex: 'featureName', width: 180, ellipsis: true },
    { title: '申请前状态', dataIndex: 'beforeStatus', width: 110, render: status }, { title: '目标状态', dataIndex: 'targetStatus', width: 100, render: status }, { title: '当前状态', dataIndex: 'currentStatus', width: 100, render: status },
    { title: '任务状态', dataIndex: 'taskStatus', width: 100, render: status }, { title: '申请人', dataIndex: 'submittedBy', width: 130, ellipsis: true }, { title: '审批人', dataIndex: 'reviewedBy', width: 130, ellipsis: true, render: (value: string | null) => value || '—' },
    { title: '记录', width: 110, fixed: 'right' as const, render: (_: unknown, row: FeatureToggleChange) => <Button actionKey="feature-management.feature-management-page.1" aria-label="查看记录" onClick={() => setDetail(row)}>查看记录</Button> }
  ];
  return <div className="page-stack">
    {error && <Alert type="error" message={error} action={<Button actionKey="feature-management.feature-management-page.2" aria-label="重试" onClick={() => void load()}>重试</Button>} />}
    {notice && <Alert type="info" message={notice} />}
    <Tabs className="page-sections" activeKey={section === 'queue' && !(access && reviewer(access.user)) ? 'toggles' : section} onChange={setSection} items={[
      { key: 'toggles', label: '开关列表', children: <Table<GlobalFeatureToggle> rowKey="id" loading={loading} dataSource={toggles} pagination={false} scroll={{ x: 1100 }} locale={{ emptyText: error ? '功能开关未能加载' : '暂无全局功能开关' }} columns={[
      { title: '功能编码', dataIndex: 'featureCode', width: 260, ellipsis: true }, { title: '功能名称', dataIndex: 'featureName', width: 200, ellipsis: true }, { title: '当前状态', dataIndex: 'status', width: 90, render: status }, { title: '版本', dataIndex: 'versionNo', width: 80, ellipsis: true }, { title: '说明', dataIndex: 'description', width: 360, ellipsis: true },
      { title: '操作', width: 110, fixed: 'right', render: (_, row) => access && manager(access.user) ? <Button actionKey="FEATURE_TOGGLE_MANAGE" aria-label={row.status === 'ENABLED' ? '申请停用' : '申请启用'} disabled={loading || blocked(row)} onClick={() => { setSelected(row); setDescription(''); setFormError(''); setConfirming(false); }}>{row.status === 'ENABLED' ? '申请停用' : '申请启用'}</Button> : '只读' }
    ]} /> },
      { key: 'history', label: '申请与审批记录', children: <Table<FeatureToggleChange> rowKey="id" loading={loading} dataSource={changes} columns={columns} scroll={{ x: 1200 }} locale={{ emptyText: '暂无申请记录' }} pagination={{ current: page, total, pageSize: 20, showSizeChanger: false, showTotal: n => `共 ${n} 条`, onChange: setPage }} /> },
      ...(access && reviewer(access.user) ? [{ key: 'queue', label: '待审批申请', children: <Table<FeatureToggleChange> rowKey="id" loading={loading} dataSource={queue} columns={[...columns, { title: '审批操作', width: 150, fixed: 'right', render: (_, row) => row.taskStatus === 'PENDING_REVIEW' && row.submittedByUserId !== access.user.userId && <><Button actionKey="FEATURE_TOGGLE_REVIEW" aria-label="批准" disabled={loading || row.beforeStatus === null || row.baseVersion === null || (row.targetStatus === 'ENABLED' && (!row.enableAllowed || ['GEO_ATTENDANCE', 'STUDENT_LOCATION_TRACK'].includes(row.featureCode)))} onClick={() => { setReview({ item: row, action: 'approve' }); setComment(''); setFormError(''); }}>批准</Button><Button actionKey="feature-management.feature-management-page.6" aria-label="驳回" disabled={loading} onClick={() => { setReview({ item: row, action: 'reject' }); setComment(''); setFormError(''); }}>驳回</Button></> }]} scroll={{ x: 1350 }} locale={{ emptyText: '暂无待审批申请' }} pagination={{ current: queuePage, total: queueTotal, pageSize: 20, showSizeChanger: false, onChange: setQueuePage }} /> }] : [])
    ]} />
    {selected && <Modal open title={confirming ? '确认全局功能变更申请' : '申请全局功能变更'} onCancel={() => setSelected(null)} footer={<Button actionKey="feature-management.feature-management-page.7" type="primary" aria-label={confirming ? '确认提交' : '下一步'} loading={loading} onClick={() => { if (!description.trim()) setFormError('请填写申请说明'); else if (!confirming) { setFormError(''); setConfirming(true); } else void mutate(); }}>{confirming ? '确认提交' : '下一步'}</Button>} width="min(640px, 92vw)">
      <p>{selected.featureCode === 'ORGANIZATION_MINIAPP_AUTH' && selected.status === 'ENABLED' ? '停用后将强制退出所有机构小程序活动会话。' : ''}</p><p>{selected.featureName}：{status(selected.status)} → {status(selected.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')}（当前版本 {selected.versionNo}）</p>
      {formError && <Alert type="error" message={formError} />}
      {confirming ? <><p>{description}</p><Alert type="warning" message="请确认影响范围为全局，审核通过后将影响相关客户端功能。" /></> : <><label htmlFor="feature-description">申请说明</label><Input.TextArea id="feature-description" value={description} maxLength={1000} onChange={e => setDescription(e.target.value)} /></>}
    </Modal>}
    {review && <Modal open title={review.action === 'approve' ? '批准全局功能变更' : '驳回全局功能变更'} onCancel={() => setReview(null)} footer={<Button actionKey="feature-management.feature-management-page.8" type="primary" aria-label={review.action === 'approve' ? '确认批准' : '确认驳回'} loading={loading} onClick={() => void mutate()}>{review.action === 'approve' ? '确认批准' : '确认驳回'}</Button>} width="min(520px, 92vw)"><p>{review.item.title}：{status(review.item.beforeStatus)} → {status(review.item.targetStatus)}；当前{status(review.item.currentStatus)}</p><p>{review.item.description}</p>{formError && <Alert type="error" message={formError} />}<label htmlFor="feature-comment">审批意见</label><Input.TextArea id="feature-comment" value={comment} maxLength={500} onChange={e => setComment(e.target.value)} /></Modal>}
    {detail && <Modal open title="申请与审批记录详情" onCancel={() => setDetail(null)} footer={<Button actionKey="feature-management.feature-management-page.9" onClick={() => setDetail(null)}>关闭</Button>} width="min(1100px, 92vw)"><Descriptions column={{ xs: 1, sm: 2 }} items={[
      ['申请标题', detail.title], ['功能编码', detail.featureCode], ['申请说明', detail.description], ['申请前状态', status(detail.beforeStatus)], ['目标状态', status(detail.targetStatus)], ['当前状态', status(detail.currentStatus)], ['申请版本', detail.baseVersion ?? '未知'], ['当前版本', detail.currentVersion], ['任务状态', status(detail.taskStatus)], ['申请人', detail.submittedBy], ['提交时间', detail.submittedAt], ['审批人', detail.reviewedBy], ['审批时间', detail.reviewedAt], ['审批意见', detail.reviewComment], ['创建时间', detail.createdAt]
    ].map(([label, value]) => ({ key: label!, label, children: value || '—' }))} /></Modal>}
  </div>;
}
