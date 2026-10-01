import { act, render, waitFor } from '@testing-library/react';
import { Table } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ViewportTable } from './ViewportTable';

afterEach(() => vi.restoreAllMocks());

describe('ViewportTable', () => {
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
