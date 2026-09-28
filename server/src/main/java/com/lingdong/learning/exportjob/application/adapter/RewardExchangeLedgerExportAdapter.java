package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.RewardExchangeExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.RewardExchangeExportRow;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class RewardExchangeLedgerExportAdapter implements ExportDatasetAdapter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("REWARD_NAME", "奖励名称", true),
            new ExportColumnDefinition("REQUIRED_POINTS", "所需积分", true),
            new ExportColumnDefinition("REQUESTED_AT", "申请时间", true),
            new ExportColumnDefinition("APPROVAL_STATUS", "审批状态", true),
            new ExportColumnDefinition("VERIFICATION_STATUS", "核销状态", true));
    private final RewardExchangeExportMapper mapper;
    public RewardExchangeLedgerExportAdapter(RewardExchangeExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.REWARD_EXCHANGE_LEDGER; }
    @Override public boolean sensitive() { return false; }
    @Override public List<ExportColumnDefinition> columns() { return COLUMNS; }
    @Override public long captureUpperBound(ExportRequestDefinition request) {
        requireStudent(request); Long upper = mapper.findUpperBound(request); return upper == null ? 0 : upper;
    }
    @Override public long count(ExportRequestDefinition request, long upperBound) {
        requireStudent(request); return mapper.count(request, upperBound);
    }
    @Override public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        requireStudent(request);
        if (limit < 1 || limit == Integer.MAX_VALUE || cursor < 0 || upperBound < 0) throw new IllegalArgumentException("导出分页参数不合法");
        var fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        var selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(RewardExchangeExportRow row) {
        String approval = switch (row.status()) {
            case PENDING_APPROVAL -> "待审批";
            case PENDING_VERIFICATION, VERIFIED -> "已通过";
            case REJECTED -> "已驳回";
            case AUTO_REJECTED -> "超时驳回";
            case EXPIRED -> row.reviewedAt() == null ? "未审批" : "已通过";
        };
        String verification = switch (row.status()) {
            case VERIFIED -> "已核销";
            case PENDING_VERIFICATION -> "待核销";
            case EXPIRED -> "已过期";
            default -> "未核销";
        };
        return Map.of("REWARD_NAME", row.rewardName(), "REQUIRED_POINTS", row.requiredPoints(),
                "REQUESTED_AT", TIME.format(row.requestedAt()), "APPROVAL_STATUS", approval,
                "VERIFICATION_STATUS", verification);
    }
    private void requireStudent(ExportRequestDefinition request) {
        if (request == null || request.studentId() == null) throw new IllegalArgumentException("奖励兑换导出必须指定学生");
    }
}
