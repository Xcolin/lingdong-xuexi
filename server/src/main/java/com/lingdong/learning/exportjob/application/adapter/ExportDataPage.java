package com.lingdong.learning.exportjob.application.adapter;

import java.util.List;
import java.util.Map;

/** 一批按主键稳定排序的导出数据。 */
public record ExportDataPage(
        List<Map<String, Object>> rows,
        Long nextCursor,
        boolean hasMore
) {
    public ExportDataPage {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
