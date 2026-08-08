import { useCallback, useEffect, useMemo, useState } from 'react';
import { ProCard } from '@ant-design/pro-components';
import {
  App, Button, Empty, Form, Input, List, Modal, Progress, Segmented,
  Select, Space, Statistic, Table, Tag, Tooltip
} from 'antd';
import { FilePenLine, RefreshCw } from 'lucide-react';
import { growthReviewApi } from './api';
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

export function GrowthReviewPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<AddGrowthReviewSupplementInput>();
  const [students, setStudents] = useState<GrowthReviewStudentOption[]>([]);
  const [studentId, setStudentId] = useState<string>();
  const [periodType, setPeriodType] = useState<GrowthReviewPeriodType>('DAY');
  const [reviews, setReviews] = useState<GrowthReviewSummary[]>([]);
  const [detail, setDetail] = useState<GrowthReviewDetail>();
  const [loading, setLoading] = useState(false);
  const [supplementOpen, setSupplementOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    void growthReviewApi.listStudents()
      .then((items) => {
        setStudents(items);
        setStudentId((current) => current ?? items[0]?.studentId);
      })
      .catch((error) => message.error(errorMessage(error)));
  }, [message]);

  const loadReviews = useCallback(async () => {
    if (!studentId) {
      setReviews([]);
      setDetail(undefined);
      return;
    }
    setLoading(true);
    try {
      const page = await growthReviewApi.list(studentId, periodType, 1, PAGE_SIZE);
      setReviews(page.items);
      const first = page.items[0];
      setDetail(first ? await growthReviewApi.detail(studentId, first.reviewId) : undefined);
    } catch (error) {
      setReviews([]);
      setDetail(undefined);
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [message, periodType, studentId]);

  useEffect(() => {
    void loadReviews();
  }, [loadReviews]);

  async function selectReview(review: GrowthReviewSummary): Promise<void> {
    if (!studentId || review.reviewId === detail?.reviewId) return;
    setLoading(true);
    try {
      setDetail(await growthReviewApi.detail(studentId, review.reviewId));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function submitSupplement(values: AddGrowthReviewSupplementInput): Promise<void> {
    if (!studentId || !detail) return;
    setSubmitting(true);
    try {
      await growthReviewApi.supplement(studentId, detail.reviewId, {
        supplementType: values.supplementType,
        content: values.content.trim()
      });
      setDetail(await growthReviewApi.detail(studentId, detail.reviewId));
      setSupplementOpen(false);
      form.resetFields();
      message.success('复盘补录已追加');
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  const selectedStudentName = useMemo(
    () => students.find((item) => item.studentId === studentId)?.studentName,
    [studentId, students]
  );

  return (
    <div className="page-stack growth-review-page">
      <header className="page-heading">
        <h1>成长复盘</h1>
        <Space wrap>
          <Select
            aria-label="选择孩子"
            className="growth-review-student-select"
            value={studentId}
            placeholder="选择孩子"
            options={students.map((student) => ({
              label: student.studentName, value: student.studentId
            }))}
            onChange={setStudentId}
          />
          <Tooltip title="刷新复盘">
            <Button
              aria-label="刷新复盘"
              icon={<RefreshCw size={16} />}
              loading={loading}
              onClick={() => void loadReviews()}
            />
          </Tooltip>
        </Space>
      </header>

      <div className="growth-review-toolbar">
        <Segmented
          aria-label="复盘周期"
          value={periodType}
          options={PERIOD_OPTIONS}
          onChange={(value) => setPeriodType(value as GrowthReviewPeriodType)}
        />
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
                    render: (value: number) => <strong className="point-positive">+{value}</strong>
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
                      <Button
                        icon={<FilePenLine size={16} />}
                        onClick={() => setSupplementOpen(true)}
                      >补录复盘</Button>
                    )}
                  </div>

                  <div className="growth-review-metrics">
                    <Statistic title="完成率" value={formatRate(detail.completionRate)} />
                    <Statistic title="累计获取" value={detail.earnedPoints} suffix="分" />
                    <Statistic title="进行中" value={detail.inProgressCount} suffix="项" />
                    <Statistic title="情绪暂停" value={detail.pauseCount} suffix="次" />
                  </div>
                  <Progress
                    percent={Math.round(detail.completionRate * 100)}
                    showInfo={false}
                    strokeColor="#167c5a"
                    trailColor="#e5ece8"
                  />

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
                          <div><i style={{ width: `${Math.max(2, trend.completionRate * 100)}%` }} /></div>
                          <strong>{trend.completedCount}/{trend.taskTotalCount}</strong>
                          <small>+{trend.earnedPoints} 分</small>
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
            <Button disabled={submitting} onClick={() => setSupplementOpen(false)}>取消</Button>
            <Button type="primary" htmlType="submit" loading={submitting}>确认追加</Button>
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

function supplementLabel(type: GrowthReviewSupplementType): string {
  return SUPPLEMENT_TYPES.find((item) => item.value === type)?.label ?? type;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '成长复盘加载失败';
}
