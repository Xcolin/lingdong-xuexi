import type { PaginationProps } from 'antd';
import type { TablePaginationConfig } from 'antd/es/table/interface';

export const DEFAULT_PAGE_SIZE = 10;
export const PAGE_SIZE_OPTIONS = [10, 20, 50];

/** Shared controls for table footers and standalone pickers. */
export function createPaginationControl(config: PaginationProps = {}): PaginationProps {
  const { onChange, ...rest } = config;
  let previousSize = config.pageSize ?? config.defaultPageSize ?? DEFAULT_PAGE_SIZE;
  return {
    ...rest,
    defaultPageSize: DEFAULT_PAGE_SIZE,
    pageSizeOptions: PAGE_SIZE_OPTIONS,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: total => `共 ${total} 条`,
    hideOnSinglePage: false,
    onChange: (page, pageSize) => {
      const nextPage = pageSize === previousSize ? page : 1;
      previousSize = pageSize;
      onChange?.(nextPage, pageSize);
    },
  };
}

export function createPagination(config: TablePaginationConfig = {}): TablePaginationConfig {
  return { ...createPaginationControl(config), position: ['bottomRight'] };
}
