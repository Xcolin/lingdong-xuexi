package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.CacheOperationLogExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.CacheOperationLogExportRow;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** 以缓存日志主键分页输出事实，不触发缓存处理器。 */
@Component
public class CacheOperationLogExportAdapter implements ExportDatasetAdapter {
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("CACHE_DOMAIN", "缓存类型", true),
            new ExportColumnDefinition("MODULE", "模块", true),
            new ExportColumnDefinition("OPERATION", "操作", true),
            new ExportColumnDefinition("OPERATOR_ID", "执行人", true),
            new ExportColumnDefinition("OCCURRED_AT", "申请时间", true),
            new ExportColumnDefinition("RESULT", "结果", true));
    private final CacheOperationLogExportMapper mapper;

    public CacheOperationLogExportAdapter(CacheOperationLogExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.CACHE_OPERATION_LOG; }
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
        List<CacheOperationLogExportRow> fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        List<CacheOperationLogExportRow> selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(CacheOperationLogExportRow row) {
        String module = switch (row.cacheDomain()) {
            case "PERMISSION" -> "权限"; case "DICTIONARY" -> "数据字典";
            case "ORGANIZATION" -> "组织"; case "FEATURE_TOGGLE" -> "功能开关";
            case "USER_SESSION" -> "用户会话"; case "BUSINESS_STATISTICS" -> "业务统计";
            case "ALL" -> "全部已注册缓存"; default -> throw new IllegalStateException("未知缓存域");
        };
        String operation = "REFRESH".equals(row.operationType()) ? "刷新"
                : "USER_SESSION".equals(row.cacheDomain()) ? "清除（强制退出）" : "清除";
        String result = switch (row.status()) {
            case "PENDING" -> "待处理"; case "SUCCEEDED" -> "成功";
            case "FAILED" -> "失败"; case "REJECTED" -> "已驳回";
            default -> throw new IllegalStateException("未知缓存状态");
        };
        return Map.of("CACHE_DOMAIN", row.cacheDomain(), "MODULE", module, "OPERATION", operation,
                "OPERATOR_ID", row.executedBy() == null ? "" : row.executedBy().toString(),
                "OCCURRED_AT", row.createdAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "RESULT", result);
    }
}
