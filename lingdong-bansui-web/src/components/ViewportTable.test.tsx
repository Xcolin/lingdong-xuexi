import { act, render, screen, waitFor } from '@testing-library/react';
import { ConfigProvider, Table } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ViewportTable } from './ViewportTable';

afterEach(() => vi.restoreAllMocks());

describe('ViewportTable', () => {
  it('默认表格保留可用高度，少量数据及空数据不改变撑满模式',()=>{
    const view=render(<ViewportTable rowKey="id" dataSource={[{id:'1',name:'学生'}]} columns={[{title:'名称',dataIndex:'name'}]}/>);
    expect(view.container.querySelector('.viewport-table')).toHaveClass('viewport-table-fill');
    expect(view.container.querySelector('.viewport-table')?.getAttribute('style')).toContain('--table-body-height:');
    view.rerender(<ViewportTable dataSource={[]}/>);
    expect(view.container.querySelector('.viewport-table')).toHaveClass('viewport-table-fill');
    view.rerender(<ViewportTable dataSource={[]} scroll={{y:180}}/>);
    expect(view.container.querySelector('.viewport-table')).not.toHaveClass('viewport-table-fill');
  });
  it('client tables show ten rows and reset to the first page after changing size', async () => {
    const user = userEvent.setup();
    const rows = Array.from({ length: 60 }, (_, index) => ({ id: String(index + 1), name: `学员${index + 1}` }));
    const { container } = render(<ConfigProvider locale={zhCN}><ViewportTable rowKey="id" columns={[{ title: '名称', dataIndex: 'name' }]} dataSource={rows} pagination={{ pageSize: 20, hideOnSinglePage: true }} /></ConfigProvider>);
    expect(screen.getByText('共 60 条')).toBeInTheDocument();
    expect(container.querySelectorAll('tbody tr.ant-table-row')).toHaveLength(10);
    await user.click(screen.getByTitle('3'));
    expect(screen.getByText('学员21')).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: '页码' }));
    await user.click(screen.getByTitle('50 条/页'));
    expect(container.querySelectorAll('tbody tr.ant-table-row')).toHaveLength(50);
    expect(screen.getByText('学员1')).toBeInTheDocument();
    expect(container.querySelector('.ant-pagination-item-active')).toHaveAttribute('title', '1');
  });

  it('keeps explicit pagination false', () => {
    const { container } = render(<ViewportTable dataSource={[]} pagination={false} />);
    expect(container.querySelector('.ant-pagination')).toBeNull();
  });

  it('preserves explicit scrolling and the compound Table API', () => {
    const { container } = render(
      <ViewportTable<{ id: string; name: string }>
        rowKey="id"
        columns={[{ title: '名称', dataIndex: 'name' }]}
        dataSource={[{ id: '1', name: '学员' }]}
        scroll={{ x: 1200, y: 240 }}
      />,
    );
    expect(container.querySelector('.ant-table-body')).toHaveStyle({ maxHeight: '240px' });
    expect(container.querySelector('.ant-table-body table')).toHaveStyle({ width: '1200px' });
    expect(ViewportTable.Column).toBe(Table.Column);
    expect(ViewportTable.Summary).toBe(Table.Summary);
    expect(ViewportTable.SELECTION_ALL).toBe(Table.SELECTION_ALL);
  });

  it('remeasures when a retained page becomes visible at a new viewport height', async () => {
    let viewportBottom = 700;
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockImplementation(function (this: HTMLElement) {
      return (this.closest('[hidden]') ? [] : [{}]) as unknown as DOMRectList;
    });
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const bottom = this.classList.contains('workspace-page') ? viewportBottom : 200;
      return { top: 200, bottom, left: 0, right: 1000, width: 1000, height: bottom - 200, x: 0, y: 200, toJSON: () => ({}) };
    });
    const retainedTable = <ViewportTable dataSource={[]} pagination={false} />;
    const { container, rerender } = render(
      <section className="workspace-page" hidden>{retainedTable}</section>,
    );
    expect(container.querySelector('.ant-table-body')).toHaveStyle({ maxHeight: '320px' });
    rerender(<section className="workspace-page">{retainedTable}</section>);
    await waitFor(() => expect(container.querySelector('.ant-table-body')).toHaveStyle({ maxHeight: '476px' }));
    rerender(<section className="workspace-page" hidden>{retainedTable}</section>);
    viewportBottom = 900;
    act(() => window.dispatchEvent(new Event('resize')));
    rerender(<section className="workspace-page">{retainedTable}</section>);
    await waitFor(() => expect(container.querySelector('.ant-table-body')).toHaveStyle({ maxHeight: '676px' }));
  });
});
