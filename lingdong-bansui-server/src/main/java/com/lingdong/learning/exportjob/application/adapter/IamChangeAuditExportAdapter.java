package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.IamAuditExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.IamAuditExportRow;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 系统权限变更日志导出，只输出九个经过控制的审计字段。 */
@Component
public class IamChangeAuditExportAdapter implements ExportDatasetAdapter {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("OCCURRED_AT", "发生时间", true),
            new ExportColumnDefinition("EVENT_TYPE", "事件类型", true),
            new ExportColumnDefinition("TARGET_TYPE", "目标类型", true),
            new ExportColumnDefinition("TARGET_ID", "目标标识", true),
            new ExportColumnDefinition("TARGET_NAME", "目标名称", true),
            new ExportColumnDefinition("OPERATOR_NAME", "操作人", true),
            new ExportColumnDefinition("BEFORE_SUMMARY", "变更前摘要", true),
            new ExportColumnDefinition("AFTER_SUMMARY", "变更后摘要", true),
            new ExportColumnDefinition("RESULT", "操作结果", true)
    );

    private final IamAuditExportMapper mapper;

    public IamChangeAuditExportAdapter(IamAuditExportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExportJobType type() {
        return ExportJobType.IAM_CHANGE_AUDIT;
    }

    @Override
    public boolean sensitive() {
        return true;
    }

    @Override
    public List<ExportColumnDefinition> columns() {
        return COLUMNS;
    }

    @Override
    public long captureUpperBound(ExportRequestDefinition request) {
        Objects.requireNonNull(request, "权限日志导出筛选不能为空");
        Long upperBound = mapper.findUpperBound(request.startedAt(), request.endedAt(), request.eventType());
        return upperBound == null ? 0L : upperBound;
    }

    @Override
    public long count(ExportRequestDefinition request, long upperBound) {
        Objects.requireNonNull(request, "权限日志导出筛选不能为空");
        return mapper.count(request.startedAt(), request.endedAt(), request.eventType(), upperBound);
    }

    @Override
    public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        Objects.requireNonNull(request, "权限日志导出筛选不能为空");
        if (limit < 1 || limit >= Integer.MAX_VALUE || cursor < 0 || upperBound < 0) {
            throw new IllegalArgumentException("导出分页参数不合法");
        }
        List<IamAuditExportRow> fetched = mapper.findAfter(
                request.startedAt(), request.endedAt(), request.eventType(), upperBound, cursor, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<IamAuditExportRow> selected = hasMore ? fetched.subList(0, limit) : fetched;
        List<Map<String, Object>> rows = new ArrayList<>(selected.size());
        for (IamAuditExportRow row : selected) {
            rows.add(toValues(row));
        }
        Long nextCursor = selected.isEmpty() ? null : selected.get(selected.size() - 1).id();
        return new ExportDataPage(rows, nextCursor, hasMore);
    }

    private Map<String, Object> toValues(IamAuditExportRow row) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("OCCURRED_AT", row.occurredAt() == null ? "" : TIME_FORMATTER.format(row.occurredAt()));
        values.put("EVENT_TYPE", eventType(row));
        values.put("TARGET_TYPE", targetType(row));
        values.put("TARGET_ID", row.targetId() == null ? "" : row.targetId().toString());
        values.put("TARGET_NAME", targetName(row));
        values.put("OPERATOR_NAME", ExportMasking.familyName(row.operatorName()));
        values.put("BEFORE_SUMMARY", text(row.beforeValue()));
        values.put("AFTER_SUMMARY", text(row.afterValue()));
        values.put("RESULT", "成功");
        return Map.copyOf(values);
    }

    private String targetName(IamAuditExportRow row) {
        if (row.targetType() == null) {
            return "";
        }
        return switch (row.targetType()) {
            case USER, USER_ORGANIZATION, USER_ROLE, USER_PERMISSION, ORGANIZATION_ADMIN ->
                    ExportMasking.familyName(row.targetName());
            default -> text(row.targetName());
        };
    }

    private String eventType(IamAuditExportRow row) {
        if (row.eventType() == null) {
            return "";
        }
        return switch (row.eventType()) {
            case MENU_CREATE -> "菜单创建";
            case MENU_UPDATE -> "菜单变更";
            case MENU_REORDER -> "菜单排序";
            case USER_CREATE -> "用户创建";
            case USER_PROFILE_CHANGE -> "用户资料变更";
            case USER_PASSWORD_RESET -> "用户密码重置";
            case USER_STATUS_CHANGE -> "用户状态变更";
            case USER_ORGANIZATION_ASSOCIATE -> "用户关联组织";
            case USER_ROLE_ASSIGN -> "用户分配角色";
            case ROLE_CREATE -> "角色创建";
            case PERMISSION_CREATE -> "权限创建";
            case ROLE_PERMISSION_CONFIGURE -> "角色权限配置";
            case ROLE_PERMISSION_REMOVE -> "角色权限移除";
            case USER_PERMISSION_CONFIGURE -> "用户权限配置";
            case USER_PERMISSION_REMOVE -> "用户权限移除";
            case ROLE_DATA_SCOPE_ADD -> "角色数据范围新增";
            case ORGANIZATION_ADMIN_ASSIGN -> "组织管理员配置";
        };
    }

    private String targetType(IamAuditExportRow row) {
        if (row.targetType() == null) {
            return "";
        }
        return switch (row.targetType()) {
            case MENU -> "菜单";
            case USER -> "用户";
            case ROLE -> "角色";
            case PERMISSION -> "权限";
            case ROLE_PERMISSION -> "角色权限";
            case USER_PERMISSION -> "用户权限";
            case ROLE_DATA_SCOPE -> "角色数据范围";
            case ORGANIZATION_ADMIN -> "组织管理员";
            case USER_ORGANIZATION -> "用户组织关系";
            case USER_ROLE -> "用户角色关系";
        };
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
