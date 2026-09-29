package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.GrowthPointExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.GrowthPointExportRow;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 家长积分明细导出，只输出十个既定业务字段。 */
@Component
public class GrowthPointLedgerExportAdapter implements ExportDatasetAdapter {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("OCCURRED_AT", "发生时间", true),
            new ExportColumnDefinition("STUDENT_NAME", "学生姓名", true),
            new ExportColumnDefinition("CHANGE_TYPE", "变动类型", true),
            new ExportColumnDefinition("AMOUNT", "积分变动", true),
            new ExportColumnDefinition("AVAILABLE_DELTA", "可用积分变动", true),
            new ExportColumnDefinition("SOURCE_TYPE", "来源类型", true),
            new ExportColumnDefinition("SOURCE_ORGANIZATION", "来源机构", false),
            new ExportColumnDefinition("TASK_TITLE", "任务标题", false),
            new ExportColumnDefinition("REVIEWER_NAME", "审核人", false),
            new ExportColumnDefinition("REMARK", "备注", false)
    );

    private final GrowthPointExportMapper mapper;

    public GrowthPointLedgerExportAdapter(GrowthPointExportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExportJobType type() {
        return ExportJobType.GROWTH_POINT_LEDGER;
    }

    @Override
    public boolean sensitive() {
        return false;
    }

    @Override
    public List<ExportColumnDefinition> columns() {
        return COLUMNS;
    }

    @Override
    public long captureUpperBound(ExportRequestDefinition request) {
        requireStudent(request);
        Long upperBound = mapper.findUpperBound(request.studentId(), request.startedAt(), request.endedAt());
        return upperBound == null ? 0L : upperBound;
    }

    @Override
    public long count(ExportRequestDefinition request, long upperBound) {
        requireStudent(request);
        return mapper.count(request.studentId(), request.startedAt(), request.endedAt(), upperBound);
    }

    @Override
    public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        requireStudent(request);
        requirePage(limit, cursor, upperBound);
        List<GrowthPointExportRow> fetched = mapper.findAfter(
                request.studentId(), request.startedAt(), request.endedAt(), upperBound, cursor, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<GrowthPointExportRow> selected = hasMore ? fetched.subList(0, limit) : fetched;
        List<Map<String, Object>> rows = new ArrayList<>(selected.size());
        for (GrowthPointExportRow row : selected) {
            rows.add(toValues(row));
        }
        Long nextCursor = selected.isEmpty() ? null : selected.get(selected.size() - 1).id();
        return new ExportDataPage(rows, nextCursor, hasMore);
    }

    private Map<String, Object> toValues(GrowthPointExportRow row) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("OCCURRED_AT", row.occurredAt() == null ? "" : TIME_FORMATTER.format(row.occurredAt()));
        values.put("STUDENT_NAME", ExportMasking.familyName(row.studentName()));
        values.put("CHANGE_TYPE", changeType(row));
        values.put("AMOUNT", row.amount());
        values.put("AVAILABLE_DELTA", row.availableDelta());
        values.put("SOURCE_TYPE", sourceType(row));
        values.put("SOURCE_ORGANIZATION", text(row.sourceOrganizationName()));
        values.put("TASK_TITLE", text(row.taskTitle()));
        values.put("REVIEWER_NAME", ExportMasking.familyName(row.reviewerName()));
        values.put("REMARK", text(row.remark()));
        return Map.copyOf(values);
    }

    private String changeType(GrowthPointExportRow row) {
        if (row.changeType() == null) {
            return "";
        }
        return switch (row.changeType()) {
            case TASK_REWARD -> "任务奖励";
            case REDEMPTION -> "奖励兑换";
            case DORMANCY_CLEAR -> "沉睡清零";
            case CORRECTION -> "积分纠错";
        };
    }

    private String sourceType(GrowthPointExportRow row) {
        if (row.sourceType() == null) {
            return "";
        }
        return switch (row.sourceType()) {
            case FAMILY -> "家庭";
            case ORGANIZATION -> "机构";
            case TEACHER -> "教师";
        };
    }

    private void requireStudent(ExportRequestDefinition request) {
        Objects.requireNonNull(request, "积分导出筛选不能为空");
        if (request.studentId() == null) {
            throw new IllegalArgumentException("积分导出必须指定学生");
        }
    }

    private void requirePage(int limit, long cursor, long upperBound) {
        if (limit < 1 || limit >= Integer.MAX_VALUE || cursor < 0 || upperBound < 0) {
            throw new IllegalArgumentException("导出分页参数不合法");
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
