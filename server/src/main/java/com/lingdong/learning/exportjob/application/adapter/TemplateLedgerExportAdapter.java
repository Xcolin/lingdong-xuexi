package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.TemplateLedgerExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.TemplateLedgerExportRow;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 以模板主键分页输出当前配置版本台账，不读取模板文件或存储信息。 */
@Component
public class TemplateLedgerExportAdapter implements ExportDatasetAdapter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("TEMPLATE_NAME", "名称", true),
            new ExportColumnDefinition("TEMPLATE_TYPE", "类型", true),
            new ExportColumnDefinition("MODULE_CODE", "适用模块", true),
            new ExportColumnDefinition("VERSION", "版本", true),
            new ExportColumnDefinition("STATUS", "状态", true),
            new ExportColumnDefinition("UPDATED_AT", "更新时间", true));
    private final TemplateLedgerExportMapper mapper;

    public TemplateLedgerExportAdapter(TemplateLedgerExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.TEMPLATE_LEDGER; }
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
        List<TemplateLedgerExportRow> fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        List<TemplateLedgerExportRow> selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(TemplateLedgerExportRow row) {
        return Map.of("TEMPLATE_NAME", row.templateName(),
                "TEMPLATE_TYPE", "IMPORT".equals(row.templateType()) ? "导入" : "导出",
                "MODULE_CODE", row.moduleCode(), "VERSION", row.version(),
                "STATUS", "ENABLED".equals(row.status()) ? "启用" : "停用",
                "UPDATED_AT", row.updatedAt() == null ? "" : TIME.format(row.updatedAt()));
    }
}
