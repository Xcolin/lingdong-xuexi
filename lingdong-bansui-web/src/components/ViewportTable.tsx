import type { CSSProperties } from 'react';
import { forwardRef, useLayoutEffect, useRef, useState } from 'react';
import { Table, type TableProps } from 'antd';
import type { TableRef } from 'antd/es/table';
import { createPagination, DEFAULT_PAGE_SIZE } from './pagination';

/** A Table with a viewport-sized scroll body; explicit scroll settings take priority. */
const AdaptiveTable = forwardRef<TableRef, TableProps<Record<string, unknown>>>(
  function AdaptiveTable({ scroll, ...props }, ref) {
    const [clientPage, setClientPage] = useState(1);
    const [clientPageSize, setClientPageSize] = useState(DEFAULT_PAGE_SIZE);
    const paginationConfig = props.pagination === false ? false : props.pagination ?? {};
    const pagination = paginationConfig === false ? false : createPagination({
      ...paginationConfig,
      current: paginationConfig.current ?? clientPage,
      pageSize: paginationConfig.current === undefined ? clientPageSize : paginationConfig.pageSize ?? DEFAULT_PAGE_SIZE,
      onChange: (page, pageSize) => {
        setClientPage(page); setClientPageSize(pageSize);
        paginationConfig.onChange?.(page, pageSize);
      },
    });
    const containerRef = useRef<HTMLDivElement>(null);
    const [bodyHeight, setBodyHeight] = useState(320);

    useLayoutEffect(() => {
      const container = containerRef.current;
      if (!container || scroll?.y !== undefined) return;
      const page = container.closest<HTMLElement>('.workspace-page');
      let frame = 0;
      const measure = () => {
        if (!container.getClientRects().length) return;
        const body = container.querySelector<HTMLElement>('.ant-table-body');
        const top = body?.getBoundingClientRect().top ?? container.getBoundingClientRect().top + 48;
        const overlay = container.closest<HTMLElement>('.ant-modal-body, .ant-drawer-body');
        const panel = container.closest<HTMLElement>('.management-detail-pane, .ant-pro-card-body');
        const panelBottom = panel && ['auto','scroll','hidden'].includes(getComputedStyle(panel).overflowY) ? panel.getBoundingClientRect().bottom : Infinity;
        const bottom = Math.min(panelBottom, overlay?.getBoundingClientRect().bottom ?? Infinity, page?.getBoundingClientRect().bottom ?? window.innerHeight);
        // Keep pagination, summary and footer reachable below the scrolling rows.
        const pagination = container.querySelector<HTMLElement>('.ant-pagination');
        const footer = container.querySelector<HTMLElement>('.ant-table-footer');
        const summary = container.querySelector<HTMLElement>('.ant-table-summary');
        let trailing = 0;
        let parent = container.parentElement;
        while (parent && parent !== page && !parent.classList.contains('page-stack')) {
          trailing += Number.parseFloat(getComputedStyle(parent).paddingBottom) || 0;
          const actions = Array.from(parent.children).find(child => child.classList.contains('panel-footer'));
          if (actions) trailing += actions.getBoundingClientRect().height + (Number.parseFloat(getComputedStyle(actions).marginTop) || 0);
          parent = parent.parentElement;
        }
        const reserved = (pagination ? pagination.getBoundingClientRect().height + 32 : 0)
          + (footer?.getBoundingClientRect().height ?? 0)
          + (summary?.getBoundingClientRect().height ?? 0) + trailing + 24;
        // A minimum usable body is intentional: short windows scroll the page too.
        const next = Math.max(120, Math.floor(bottom - top - reserved - (page?.scrollTop ?? 0)));
        setBodyHeight(previous => previous === next ? previous : next);
      };
      const schedule = () => {
        cancelAnimationFrame(frame);
        frame = requestAnimationFrame(measure);
      };
      const observer = new ResizeObserver(schedule);
      // Ancestors include filter panels: changes to their height move this table.
      let ancestor: HTMLElement | null = container;
      while (ancestor) {
        observer.observe(ancestor);
        if (ancestor === page) break;
        ancestor = ancestor.parentElement;
      }
      container.querySelectorAll('.ant-table-thead, .ant-pagination, .ant-table-footer, .ant-table-summary')
        .forEach(element => observer.observe(element));
      const visibility = new MutationObserver(schedule);
      if (page) visibility.observe(page, { attributes: true, attributeFilter: ['hidden', 'style', 'class'] });
      window.addEventListener('resize', schedule);
      measure();
      return () => {
        observer.disconnect();
        visibility.disconnect();
        window.removeEventListener('resize', schedule);
        cancelAnimationFrame(frame);
      };
    }, [scroll?.y, props.pagination, props.dataSource]);

    return (
      <div ref={containerRef} className={`viewport-table${scroll?.y === undefined ? ' viewport-table-fill' : ''}`} style={{'--table-body-height': `${bodyHeight}px`} as CSSProperties}>
        <Table size="small" {...props} pagination={pagination} onChange={(next, filters, sorter, extra) => {
          props.onChange?.({ ...next, current: next.pageSize === (paginationConfig && paginationConfig.pageSize || clientPageSize) ? next.current : 1 }, filters, sorter, extra);
        }} ref={ref} scroll={{ x: 'max-content', y: bodyHeight, ...scroll }} />
      </div>
    );
  },
);

// Preserve generic inference, refs and the public Table compound-component API.
export const ViewportTable = Object.assign(AdaptiveTable, {
  SELECTION_COLUMN: Table.SELECTION_COLUMN,
  EXPAND_COLUMN: Table.EXPAND_COLUMN,
  SELECTION_ALL: Table.SELECTION_ALL,
  SELECTION_INVERT: Table.SELECTION_INVERT,
  SELECTION_NONE: Table.SELECTION_NONE,
  Column: Table.Column,
  ColumnGroup: Table.ColumnGroup,
  Summary: Table.Summary,
}) as typeof Table;

export default ViewportTable;
