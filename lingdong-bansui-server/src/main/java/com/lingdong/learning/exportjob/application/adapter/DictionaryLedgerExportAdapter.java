package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.DictionaryLedgerExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.DictionaryLedgerExportRow;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 以字典项主键分页输出当前台账，保留停用记录，不输出内部标识。 */
@Component
public class DictionaryLedgerExportAdapter implements ExportDatasetAdapter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("TYPE_CODE", "类型编码", true),
            new ExportColumnDefinition("TYPE_NAME", "类型名称", true),
            new ExportColumnDefinition("ITEM_CODE", "项编码", true),
            new ExportColumnDefinition("ITEM_NAME", "项名称", true),
            new ExportColumnDefinition("SORT_ORDER", "排序", true),
            new ExportColumnDefinition("STATUS", "项状态", true),
            new ExportColumnDefinition("UPDATED_AT", "更新时间", true));
    private final DictionaryLedgerExportMapper mapper;

    public DictionaryLedgerExportAdapter(DictionaryLedgerExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.DICTIONARY_LEDGER; }
    @Override public boolean sensitive() { return false; }
    @Override public List<ExportColumnDefinition> columns() { return COLUMNS; }
    @Override public long captureUpperBound(ExportRequestDefinition request) {
        Long value = mapper.findUpperBound(request);
        return value == null ? 0L : value;
    }
    @Override public long count(ExportRequestDefinition request, long upperBound) {
        return mapper.count(request, upperBound);
    }
    @Override public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        if (limit < 1 || limit >= Integer.MAX_VALUE || cursor < 0 || upperBound < 0) {
            throw new IllegalArgumentException("导出分页参数不合法");
        }
        List<DictionaryLedgerExportRow> fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        List<DictionaryLedgerExportRow> selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(DictionaryLedgerExportRow row) {
        return Map.of("TYPE_CODE", row.typeCode(), "TYPE_NAME", row.typeName(),
                "ITEM_CODE", row.itemCode(), "ITEM_NAME", row.itemName(), "SORT_ORDER", row.sortOrder(),
                "STATUS", "ENABLED".equals(row.status()) ? "启用" : "停用",
                "UPDATED_AT", row.updatedAt() == null ? "" : TIME.format(row.updatedAt()));
    }
}
