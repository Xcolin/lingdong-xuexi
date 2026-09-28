package com.lingdong.learning.audit.application;

import com.lingdong.learning.cache.infrastructure.persistence.CacheOperationMapper;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleChangeMapper;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceChangeMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationChangeMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/** 仅在任务范围校验通过后读取领域快照；不读取凭据、附件存储信息或导出内容。 */
@Component
public class SystemTaskPayloadReader {
    private final CacheOperationMapper caches;
    private final FeatureToggleChangeMapper features;
    private final InterfaceServiceChangeMapper interfaces;
    private final OrganizationChangeMapper organizations;
    private final ExportJobMapper exports;
    private final ObjectMapper json;

    public SystemTaskPayloadReader(CacheOperationMapper caches, FeatureToggleChangeMapper features,
            InterfaceServiceChangeMapper interfaces, OrganizationChangeMapper organizations, ExportJobMapper exports, ObjectMapper json) {
        this.caches = caches; this.features = features; this.interfaces = interfaces;
        this.organizations = organizations; this.exports = exports;
        this.json = json;
    }

    public Payload read(SystemTask task) {
        switch (task.type()) {
            case CACHE_CLEAR -> {
                var row = caches.findByTaskId(task.id());
                if (row == null) return missing();
                return new Payload(List.of(field("缓存范围", row.domain()), field("操作类型", row.operationType()),
                        field("影响说明", row.impactDescription()), field("操作编号", row.code()),
                        field("执行人 ID", row.executedBy()), field("执行时间", row.executedAt())),
                        List.of(), text(row.status()), row.failureMessage(), "缓存清除不保存缓存内容快照；会话清除会强制退出活动会话。");
            }
            case GLOBAL_FEATURE_TOGGLE -> {
                var row = features.findByTaskId(task.id());
                if (row == null) return missing();
                return new Payload(List.of(field("功能编码", row.featureCode()), field("申请时版本", row.baseVersion())),
                        List.of(diff("开关状态", row.beforeStatus(), row.targetStatus())),
                        task.status() == SystemTaskStatus.EFFECTIVE ? "APPLIED" : null, null,
                        row.beforeStatus() == null ? "历史申请未记录变更前快照。" : null);
            }
            case ORGANIZATION_DISABLE, ORGANIZATION_MOVE, ORGANIZATION_DELETE -> {
                var row = organizations.findByTaskId(task.id());
                if (row == null) return missing();
                var differences = task.type() == SystemTaskType.ORGANIZATION_MOVE
                        ? List.of(diff("上级组织 ID", row.fromParentIdSnapshot(), row.targetParentId()))
                        : List.of(diff("组织状态", organizations.findRequestedStatus(task.id()),
                                task.type() == SystemTaskType.ORGANIZATION_DISABLE ? "DISABLED" : "DELETED"));
                return new Payload(List.of(field("组织 ID", row.organizationId()), field("组织编码", row.organizationCodeSnapshot()),
                        field("组织名称", row.organizationNameSnapshot()), field("申请时版本", row.expectedVersion()),
                        field("变更原因", row.reason())), differences, text(row.executionStatus()), row.failureReason(),
                        "原值来自申请快照；缺失的历史值显示为未记录。");
            }
            case INTERFACE_SERVICE_CHANGE -> {
                var row = interfaces.findByTaskId(task.id());
                if (row == null) return missing();
                var differences = row.changeType() == com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChangeType.CHANGE_AUTHORIZATION
                        ? List.of(diff("授权范围", row.beforeAuthorizationScope(), row.authorizationScope()),
                                diff("授权范围值", row.beforeAuthorizationScopeValue(), row.authorizationScopeValue()))
                        : List.of(diff("服务状态", row.beforeStatus(), row.targetStatus()));
                return new Payload(List.of(field("服务 ID", row.serviceId()), field("变更类型", row.changeType()),
                        field("服务名称", row.serviceName()), field("方向", row.direction()), field("用途", row.purpose()),
                        field("调用方", row.callerName()), field("责任人 ID", row.ownerId()),
                        field("申请授权范围", row.authorizationScope()), field("申请授权范围值", row.authorizationScopeValue())),
                        differences, text(row.executionStatus()), row.failureReason(),
                        row.changeType() == com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChangeType.CREATE
                                ? "新增登记，无原服务状态。" : row.beforeStatus() == null ? "历史申请未记录变更前快照。" : null);
            }
            case SENSITIVE_DATA_EXPORT -> {
                var row = exports.findBySystemTaskId(task.id());
                if (row == null) return missing();
                var fields = new ArrayList<>(List.of(field("导出编号", row.jobCode()), field("数据集", row.exportType()),
                        field("模板名称", row.templateName()), field("模板版本", row.templateVersion()),
                        field("申请原因", row.requestReason()), field("总行数", row.totalRows()),
                        field("已处理行数", row.processedRows()), field("失败代码", row.failureCode())));
                String notice = "导出不修改源数据；本页展示申请参数，不展示导出内容和私密范围快照。";
                try {
                    JsonNode filter = snapshot(row.filterSnapshot());
                    JsonNode columns = snapshot(row.columnSnapshot());
                    JsonNode mask = snapshot(row.maskPolicySnapshot());
                    if (!filter.isObject() || !columns.isArray() || !mask.isObject()) throw new IllegalArgumentException();
                    // 仅投影已定义的审批参数，未知字段不得整体透传。
                    fields.add(field("筛选开始时间", scalar(filter, "startedAt")));
                    fields.add(field("筛选结束时间", scalar(filter, "endedAt")));
                    fields.add(field("事件类型", scalar(filter, "eventType")));
                    var labels = new ArrayList<String>();
                    for (JsonNode column : columns) {
                        String code = scalar(column, "code"), header = scalar(column, "header");
                        if (code == null || header == null) throw new IllegalArgumentException();
                        labels.add(header + "（" + code + "）");
                    }
                    fields.add(field("导出列", String.join("、", labels)));
                    fields.add(field("姓名脱敏策略", scalar(mask, "namePolicy")));
                    fields.add(field("脱敏策略版本", scalar(mask, "version")));
                } catch (java.io.IOException | IllegalArgumentException exception) {
                    notice = "申请参数快照缺失或格式异常，无法完整展示；请核查后再审批。";
                }
                return new Payload(List.copyOf(fields), List.of(), text(row.status()), row.failureMessage(), notice);
            }
            default -> { return missing(); }
        }
    }

    private JsonNode snapshot(String value) throws java.io.IOException {
        if (value == null) throw new IllegalArgumentException();
        return json.readTree(value);
    }
    private static String scalar(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        if (!value.isValueNode()) throw new IllegalArgumentException();
        return value.asText();
    }

    private static Payload missing() { return new Payload(List.of(), List.of(), null, null, "该历史任务缺少可读取的业务快照。"); }
    private static Field field(String label, Object value) { return new Field(label, text(value)); }
    private static Difference diff(String label, Object before, Object after) { return new Difference(label, text(before), text(after)); }
    private static String text(Object value) { return value == null ? null : value.toString(); }
    public record Field(String label, String value) { }
    public record Difference(String label, String before, String after) { }
    public record Payload(List<Field> fields, List<Difference> differences, String executionStatus, String failureReason, String notice) { }
}
