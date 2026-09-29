import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { beforeEach, expect, it, vi } from 'vitest';
import { GrowthReviewSubscriptionPanel } from './GrowthReviewSubscriptionPanel';
import { growthReviewSubscriptionApi as api } from './subscriptionApi';
vi.mock('./subscriptionApi', () => ({ growthReviewSubscriptionApi: { get: vi.fn(), set: vi.fn() } }));
const studentId = '1874244142494661102';
beforeEach(() => { vi.resetAllMocks(); vi.mocked(api.get).mockResolvedValue({ studentId, enabled: false, version: 0 }); });
it('读取偏好并以当前版本开启和取消', async () => {
  const user = userEvent.setup();
  vi.mocked(api.set).mockResolvedValueOnce({ studentId, enabled: true, version: 1 })
    .mockResolvedValueOnce({ studentId, enabled: false, version: 2 });
  render(<App><GrowthReviewSubscriptionPanel studentId={studentId} canEnable /></App>);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await user.click(screen.getByRole('switch'));
  await waitFor(() => expect(screen.getByRole('switch')).toBeChecked());
  expect(api.set).toHaveBeenLastCalledWith(studentId, { enabled: true, version: 0 });
  await user.click(screen.getByRole('switch'));
  await waitFor(() => expect(screen.getByRole('switch')).not.toBeChecked());
  expect(api.set).toHaveBeenLastCalledWith(studentId, { enabled: false, version: 1 });
});
it('无开启权限仍可取消既有订阅，取消后不可重新开启', async () => {
  vi.mocked(api.get).mockResolvedValue({ studentId, enabled: true, version: 4 });
  vi.mocked(api.set).mockResolvedValue({ studentId, enabled: false, version: 5 });
  render(<App><GrowthReviewSubscriptionPanel studentId={studentId} canEnable={false} /></App>);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await userEvent.click(screen.getByRole('switch'));
  await waitFor(() => expect(screen.getByRole('switch')).toBeDisabled());
  expect(api.set).toHaveBeenCalledWith(studentId, { enabled: false, version: 4 });
});
it('保存失败不重试写入，禁用开关并允许重新读取', async () => {
  vi.mocked(api.set).mockRejectedValue(new Error('当前状态不允许执行此操作'));
  render(<App><GrowthReviewSubscriptionPanel studentId={studentId} canEnable /></App>);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await userEvent.click(screen.getByRole('switch'));
  expect(await screen.findByText('当前状态不允许执行此操作')).toBeInTheDocument();
  expect(screen.getByRole('switch')).toBeDisabled();
  await userEvent.click(screen.getByRole('button', { name: '刷新订阅状态' }));
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  expect(api.set).toHaveBeenCalledTimes(1);
});
it('切换孩子后忽略旧读取响应', async () => {
  let finish!: (value: { studentId: string; enabled: boolean; version: number }) => void;
  vi.mocked(api.get).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = render(<App><GrowthReviewSubscriptionPanel studentId={studentId} canEnable /></App>);
  const next = '1874244142494661103';
  vi.mocked(api.get).mockResolvedValue({ studentId: next, enabled: false, version: 0 });
  view.rerender(<App><GrowthReviewSubscriptionPanel studentId={next} canEnable /></App>);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await act(async () => { finish({ studentId, enabled: true, version: 7 }); });
  expect(screen.getByRole('switch')).not.toBeChecked();
});

it('保存中禁止重复操作，切换孩子后旧保存结果不回填', async () => {
  let finish!: (value: { studentId: string; enabled: boolean; version: number }) => void;
  vi.mocked(api.set).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = render(<App><GrowthReviewSubscriptionPanel studentId={studentId} canEnable /></App>);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await userEvent.click(screen.getByRole('switch'));
  expect(screen.getByRole('switch')).toBeDisabled();
  expect(screen.getByRole('button', { name: '刷新订阅状态' })).toBeDisabled();
  await userEvent.click(screen.getByRole('switch'));
  expect(api.set).toHaveBeenCalledTimes(1);
  const next = '1874244142494661103';
  vi.mocked(api.get).mockResolvedValue({ studentId: next, enabled: false, version: 0 });
  view.rerender(<App><GrowthReviewSubscriptionPanel studentId={next} canEnable /></App>);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await act(async () => { finish({ studentId, enabled: true, version: 1 }); });
  expect(screen.getByRole('switch')).not.toBeChecked();
  expect(screen.getByRole('switch')).toBeEnabled();
});

it('首次读取失败时不允许写入，刷新成功后恢复操作', async () => {
  vi.mocked(api.get).mockRejectedValueOnce(new Error('读取失败'));
  render(<App><GrowthReviewSubscriptionPanel studentId={studentId} canEnable /></App>);
  expect(await screen.findByText('读取失败')).toBeInTheDocument();
  expect(screen.getByRole('switch')).toBeDisabled();
  expect(api.set).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole('button', { name: '刷新订阅状态' }));
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  expect(screen.queryByText('读取失败')).not.toBeInTheDocument();
});
