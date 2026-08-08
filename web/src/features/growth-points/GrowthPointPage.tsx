import { useEffect, useState } from 'react';
import { Alert, App as AntdApp, Button, Empty, Form, Input, Modal, Select, Space, Statistic, Table, Tag, Tooltip } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import { RefreshCw, RotateCcw } from 'lucide-react';
import { growthPointApi } from './api';
import type {
  GrowthPointAccount,
  GrowthPointChangeType,
  GrowthPointLedger,
  GrowthPointLedgerPage,
  GrowthPointSourceType,
  GrowthPointStudentOption
} from './types';

const PAGE_SIZE = 20;

interface GrowthPointPageProps {
  correctionEnabled?: boolean;
}

interface CorrectionFormValues {
  reason: string;
}
const sourceLabels: Record<GrowthPointSourceType, string> = {
  FAMILY: '家庭',
  ORGANIZATION: '机构',
  TEACHER: '教师'
};
const changeLabels: Record<GrowthPointChangeType, string> = {
  TASK_REWARD: '任务奖励',
  REDEMPTION: '积分兑换',
  DORMANCY_CLEAR: '休眠清理',
  CORRECTION: '台账更正'
};

export function GrowthPointPage({ correctionEnabled = false }: GrowthPointPageProps) {
  const { message: messageApi } = AntdApp.useApp();
  const [students, setStudents] = useState<GrowthPointStudentOption[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<string>();
  const [account, setAccount] = useState<GrowthPointAccount | null>(null);
  const [ledgerPage, setLedgerPage] = useState<GrowthPointLedgerPage>({
    items: [], page: 1, pageSize: PAGE_SIZE, total: 0
  });
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [correctingLedger, setCorrectingLedger] = useState<GrowthPointLedger | null>(null);
  const [correctionSubmitting, setCorrectionSubmitting] = useState(false);
  const [correctionError, setCorrectionError] = useState<string | null>(null);

  useEffect(() => {
    void loadInitial();
  }, []);

  async function loadInitial(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const options = await growthPointApi.listStudents();
      setStudents(options);
      if (options.length === 0) {
        setSelectedStudentId(undefined);
        setAccount(null);
        setLedgerPage({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 });
        return;
      }
      setSelectedStudentId(options[0].studentId);
      await loadStudent(options[0].studentId, 1, false);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function loadStudent(studentId: string, page: number, manageLoading = true): Promise<void> {
    if (manageLoading) {
      setLoading(true);
    }
    setErrorMessage(null);
    try {
      const [nextAccount, nextLedgers] = await Promise.all([
        growthPointApi.account(studentId),
        growthPointApi.ledgers(studentId, page, PAGE_SIZE)
      ]);
      setAccount(nextAccount);
      setLedgerPage(nextLedgers);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      if (manageLoading) {
        setLoading(false);
      }
    }
  }

  function changeStudent(studentId: string): void {
    setSelectedStudentId(studentId);
    setAccount(null);
    setLedgerPage({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 });
    void loadStudent(studentId, 1);
  }

  function openCorrection(ledger: GrowthPointLedger): void {
    setCorrectionError(null);
    setCorrectingLedger(ledger);
  }

  function closeCorrection(): void {
    if (!correctionSubmitting) {
      setCorrectingLedger(null);
      setCorrectionError(null);
    }
  }

  async function submitCorrection(values: CorrectionFormValues): Promise<void> {
    if (!selectedStudentId || !correctingLedger) {
      return;
    }
    setCorrectionSubmitting(true);
    setCorrectionError(null);
    try {
      await growthPointApi.correct(selectedStudentId, correctingLedger.id, values.reason.trim());
      setCorrectingLedger(null);
      messageApi.success('积分已纠错，任务已回退至待审核');
      await loadStudent(selectedStudentId, ledgerPage.page, false);
    } catch (error) {
      setCorrectionError(toMessage(error));
    } finally {
      setCorrectionSubmitting(false);
    }
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <h1>积分台账</h1>
        <Space wrap>
          <Select
            aria-label="选择孩子"
            className="growth-point-student-select"
            value={selectedStudentId}
            placeholder="选择孩子"
            loading={loading && students.length === 0}
            disabled={loading}
            options={students.map((student) => ({
              value: student.studentId,
              label: student.studentName
            }))}
            onChange={changeStudent}
          />
          <Tooltip title="刷新积分">
            <Button
              aria-label="刷新积分"
              icon={<RefreshCw size={16} />}
              loading={loading}
              disabled={!selectedStudentId}
              onClick={() => selectedStudentId && void loadStudent(selectedStudentId, ledgerPage.page)}
            />
          </Tooltip>
        </Space>
      </div>

      {errorMessage && (
        <Alert
          type="error"
          showIcon
          message={errorMessage}
          action={<Button size="small" onClick={() => void loadInitial()}>重试</Button>}
        />
      )}

      {students.length === 0 && !loading ? (
        <div className="growth-point-empty"><Empty description="暂无可查询积分的孩子" /></div>
      ) : (
        <>
          <section className="growth-point-summary" aria-label="积分账户概览">
            <div>
              <span>{account?.studentName ?? '积分账户'}</span>
              <small>{account ? `更新于 ${formatDateTime(account.updatedAt)}` : '正在加载'}</small>
            </div>
            <Statistic title="累计积分" value={account?.totalPoints ?? 0} loading={loading && !account} />
            <Statistic title="可用积分" value={account?.availablePoints ?? 0} loading={loading && !account} />
          </section>

          <ProCard className="content-panel" bordered={false} title="积分明细">
            <Table<GrowthPointLedger>
              rowKey="id"
              loading={loading}
              dataSource={ledgerPage.items}
              locale={{ emptyText: '暂无积分变动' }}
              scroll={{ x: correctionEnabled ? 1010 : 900 }}
              pagination={{
                current: ledgerPage.page,
                pageSize: ledgerPage.pageSize,
                total: ledgerPage.total,
                showSizeChanger: false,
                onChange: (page) => selectedStudentId && void loadStudent(selectedStudentId, page)
              }}
              columns={[
                {
                  title: '发生时间', dataIndex: 'occurredAt', key: 'occurredAt', width: 170,
                  render: (value: string) => formatDateTime(value)
                },
                {
                  title: '变动事项', key: 'change', width: 240,
                  render: (_, item) => (
                    <div className="growth-point-event">
                      <strong>{ledgerTitle(item)}</strong>
                      <span>{changeLabels[item.changeType]}</span>
                      {decaySummary(item) && <span>{decaySummary(item)}</span>}
                    </div>
                  )
                },
                {
                  title: '来源', key: 'source', width: 130,
                  render: (_, item) => item.sourceType
                    ? <Tag>{sourceLabels[item.sourceType]}</Tag>
                    : '-'
                },
                {
                  title: '积分变动', dataIndex: 'amount', key: 'amount', width: 110,
                  render: (value: number) => (
                    <span className={value >= 0 ? 'point-positive' : 'point-negative'}>
                      {value > 0 ? '+' : ''}{value}
                    </span>
                  )
                },
                {
                  title: '可用变动', dataIndex: 'availableDelta', key: 'availableDelta', width: 110,
                  render: (value: number) => `${value > 0 ? '+' : ''}${value}`
                },
                {
                  title: '审核人', dataIndex: 'reviewerDisplayName', key: 'reviewerDisplayName', width: 130,
                  render: (value: string | null) => value ?? '-'
                },
                ...(correctionEnabled ? [{
                  title: '操作', key: 'action', width: 100, fixed: 'right' as const,
                  render: (_: unknown, item: GrowthPointLedger) => item.correctable ? (
                    <Tooltip title={item.correctionDeadline
                      ? `截止 ${formatDateTime(item.correctionDeadline)}` : '积分纠错'}>
                      <Button
                        type="link"
                        size="small"
                        icon={<RotateCcw size={15} />}
                        onClick={() => openCorrection(item)}
                      >纠错</Button>
                    </Tooltip>
                  ) : '-'
                }] : [])
              ]}
            />
          </ProCard>
        </>
      )}

      <Modal
        title="积分纠错"
        open={correctingLedger !== null}
        footer={null}
        destroyOnHidden
        maskClosable={!correctionSubmitting}
        onCancel={closeCorrection}
      >
        {correctingLedger && (
          <Form<CorrectionFormValues>
            layout="vertical"
            onFinish={(values) => void submitCorrection(values)}
          >
            <Alert
              type="warning"
              showIcon
              message={`提交后将扣除 ${correctingLedger.amount} 积分，任务回退至待审核。`}
              description="原积分台账会永久保留，纠错后需要重新审核本次打卡。"
            />
            {correctionError && <Alert className="growth-point-correction-error" type="error" showIcon message={correctionError} />}
            <Form.Item
              label="纠错原因"
              name="reason"
              rules={[
                { required: true, whitespace: true, message: '请填写纠错原因' },
                { max: 500, message: '纠错原因不能超过 500 个字符' }
              ]}
            >
              <Input.TextArea rows={4} maxLength={500} showCount placeholder="说明误操作情况和重新审核依据" />
            </Form.Item>
            <div className="form-actions">
              <Button disabled={correctionSubmitting} onClick={closeCorrection}>取消</Button>
              <Button type="primary" htmlType="submit" loading={correctionSubmitting}>确认纠错</Button>
            </div>
          </Form>
        )}
      </Modal>
    </div>
  );
}

function ledgerTitle(ledger: GrowthPointLedger): string {
  if (ledger.changeType === 'REDEMPTION' && ledger.sourceExchangeId && ledger.remark) {
    return ledger.remark;
  }
  return ledger.taskTitle ?? changeLabels[ledger.changeType];
}

function decaySummary(ledger: GrowthPointLedger): string | null {
  if (ledger.changeType !== 'TASK_REWARD' || !ledger.basePointsSnapshot || !ledger.streakDays) {
    return null;
  }
  return `连续第 ${ledger.streakDays} 天，基础 ${ledger.basePointsSnapshot} 分，衰减 ${ledger.decayPercent ?? 0}%`;
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short'
  }).format(new Date(value));
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '积分数据加载失败';
}
