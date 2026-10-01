import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConfiguredModal } from './ConfiguredModal';
import { MenuConfigurationProvider } from '../app/MenuConfiguration';
import type { MenuNode } from '../api/menus';
const node = (suffix: string, name: string, status: 'ENABLED' | 'DISABLED' = 'ENABLED', sortOrder = 1): MenuNode => ({ id: suffix, code: `test.modal.${suffix}`, name, status, sortOrder, type: 'BUTTON', parentId: null, route: '/test', icon: null, permissionCode: null, version: '1' });
afterEach(() => { act(() => ConfiguredModal.destroyAll()); });
describe('ConfiguredModal', () => {
  it('keeps dynamic business labels when configuration still has the default name', () => {
    render(<MenuConfigurationProvider nodes={[node('ok', '确定'), node('cancel', '取消')]} refresh={async () => {}}><ConfiguredModal open actionPrefix="test.modal" okText="确认批准" cancelText="暂不操作" /></MenuConfigurationProvider>);
    expect(screen.getByRole('button', { name: '确认批准' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '暂不操作' })).toBeInTheDocument();
  });
  it('configures default footer names, visibility and order while retaining events', () => {
    const onOk = vi.fn();
    const { rerender } = render(<MenuConfigurationProvider nodes={[node('ok', '保存变更', 'ENABLED', 8), node('cancel', '取消', 'DISABLED')]} refresh={async () => {}}><ConfiguredModal open actionPrefix="test.modal" onOk={onOk} /></MenuConfigurationProvider>);
    fireEvent.click(screen.getByRole('button', { name: '保存变更' }));
    expect(onOk).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: '取消' })).toBeNull();
    expect(screen.getByRole('button', { name: '保存变更' }).parentElement).toHaveStyle({ order: '8' });
    rerender(<ConfiguredModal open actionPrefix="test.modal" footer={null} />);
    expect(screen.queryByRole('button', { name: '保存变更' })).toBeNull();
  });
  it('captures configuration for static confirms and preserves promise-based confirmation', async () => {
    let finish!: () => void;
    const onOk = vi.fn(() => new Promise<void>(resolve => { finish = resolve; }));
    render(<MenuConfigurationProvider nodes={[node('ok', '执行确认'), node('cancel', '取消', 'DISABLED')]} refresh={async () => {}}><div /></MenuConfigurationProvider>);
    act(() => { ConfiguredModal.confirm({ actionPrefix: 'test.modal', title: '静态操作', onOk }); });
    const button = await screen.findByRole('button', { name: '执行确认' });
    expect(screen.queryByRole('button', { name: '取消' })).toBeNull();
    fireEvent.click(button);
    expect(onOk).toHaveBeenCalledOnce();
    await waitFor(() => expect(button).toHaveClass('ant-btn-loading'));
    await act(async () => { finish(); });
    await waitFor(() => expect(screen.queryByRole('button', { name: '执行确认' })).toBeNull());
  });
});
