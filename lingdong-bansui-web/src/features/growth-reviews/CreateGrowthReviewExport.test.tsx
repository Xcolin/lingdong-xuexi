import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { CreateGrowthReviewExport } from './CreateGrowthReviewExport';
const api = vi.hoisted(() => ({ options: vi.fn(), create: vi.fn() }));
vi.mock('./exportApi', () => ({ growthReviewExportApi: api }));
beforeEach(() => {
  vi.clearAllMocks();
  api.options.mockResolvedValue([{ id: '1874244142494650105', templateName: '复盘模板', version: 'V1', modes: ['SIMPLE'] }]);
  api.create.mockResolvedValue({ id: '1874244142494650180' });
});
it('单份提交使用服务端模板与模式，成功通知历史刷新', async () => {
  const done = vi.fn(); const user = userEvent.setup();
  render(<App><CreateGrowthReviewExport studentId="1874244142494650102" reviewId="1874244142494650151" onCreated={done} /></App>);
  await user.click(screen.getByRole('button', { name: '导出 PDF' }));
  await screen.findByText('复盘模板 · V1');
  await user.type(screen.getByLabelText('导出原因'), ' 留存复盘 ');
  await user.click(screen.getByRole('button', { name: '提交导出' }));
  await screen.findByText('复盘导出已入队');
  expect(api.create).toHaveBeenCalledWith({ studentId: '1874244142494650102', reviewId: '1874244142494650151', templateId: '1874244142494650105', mode: 'SIMPLE', reason: '留存复盘' });
  expect(done).toHaveBeenCalledOnce();
});
it('没有模板不能提交，支持重试', async () => {
  api.options.mockResolvedValue([]); const user = userEvent.setup();
  render(<App><CreateGrowthReviewExport studentId="a" onCreated={vi.fn()} /></App>);
  await user.click(screen.getByRole('button', { name: '导出 PDF' }));
  await screen.findByText('暂无可用复盘模板');
  expect(screen.getByRole('button', { name: '提交导出' })).toBeDisabled();
});
it('区间拒绝倒置日期，正确请求不携带单份标识', async () => {
  const user = userEvent.setup();
  render(<App><CreateGrowthReviewExport studentId="1874244142494650102" onCreated={vi.fn()} /></App>);
  await user.click(screen.getByRole('button', { name: '导出 PDF' }));
  await screen.findByText('复盘模板 · V1');
  fireEvent.change(screen.getByLabelText('开始日期'), { target: { value: '2026-09-08' } });
  fireEvent.change(screen.getByLabelText('结束日期'), { target: { value: '2026-09-01' } });
  await user.type(screen.getByLabelText('导出原因'), '区间留存');
  await user.click(screen.getByRole('button', { name: '提交导出' }));
  await screen.findByText('结束日期不能早于开始日期');
  expect(api.create).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('结束日期'), { target: { value: '2026-09-09' } });
  await user.click(screen.getByRole('button', { name: '提交导出' }));
  await screen.findByText('复盘导出已入队');
  expect(api.create).toHaveBeenCalledWith({ studentId: '1874244142494650102', periodType: 'DAY',
    dateFrom: '2026-09-08', dateTo: '2026-09-09', templateId: '1874244142494650105', mode: 'SIMPLE', reason: '区间留存' });
});
