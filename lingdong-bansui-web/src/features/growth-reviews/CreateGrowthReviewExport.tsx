import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { useEffect, useRef, useState } from 'react';
import { Alert, App, Form, Input, Modal, Segmented, Select, Space } from 'antd';
import { FileDown, RefreshCw } from 'lucide-react';
import { growthReviewExportApi as api } from './exportApi';
import type { GrowthReviewPeriodType } from './types';

type Option = Awaited<ReturnType<typeof api.options>>[number];
interface Values { templateId: string; mode: 'SIMPLE' | 'DETAILED'; reason: string; periodType: GrowthReviewPeriodType; dateFrom: string; dateTo: string; }

/** 独立创建表单，所有对象标识保持字符串，模板资格由提交接口再次复核。 */
export function CreateGrowthReviewExport({ studentId, reviewId, onCreated }: {
  studentId: string; reviewId?: string; onCreated: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<Values>();
  const [open, setOpen] = useState(false);
  const [selection, setSelection] = useState<'SINGLE' | 'RANGE'>('SINGLE');
  const [selectedReviewId, setSelectedReviewId] = useState<string>();
  const [options, setOptions] = useState<Option[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();
  const [retry, setRetry] = useState(0);
  const templateId = Form.useWatch('templateId', form);
  const template = options.find(item => item.id === templateId);
  const epoch = useRef(0);
  const lock = useRef(false);
  useEffect(() => () => { epoch.current++; }, []);
  useEffect(() => {
    if (!open) return;
    const request = ++epoch.current;
    setLoading(true); setError(undefined); setOptions([]);
    void api.options(studentId).then(items => {
      if (request !== epoch.current) return;
      setOptions(items);
      form.setFieldsValue({ templateId: items[0]?.id, mode: items[0]?.modes[0] });
    }).catch(cause => { if (request === epoch.current) setError(errorText(cause)); })
      .finally(() => { if (request === epoch.current) setLoading(false); });
    return () => { epoch.current++; };
  }, [open, studentId, form, retry]);

  async function submit(values: Values) {
    if (lock.current || !template || !template.modes.includes(values.mode)) return;
    if (selection === 'SINGLE' && !selectedReviewId) return;
    lock.current = true; setSubmitting(true); setError(undefined);
    const request = epoch.current;
    const common = { studentId, templateId: template.id, mode: values.mode, reason: values.reason.trim() };
    try {
      await api.create(selection === 'SINGLE' ? { ...common, reviewId: selectedReviewId! }
        : { ...common, periodType: values.periodType, dateFrom: values.dateFrom, dateTo: values.dateTo });
      if (request !== epoch.current) return;
      setOpen(false); message.success('复盘导出已入队'); onCreated();
    } catch (cause) { if (request === epoch.current) setError(errorText(cause)); }
    finally { lock.current = false; if (request === epoch.current) setSubmitting(false); }
  }

  return <>
    <Button actionKey="growth-reviews.create-growth-review-export.1" icon={<FileDown size={16} />} onClick={() => {
      setSelectedReviewId(reviewId); setSelection(reviewId ? 'SINGLE' : 'RANGE'); setOpen(true);
    }}>导出 PDF</Button>
    <Modal title="导出复盘 PDF" open={open} footer={null} destroyOnHidden width="min(640px, 92vw)"
      closable={!submitting} maskClosable={!submitting} keyboard={!submitting}
      onCancel={() => { if (!submitting) setOpen(false); }}>
      <Form form={form} layout="vertical" preserve={false} initialValues={{ periodType: 'DAY' }} onFinish={submit}>
        <Form.Item label="导出范围">
          <Segmented value={selection} disabled={submitting} options={[
            { label: '当前复盘', value: 'SINGLE', disabled: !selectedReviewId }, { label: '日期区间', value: 'RANGE' }
          ]} onChange={value => setSelection(value as 'SINGLE' | 'RANGE')} />
        </Form.Item>
        {selection === 'RANGE' && <>
          <Form.Item label="复盘周期" name="periodType" rules={[{ required: true }]}>
            <Select disabled={submitting} options={[{ label: '日报', value: 'DAY' }, { label: '周报', value: 'WEEK' }, { label: '月报', value: 'MONTH' }]} />
          </Form.Item>
          <Form.Item label="开始日期" name="dateFrom" rules={[{ required: true, message: '请选择开始日期' }]}>
            <Input type="date" disabled={submitting} />
          </Form.Item>
          <Form.Item label="结束日期" name="dateTo" dependencies={['dateFrom']} rules={[
            { required: true, message: '请选择结束日期' },
            ({ getFieldValue }) => ({ validator(_, value) {
              return value && getFieldValue('dateFrom') && value < getFieldValue('dateFrom')
                ? Promise.reject(new Error('结束日期不能早于开始日期')) : Promise.resolve();
            } })
          ]}><Input type="date" disabled={submitting} /></Form.Item>
        </>}
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
        {!loading && options.length === 0 && <Space style={{ marginBottom: 12 }} wrap>
          <span>暂无可用复盘模板</span><Button actionKey="growth-reviews.create-growth-review-export.2" aria-label="重试模板" icon={<RefreshCw size={16} />} onClick={() => setRetry(value => value + 1)} />
        </Space>}
        <Form.Item label="模板版本" name="templateId" rules={[{ required: true, message: '请选择模板' }]}>
          <Select loading={loading} disabled={submitting || loading} options={options.map(item => ({ value: item.id, label: `${item.templateName} · ${item.version}` }))}
            onChange={id => form.setFieldValue('mode', options.find(item => item.id === id)?.modes[0])} />
        </Form.Item>
        <Form.Item label="报告模式" name="mode" rules={[{ required: true, message: '请选择模式' }]}>
          <Select disabled={submitting || loading} options={template?.modes.map(value => ({ value, label: value === 'SIMPLE' ? '简洁版' : '详细版' })) ?? []} />
        </Form.Item>
        <Form.Item label="导出原因" name="reason" rules={[{ required: true, whitespace: true, max: 500, message: '请填写导出原因，最多500字' }]}>
          <Input.TextArea rows={3} maxLength={500} showCount disabled={submitting} />
        </Form.Item>
        <div className="form-actions"><Button actionKey="growth-reviews.create-growth-review-export.3" disabled={submitting} onClick={() => setOpen(false)}>取消</Button>
          <Button actionKey="GROWTH_REVIEW_PDF_EXPORT" type="primary" htmlType="submit" loading={submitting} disabled={loading || !template}>提交导出</Button></div>
      </Form>
    </Modal>
  </>;
}
function errorText(cause: unknown): string { return cause instanceof Error ? cause.message : '复盘导出未能完成'; }
