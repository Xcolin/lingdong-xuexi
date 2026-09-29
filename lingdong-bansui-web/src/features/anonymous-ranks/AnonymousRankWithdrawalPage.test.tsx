import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { AnonymousRankWithdrawalPage } from './AnonymousRankWithdrawalPage';
import { rankApi as api } from './api';
vi.mock('./api', () => ({ rankApi: { withdrawals: vi.fn(), set: vi.fn() } }));
it('无查看能力时只展示本人撤回清单并发送版本', async () => {
  vi.mocked(api.withdrawals).mockResolvedValue([{ studentId: '1874244142494690001', classId: '1874244142494690002', version: 7 }]);
  vi.mocked(api.set).mockResolvedValue({ enabled: false, version: 8 });
  render(<AnonymousRankWithdrawalPage />);
  await userEvent.click(await screen.findByRole('button', { name: '撤回授权' }));
  await waitFor(() => expect(screen.queryByRole('button', { name: '撤回授权' })).not.toBeInTheDocument());
  expect(api.set).toHaveBeenCalledWith('1874244142494690001', '1874244142494690002', { enabled: false, version: 7 });
  expect(screen.queryByRole('switch')).not.toBeInTheDocument();
});
