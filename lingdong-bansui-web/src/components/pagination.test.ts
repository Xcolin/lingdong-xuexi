import { describe, expect, it, vi } from 'vitest';
import { createPagination, createPaginationControl } from './pagination';

describe('shared pagination', () => {
  it('uses the audit defaults and a right table footer', () => {
    const config = createPagination({ hideOnSinglePage: true, showSizeChanger: false, pageSizeOptions: [100] });
    expect(config).toMatchObject({ defaultPageSize: 10, pageSizeOptions: [10, 20, 50], showQuickJumper: true, showSizeChanger: true, hideOnSinglePage: false, position: ['bottomRight'] });
    expect(config.showTotal?.(120, [1, 10])).toBe('共 120 条');
  });
  it('resets size changes and keeps normal page navigation for standalone and table controls', () => {
    for (const factory of [createPagination, createPaginationControl]) {
      const change = vi.fn();
      const config = factory({ current: 4, pageSize: 10, onChange: change });
      config.onChange?.(4, 50);
      expect(change).toHaveBeenLastCalledWith(1, 50);
      config.onChange?.(2, 50);
      expect(change).toHaveBeenLastCalledWith(2, 50);
    }
  });
});
