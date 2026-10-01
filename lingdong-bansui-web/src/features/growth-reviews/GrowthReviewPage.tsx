import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ProCard } from '@ant-design/pro-components';
import { App, Empty, Form, Input, List, Modal, Progress, Segmented, Select, Space, Statistic, Tag, Tooltip } from 'antd';
import { FilePenLine, RefreshCw } from 'lucide-react';
import { growthReviewApi } from './api';
import { GrowthReviewExportHistory } from './GrowthReviewExportHistory';
import { CreateGrowthReviewExport } from './CreateGrowthReviewExport';
import { GrowthReviewSubscriptionPanel } from './GrowthReviewSubscriptionPanel';
import type {
  AddGrowthReviewSupplementInput,
  GrowthReviewDetail,
  GrowthReviewPeriodType,
  GrowthReviewStudentOption,
  GrowthReviewSummary,
  GrowthReviewSupplementType
} from './types';

const PAGE_SIZE = 20;
const PERIOD_OPTIONS: Array<{ label: string; value: GrowthReviewPeriodType }> = [
  { label: '日报', value: 'DAY' },
  { label: '周报', value: 'WEEK' },
  { label: '月报', value: 'MONTH' }
];
const SUPPLEMENT_TYPES: Array<{ label: string; value: GrowthReviewSupplementType }> = [
  { label: '成长观察', value: 'INSIGHT' },
  { label: '优势与待提升', value: 'STRENGTH_WEAKNESS' },
  { label: '下一步计划', value: 'NEXT_PLAN' }
];

export function GrowthReviewPage({ canReadExportHistory = false, canCreateExport = false,
  subscriptionEnabled = false, canEnableSubscription = false }: {
  canReadExportHistory?: boolean; canCreateExport?: boolean;
  subscriptionEnabled?: boolean; canEnableSubscription?: boolean;
}) {
  const [created, setCreated] = useState<{ studentId: string; version: number }>();
  const { message } = App.useApp();
  const [form] = Form.useForm<AddGrowthReviewSupplementInput>();
  const [students, setStudents] = useState<GrowthReviewStudentOption[]>([]);
  const [studentId, setStudentId] = useState<string>();
  const [periodType, setPeriodType] = useState<GrowthReviewPeriodType>('DAY');
  const [template, setTemplate] = useState<'SIMPLE' | 'DETAILED'>('DETAILED');
  const [reviews, setReviews] = useState<GrowthReviewSummary[]>([]);
  const [detail, setDetail] = useState<GrowthReviewDetail>();
  const [loading, setLoading] = useState(false);
  const [supplementOpen, setSupplementOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  // 同一页面仅接受最新读取；切换范围和卸载均作废旧响应。
  const requestVersion = useRef(0);

  useEffect(() => {
    let active = true;
    void growthReviewApi.listStudents()
      .then((items) => {
        if (!active) return;
        setStudents(items);
        setStudentId((current) => current ?? items[0]?.studentId);
      })
      .catch((error) => { if (active) message.error(errorMessage(error)); });
    return () => { active = false; };
  }, [message]);

  const loadReviews = useCallback(async () => {
    const version = ++requestVersion.current;
    setReviews([]);
    setDetail(undefined);
    if (!studentId) {
      setReviews([]);
      setDetail(undefined);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const page = await growthReviewApi.list(studentId, periodType, 1, PAGE_SIZE);
      if (version !== requestVersion.current) return;
      setReviews(page.items);
      const first = page.items[0];
      const result = first ? await growthReviewApi.detail(studentId, first.reviewId) : undefined;
      if (version === requestVersion.current) setDetail(result);
    } catch (error) {
      if (version !== requestVersion.current) return;
      setReviews([]);
      setDetail(undefined);
      message.error(errorMessage(error));
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }, [message, periodType, studentId]);

  useEffect(() => {
    setSupplementOpen(false);
    setSubmitting(false);
    form.resetFields();
    void loadReviews();
    return () => { requestVersion.current++; };
  }, [form, loadReviews]);

  async function selectReview(review: GrowthReviewSummary): Promise<void> {
    if (!studentId || review.reviewId === detail?.reviewId) return;
    const version = ++requestVersion.current;
    setLoading(true);
    try {
      const result = await growthReviewApi.detail(studentId, review.reviewId);
      if (version === requestVersion.current) setDetail(result);
    } catch (error) {
      if (version === requestVersion.current) message.error(errorMessage(error));
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }

  async function submitSupplement(values: AddGrowthReviewSupplementInput): Promise<void> {
    if (!studentId || !detail) return;
    const version = requestVersion.current;
    setSubmitting(true);
    try {
      await growthReviewApi.supplement(studentId, detail.reviewId, {
        supplementType: values.supplementType,
        content: values.content.trim()
      });
      if (version !== requestVersion.current) return;
      const result = await growthReviewApi.detail(studentId, detail.reviewId);
      if (version !== requestVersion.current) return;
      setDetail(result);
      setSupplementOpen(false);
      form.resetFields();
      message.success('复盘补录已追加');
    } catch (error) {
      if (version === requestVersion.current) message.error(errorMessage(error));
    } finally {
      if (version === requestVersion.current) setSubmitting(false);
    }
  }

  const selectedStudentName = useMemo(
    () => students.find((item) => item.studentId === studentId)?.studentName,
    [studentId, students]
  );
  // 模板只控制已授权快照的呈现，不改变统计、请求或日报补录规则。
  const periodicReport = detail?.periodType === 'WEEK' || detail?.periodType === 'MONTH';
  const showDetails = !periodicReport || template === 'DETAILED';

  return (
    <div className="page-stack growth-review-page">
      <header className="page-heading">
        <h1>成长复盘</h1>
        <Space wrap>
          {canCreateExport && studentId && <CreateGrowthReviewExport key={studentId} studentId={studentId}
            reviewId={!loading && detail?.studentId === studentId ? detail.reviewId : undefined}
            onCreated={() => setCreated(value => ({ studentId, version: (value?.version ?? 0) + 1 }))} />}
          {canReadExportHistory && studentId && <GrowthReviewExportHistory key={`${studentId}-${created?.version ?? 0}`}
            studentId={studentId} initialOpen={created?.studentId === studentId} />}
          <Select
            aria-label="选择孩子"
            className="growth-review-student-select"
            value={studentId}
            placeholder="选择孩子"
            options={students.map((student) => ({
              label: student.studentName, value: student.studentId
            }))}
            onChange={value => { setCreated(undefined); setStudentId(value); }}
          />
          <Tooltip title="刷新复盘">
            <Button actionKey="growth-reviews.growth-review-page.1"
              aria-label="刷新复盘"
              icon={<RefreshCw size={16} />}
              loading={loading}
              onClick={() => void loadReviews()}
            />
          </Tooltip>
        </Space>
      </header>

      <div className="growth-review-toolbar">
        {subscriptionEnabled && studentId && <GrowthReviewSubscriptionPanel studentId={studentId}
          canEnable={canEnableSubscription} />}
        <Segmented
          aria-label="复盘周期"
          value={periodType}
          options={PERIOD_OPTIONS}
          onChange={(value) => setPeriodType(value as GrowthReviewPeriodType)}
        />
        {periodicReport && (
          <Segmented
            aria-label="复盘模板"
            value={template}
            options={[{ label: '简洁版', value: 'SIMPLE' }, { label: '详细版', value: 'DETAILED' }]}
            onChange={(value) => setTemplate(value as 'SIMPLE' | 'DETAILED')}
          />
        )}
      </div>

      {!studentId ? (
        <div className="growth-review-empty"><Empty description="暂无可查询成长复盘的孩子" /></div>
      ) : (
        <ProCard className="content-panel growth-review-workspace" bordered={false}>
          <div className="growth-review-grid">
            <section className="growth-review-list" aria-label="成长复盘列表">
              <Table<GrowthReviewSummary>
                rowKey="reviewId"
                size="small"
                loading={loading && !detail}
                dataSource={reviews}
                pagination={false}
                locale={{ emptyText: '暂无成长复盘' }}
                rowClassName={(row) => row.reviewId === detail?.reviewId ? 'selected-row' : ''}
                onRow={(row) => ({ onClick: () => void selectReview(row) })}
                columns={[
                  {
                    title: '周期', key: 'period',
                    render: (_, row) => (
                      <div className="growth-review-period">
                        <strong>{periodLabel(row)}</strong>
                        <span>完成 {row.completedCount}/{row.taskTotalCount}</span>
                      </div>
                    )
                  },
                  {
                    title: '积分', dataIndex: 'earnedPoints', key: 'earnedPoints', width: 78,
                    render: (value: number) => <strong className={value > 0 ? 'point-positive' : undefined}>{formatPoints(value)}</strong>
                  }
                ]}
              />
            </section>

            <section className="growth-review-detail" aria-label="成长复盘详情">
              {detail ? (
                <>
                  <div className="growth-review-detail-heading">
                    <div>
                      <h2>{selectedStudentName} · {periodLabel(detail)}</h2>
                      <Tag color="green">第 {detail.contentVersion} 版</Tag>
                    </div>
                    {detail.periodType === 'DAY' && (
                      <Button actionKey="growth-reviews.growth-review-page.2"
                        icon={<FilePenLine size={16} />}
                        onClick={() => setSupplementOpen(true)}
                      >补录复盘</Button>
                    )}
                  </div>

                  <div className={`growth-review-metrics${periodicReport ? ' growth-review-periodic-metrics' : ''}`}>
                    <Statistic title="完成率" value={formatRate(detail.completionRate)} />
                    <Statistic title="累计获取" value={detail.earnedPoints} suffix="分" />
                    <Statistic title="进行中" value={detail.inProgressCount} suffix="项" />
                    <Statistic title="情绪暂停" value={detail.pauseCount} suffix="次" />
                    {periodicReport && <Statistic title="待优化" value={detail.pendingOptimizationCount} suffix="项" />}
                  </div>
                  <Progress
                    percent={Math.round(detail.completionRate * 100)}
                    showInfo={false}
                    strokeColor="#167c5a"
                    trailColor="#e5ece8"
                  />

                  {showDetails && <>
                  {periodicReport && (
                    <section className="growth-review-section" aria-label="成长分析">
                      <h3>成长分析</h3>
                      <p>本周期共 {detail.taskTotalCount} 项任务，已完成 {detail.completedCount} 项，进行中 {detail.inProgressCount} 项，待优化 {detail.pendingOptimizationCount} 项。</p>
                      <h3>下一步计划</h3>
                      {detail.supplements.some((item) => item.supplementType === 'NEXT_PLAN')
                        ? detail.supplements.filter((item) => item.supplementType === 'NEXT_PLAN').map((item) => (
                          <p key={item.id}>{item.content}</p>
                        ))
                        : <p>暂无已补录的下一步计划</p>}
                    </section>
                  )}
                  <div className="growth-review-section">
                    <h3>任务分类</h3>
                    <Table
                      rowKey="categoryCode"
                      size="small"
                      pagination={false}
                      dataSource={detail.categories}
                      locale={{ emptyText: '暂无分类统计' }}
                      columns={[
                        { title: '分类', dataIndex: 'categoryCode', key: 'categoryCode' },
                        { title: '任务', dataIndex: 'taskCount', key: 'taskCount', width: 72 },
                        { title: '完成', dataIndex: 'completedCount', key: 'completedCount', width: 72 }
                      ]}
                    />
                  </div>

                  <div className="growth-review-section">
                    <h3>每日趋势</h3>
                    <div className="growth-review-trends">
                      {detail.dailyTrends.map((trend) => (
                        <div className="growth-review-trend-row" key={trend.trendDate}>
                          <span>{trend.trendDate.slice(5)}</span>
                          <div className="growth-review-trend-bar" aria-hidden="true"><i style={{ width: `${trend.completionRate * 100}%` }} /></div>
                          <small>已完成 {trend.completedCount} / 总任务 {trend.taskTotalCount}</small>
                          <div className="growth-review-trend-metrics">
                            {/* 完成率沿用服务端快照，不能通过完成数/总数重新推算历史值。 */}
                            <strong>完成率 {formatRate(trend.completionRate)}</strong>
                            <span>积分 {formatPoints(trend.earnedPoints)} 分</span>
                            <span>待优化 {trend.pendingOptimizationCount} 项</span>
                            <span>暂停 {trend.pauseCount} 次</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="growth-review-section">
                    <h3>补录记录</h3>
                    <List
                      dataSource={detail.supplements}
                      locale={{ emptyText: '暂无补录' }}
                      renderItem={(item) => (
                        <List.Item>
                          <List.Item.Meta
                            title={supplementLabel(item.supplementType)}
                            description={item.content}
                          />
                          <span className="growth-review-editor">
                            {item.editorRole === 'PARENT' ? '家长' : '学生'}
                          </span>
                        </List.Item>
                      )}
                    />
                  </div>
                  </>}
                </>
              ) : <Empty description="请选择成长复盘" />}
            </section>
          </div>
        </ProCard>
      )}

      <Modal
        title="补录成长复盘"
        open={supplementOpen}
        footer={null}
        maskClosable={!submitting}
        onCancel={() => !submitting && setSupplementOpen(false)}
      >
        <Form form={form} layout="vertical" onFinish={(values) => void submitSupplement(values)}>
          <Form.Item
            name="supplementType"
            label="补录类型"
            rules={[{ required: true, message: '请选择补录类型' }]}
          >
            <Select options={SUPPLEMENT_TYPES} />
          </Form.Item>
          <Form.Item
            name="content"
            label="补录内容"
            rules={[{ required: true, whitespace: true, max: 1000, message: '请填写补录内容' }]}
          >
            <Input.TextArea rows={5} maxLength={1000} showCount />
          </Form.Item>
          <div className="form-actions">
            <Button actionKey="growth-reviews.growth-review-page.3" disabled={submitting} onClick={() => setSupplementOpen(false)}>取消</Button>
            <Button actionKey="growth-reviews.growth-review-page.4" type="primary" htmlType="submit" loading={submitting}>确认追加</Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}

function periodLabel(review: Pick<GrowthReviewSummary, 'periodType' | 'periodStart' | 'periodEnd'>): string {
  if (review.periodType === 'DAY') return review.periodStart;
  return `${review.periodStart} 至 ${review.periodEnd}`;
}

function formatRate(rate: number): string {
  return `${(rate * 100).toFixed(2)}%`;
}

function formatPoints(points: number): string {
  return points > 0 ? `+${points}` : String(points);
}

function supplementLabel(type: GrowthReviewSupplementType): string {
  return SUPPLEMENT_TYPES.find((item) => item.value === type)?.label ?? type;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '成长复盘加载失败';
}
