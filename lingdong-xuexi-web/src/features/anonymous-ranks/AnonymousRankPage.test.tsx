import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { AnonymousRankPage } from './AnonymousRankPage';
import { rankApi as api } from './api';
import { StrictMode } from 'react';
vi.mock('./api', () => ({ rankApi: { students: vi.fn(), classes: vi.fn(), preference: vi.fn(), set: vi.fn(), ranking: vi.fn() } }));
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.students).mockResolvedValue([{ studentId: '1874244142494690001', studentName: '我的孩子' }]);
  vi.mocked(api.classes).mockResolvedValue([{ classId: '1874244142494690002', className: '当前班级' }]);
  vi.mocked(api.preference).mockResolvedValue({ enabled: false, version: 0 });
  vi.mocked(api.ranking).mockResolvedValue([{ rank: 1, points: 20 }, { rank: 1, points: 20 }, { rank: 3, points: 0 }]);
});
it('默认不读排行，主动开启后显示并列名次，关闭后清除结果', async () => {
  vi.mocked(api.set).mockResolvedValueOnce({ enabled: true, version: 1 }).mockResolvedValueOnce({ enabled: false, version: 2 });
  render(<StrictMode><AnonymousRankPage /></StrictMode>);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  expect(api.ranking).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole('switch'));
  await waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(4));
  expect(screen.getAllByText('20')).toHaveLength(2);
  expect(api.set).toHaveBeenCalledWith('1874244142494690001', '1874244142494690002', { enabled: true, version: 0 });
  await userEvent.click(screen.getByRole('switch'));
  await waitFor(() => expect(screen.queryByRole('table')).not.toBeInTheDocument());
});
it('版本冲突后停止写入并要求刷新', async () => {
  vi.mocked(api.set).mockRejectedValue(new Error('查看偏好已变化，请刷新后重试'));
  render(<AnonymousRankPage />);
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await userEvent.click(screen.getByRole('switch'));
  expect(await screen.findByText('查看偏好已变化，请刷新后重试')).toBeInTheDocument();
  expect(screen.getByRole('switch')).toBeDisabled();
});
it('关系撤销或功能关闭后清除榜单，仍允许撤回已开启偏好', async () => {
  vi.mocked(api.preference).mockResolvedValue({ enabled: true, version: 3 });
  vi.mocked(api.ranking).mockRejectedValue(new Error('当前账号无权查看该班级排行'));
  vi.mocked(api.set).mockResolvedValue({ enabled: false, version: 4 });
  render(<AnonymousRankPage />);
  expect(await screen.findByText('当前账号无权查看该班级排行')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('switch'));
  await waitFor(() => expect(api.set).toHaveBeenCalledWith(expect.any(String), expect.any(String), { enabled: false, version: 3 }));
});
it('空榜单显示空态，不增加任何身份列', async () => {
  vi.mocked(api.preference).mockResolvedValue({ enabled: true, version: 1 });
  vi.mocked(api.ranking).mockResolvedValue([]);
  render(<AnonymousRankPage />);
  expect(await screen.findByText('暂无排行数据')).toBeInTheDocument();
  expect(screen.getAllByRole('columnheader').map(item => item.textContent)).toEqual(['名次', '积分']);
});
it('切换孩子后丢弃旧偏好响应，不用旧范围查询排名', async () => {
  let finish!: (value: { enabled: boolean; version: number }) => void;
  vi.mocked(api.students).mockResolvedValue([
    { studentId: '1874244142494690001', studentName: '我的孩子' },
    { studentId: '1874244142494690011', studentName: '另一个孩子' }
  ]);
  vi.mocked(api.preference).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  render(<AnonymousRankPage />);
  await waitFor(() => expect(api.preference).toHaveBeenCalled());
  await userEvent.click(screen.getByRole('combobox', { name: '选择孩子' }));
  await userEvent.click(await screen.findByText('另一个孩子'));
  await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  await act(async () => finish({ enabled: true, version: 6 }));
  expect(screen.getByRole('switch')).not.toBeChecked();
  expect(api.ranking).not.toHaveBeenCalled();
});
