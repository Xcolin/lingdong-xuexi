import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { GrowthReviewExportHistory } from './GrowthReviewExportHistory';
const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), downloadFile: vi.fn() }));
vi.mock('./exportApi', () => ({ growthReviewExportApi: api }));
const job = { id: '1874244142494650180', jobCode: 'EXP-测试', status: 'SUCCEEDED', totalRows: 1, processedRows: 1,
  templateName: '复盘模板', templateVersion: 'V1', requestedAt: '2026-09-08T12:00:00', requestReason: '留存' };
beforeEach(() => { vi.clearAllMocks(); api.list.mockResolvedValue({ items: [job], total: 1, page: 1, pageSize: 20 }); api.detail.mockResolvedValue(job); });
it('打开时查询指定孩子，查看公开详情', async () => {
  const user = userEvent.setup();
  render(<App><GrowthReviewExportHistory studentId="1874244142494650102" /></App>);
  expect(api.list).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '导出历史' }));
  await screen.findByText('EXP-测试');
  expect(api.list).toHaveBeenCalledWith('1874244142494650102', { page: 1, pageSize: 20, status: undefined });
  await user.click(screen.getByRole('button', { name: '详情-EXP-测试' }));
  await screen.findByText('复盘模板');
});
it('切换孩子后卸载历史，迟到响应不能展示旧孩子数据', async () => {
  let resolve!: (value: unknown) => void;
  api.list.mockReturnValue(new Promise(r => { resolve = r; }));
  const user = userEvent.setup();
  const view = render(<App><GrowthReviewExportHistory key="a" studentId="a" /></App>);
  await user.click(screen.getByRole('button', { name: '导出历史' }));
  view.rerender(<App><GrowthReviewExportHistory key="b" studentId="b" /></App>);
  resolve({ items: [job], total: 1, page: 1, pageSize: 20 });
  await waitFor(() => expect(screen.queryByText('EXP-测试')).not.toBeInTheDocument());
});
it('失败作业不可下载，查询失败显示错误且不保留旧列表', async () => {
  api.list.mockResolvedValue({ items: [{ ...job, status: 'FAILED' }], total: 1, page: 1, pageSize: 20 });
  const user = userEvent.setup();
  render(<App><GrowthReviewExportHistory studentId="a" /></App>);
  await user.click(screen.getByRole('button', { name: '导出历史' }));
  expect(await screen.findByRole('button', { name: '下载-EXP-测试' })).toBeDisabled();
  api.list.mockRejectedValue(new Error('读取权限已撤销'));
  await user.click(screen.getByRole('button', { name: '刷新导出历史' }));
  await screen.findByText('读取权限已撤销');
  expect(screen.queryByText('EXP-测试')).not.toBeInTheDocument();
});
