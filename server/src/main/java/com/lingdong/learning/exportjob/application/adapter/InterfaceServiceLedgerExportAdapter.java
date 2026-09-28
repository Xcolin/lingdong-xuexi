package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.InterfaceServiceLedgerExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.InterfaceServiceLedgerExportRow;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** 以接口主键分页输出公开台账，不读取密钥、地址或报文。 */
@Component
public class InterfaceServiceLedgerExportAdapter implements ExportDatasetAdapter {
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("SERVICE_NAME", "名称", true),
            new ExportColumnDefinition("PURPOSE", "用途", true),
            new ExportColumnDefinition("CALLER_NAME", "调用方", true),
            new ExportColumnDefinition("AUTHORIZATION_SCOPE", "授权范围", true),
            new ExportColumnDefinition("STATUS", "状态", true),
            new ExportColumnDefinition("OWNER_ID", "责任人", true));
    private final InterfaceServiceLedgerExportMapper mapper;

    public InterfaceServiceLedgerExportAdapter(InterfaceServiceLedgerExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.INTERFACE_SERVICE_LEDGER; }
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
        List<InterfaceServiceLedgerExportRow> fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        List<InterfaceServiceLedgerExportRow> selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(InterfaceServiceLedgerExportRow row) {
        String scope = row.authorizationScopeValue() == null || row.authorizationScopeValue().isBlank()
                ? row.authorizationScope() : row.authorizationScope() + ": " + row.authorizationScopeValue();
        return Map.of("SERVICE_NAME", row.serviceName(), "PURPOSE", row.purpose(),
                "CALLER_NAME", row.callerName(), "AUTHORIZATION_SCOPE", scope,
                "STATUS", "ENABLED".equals(row.status()) ? "启用" : "停用",
                "OWNER_ID", row.ownerId() == null ? "" : row.ownerId().toString());
    }
}
