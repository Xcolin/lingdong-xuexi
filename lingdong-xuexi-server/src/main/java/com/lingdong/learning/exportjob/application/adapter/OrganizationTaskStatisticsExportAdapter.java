package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.OrgTaskStatExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.OrgTaskStatExportRow;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** 机构任务统计导出：按来源组织聚合已生成的机构公开任务实例，仅输出组织名称与聚合计数。 */
@Component
public class OrganizationTaskStatisticsExportAdapter implements ExportDatasetAdapter {
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("SCHOOL_NAME", "学校", true),
            new ExportColumnDefinition("CLASS_NAME", "班级", true),
            new ExportColumnDefinition("TASK_COUNT", "任务数", true),
            new ExportColumnDefinition("COMPLETED_COUNT", "完成数", true),
            new ExportColumnDefinition("COMPLETION_RATE", "完成率(%)", true),
            new ExportColumnDefinition("AVG_POINTS", "平均积分", true));

    private final OrgTaskStatExportMapper mapper;

    public OrganizationTaskStatisticsExportAdapter(OrgTaskStatExportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExportJobType type() {
        return ExportJobType.ORGANIZATION_TASK_STATISTICS;
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
        requireScope(request);
        Long max = mapper.maxVisibleAssignmentId(request);
        return max == null ? 0 : max;
    }

    @Override
    public long count(ExportRequestDefinition request, long upperBound) {
        requireScope(request);
        return mapper.count(request, upperBound);
    }

    @Override
    public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        requireScope(request);
        if (limit < 1 || limit == Integer.MAX_VALUE || upperBound < 0 || cursor < 0) {
            throw new IllegalArgumentException("机构任务统计分页参数无效");
        }
        var rows = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = rows.size() > limit;
        var page = more ? rows.subList(0, limit) : rows;
        return new ExportDataPage(page.stream().map(this::values).toList(),
                page.isEmpty() ? null : page.get(page.size() - 1).repId(), more);
    }

    private Map<String, Object> values(OrgTaskStatExportRow row) {
        // 组织名称非个人数据，无需姓名脱敏；比率与积分保留两位小数输出；完成率分母为0（无已定局面）时输出空。
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("SCHOOL_NAME", row.schoolName() == null ? "" : row.schoolName());
        values.put("CLASS_NAME", row.className() == null ? "" : row.className());
        values.put("TASK_COUNT", row.taskCount());
        values.put("COMPLETED_COUNT", row.completedCount());
        values.put("COMPLETION_RATE", row.completionRate());
        values.put("AVG_POINTS", row.avgPoints());
        return values;
    }

    private void requireScope(ExportRequestDefinition request) {
        if (request == null || request.orgStatOrgIds() == null || request.orgStatOrgIds().isEmpty()) {
            throw new IllegalArgumentException("机构任务统计缺少冻结范围");
        }
    }
}
