import { useState } from 'react';
import { fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { WorkspaceTabs } from './WorkspaceTabs';

const pages = [{ path: '/dashboard', label: '工作台' }, { path: '/users', label: '用户管理' }, { path: '/iam', label: '角色权限' }, { path: '/organizations', label: '组织管理' }];
function FormPage({ label = '筛选词' }: { label?: string }) { const [text, setText] = useState(''); return <input aria-label={label} value={text} onChange={e => setText(e.target.value)} />; }
function Harness({ allowUsers = true }: { allowUsers?: boolean }) {
  const navigate = useNavigate();
  return <><nav>{pages.map(page => <button key={page.path} onClick={() => navigate(page.path)}>打开{page.label}</button>)}</nav>
    <WorkspaceTabs pages={pages.filter(p => allowUsers || p.path !== '/users')}>
      <Routes><Route path="/dashboard" element={<h1>首页内容</h1>} /><Route path="/users" element={<FormPage />} /><Route path="/iam" element={<><h1>权限内容</h1><FormPage label="权限筛选词" /><button onClick={() => Modal.confirm({ title: '确认旧页面操作' })}>打开确认</button></>} /><Route path="/organizations" element={<h1>组织内容</h1>} /></Routes>
    </WorkspaceTabs></>;
}
describe('多页签工作区', () => {
  it('重复打开不重复建页签，切换保留输入，关闭后重新打开重置状态', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/dashboard']}><Harness /></MemoryRouter>);
    await user.click(screen.getByText('打开用户管理'));
    await user.type(screen.getByLabelText('筛选词'), '保留条件');
    await user.click(screen.getByText('打开角色权限'));
    await user.click(screen.getByRole('tab', { name: '用户管理' }));
    expect(screen.getByLabelText('筛选词')).toHaveValue('保留条件');
    await user.click(screen.getByText('打开用户管理'));
    expect(screen.getAllByRole('tab', { name: '用户管理' })).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: '关闭用户管理' }));
    expect(screen.queryByRole('tab', { name: '用户管理' })).not.toBeInTheDocument();
    await user.click(screen.getByText('打开用户管理'));
    expect(screen.getByLabelText('筛选词')).toHaveValue('');
  });
  it('右键关闭左右页签，并保留工作台；关闭全部回到首页', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/dashboard']}><Harness /></MemoryRouter>);
    for (const name of ['用户管理', '角色权限', '组织管理']) await user.click(screen.getByText('打开' + name));
    fireEvent.contextMenu(screen.getByRole('tab', { name: '角色权限' }));
    await user.click(await screen.findByRole('menuitem', { name: '关闭左侧' }));
    expect(screen.queryByRole('tab', { name: '用户管理' })).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '工作台' })).toBeInTheDocument();
    fireEvent.contextMenu(screen.getByRole('tab', { name: '角色权限' }));
    await user.click(await screen.findByRole('menuitem', { name: '关闭右侧' }));
    expect(screen.queryByRole('tab', { name: '组织管理' })).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '角色权限' })).toHaveAttribute('aria-selected', 'true');
    await user.click(screen.getByRole('button', { name: '页签操作' }));
    await user.click(await screen.findByRole('menuitem', { name: '关闭全部' }));
    expect(within(screen.getByRole('tablist')).getAllByRole('tab')).toHaveLength(1);
    expect(screen.getByRole('heading', { name: '首页内容' })).toBeVisible();
  });
  it('权限撤销立即移除缓存页并返回工作台', async () => {
    const user = userEvent.setup();
    const view = render(<MemoryRouter initialEntries={['/dashboard']}><Harness /></MemoryRouter>);
    await user.click(screen.getByText('打开用户管理'));
    view.rerender(<MemoryRouter initialEntries={['/dashboard']}><Harness allowUsers={false} /></MemoryRouter>);
    expect(screen.queryByRole('tab', { name: '用户管理' })).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '首页内容' })).toBeVisible();
  });
  it('撤销当前页权限不会卸载其他合法页面的输入状态', async () => {
    const user = userEvent.setup();
    const view = render(<MemoryRouter initialEntries={['/iam']}><Harness /></MemoryRouter>);
    await user.type(screen.getByLabelText('权限筛选词'), '保留权限筛选');
    await user.click(screen.getByText('打开用户管理'));
    view.rerender(<MemoryRouter><Harness allowUsers={false} /></MemoryRouter>);
    await user.click(screen.getByRole('tab', { name: '角色权限' }));
    expect(screen.getByLabelText('权限筛选词')).toHaveValue('保留权限筛选');
  });
  it('导航到其他页时清理旧页面的静态确认框', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/iam']}><Harness /></MemoryRouter>);
    await user.click(screen.getByText('打开确认'));
    await waitFor(() => expect(screen.getByRole('dialog', { name: '确认旧页面操作' })).toBeVisible());
    await user.click(screen.getByText('打开工作台'));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '确认旧页面操作' })).not.toBeInTheDocument());
  });
});
