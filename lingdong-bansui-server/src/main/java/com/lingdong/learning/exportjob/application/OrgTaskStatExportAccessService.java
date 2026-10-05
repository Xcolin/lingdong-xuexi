package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption;
import com.lingdong.learning.exportjob.infrastructure.persistence.OrgTaskStatExportMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import com.lingdong.learning.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

/** 机构任务统计导出的角色、功能、权限与授权组织范围控制；仅机构管理员可导出。 */
@Service
public class OrgTaskStatExportAccessService {
    private static final int MAX_SOURCE_ORGS = 50_000;

    private final UserMapper users;
    private final UserRoleMapper roles;
    private final PermissionDecisionService permissions;
    private final FeatureAccessService features;
    private final OrganizationDataScopeService organizations;
    private final OrgTaskStatExportMapper mapper;

    public OrgTaskStatExportAccessService(UserMapper users, UserRoleMapper roles,
        PermissionDecisionService permissions, FeatureAccessService features,
        OrganizationDataScopeService organizations, OrgTaskStatExportMapper mapper) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.features = features;
        this.organizations = organizations;
        this.mapper = mapper;
    }

    public StudentTaskVisibility require(long userId) {
        var user = users.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED) throw denied();
        for (String feature : List.of("DATA_EXPORT", "IMPORT_EXPORT_TEMPLATE_MANAGEMENT", "ATTACHMENT_SERVICE", "LEARNING_TASK_MANAGEMENT")) features.requireEnabled(feature, null);
        var roleCodes = roles.findEnabledRoleCodesByUserId(userId);
        // 审核员仅处理系统审批，兼任机构管理员也不能导出机构统计。
        // Operation permissions below authorize organization statistics; no built-in role is required.
        for (String permission : List.of("ORGANIZATION_TASK_STATISTICS_EXPORT", "LEARNING_TASK_PROGRESS_READ")) {
            if (!permissions.isAllowed(userId, PermissionClient.WEB, permission)) throw denied();
        }
        var scope = organizations.resolve(userId);
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) throw denied();
        return new StudentTaskVisibility(userId, "ORG_ADMIN", scope.allOrganizations(), scope.rootPaths());
    }

    public List<ExceptionReportClassOption> classes(long userId) {
        return mapper.findOrgOptions(require(userId));
    }

    public Frozen freeze(CreateExportJobCommand command) {
        var current = require(command.requesterId());
        var authorized = mapper.findVisibleOrgIds(current, toDate(command.startedAt()), toDate(command.endedAt()), MAX_SOURCE_ORGS + 1);
        if (authorized.size() > MAX_SOURCE_ORGS) throw new IllegalArgumentException("机构任务统计超过允许范围，请缩小日期或班级范围");
        Long selected = null;
        if (command.orgStatClassId() != null) {
            selected = Long.valueOf(command.orgStatClassId());
            if (!authorized.contains(selected)) throw denied();
        }
        return new Frozen(current.role(), selected == null ? List.copyOf(authorized) : List.of(selected));
    }

    public void requireFrozen(long userId, ExportScopeSnapshot frozen) {
        var current = require(userId);
        var ids = frozen.orgStatOrgIds();
        if (!"ORG_ADMIN".equals(frozen.orgStatRole()) || ids == null || ids.isEmpty()
                || ids.size() > MAX_SOURCE_ORGS || frozen.upperBound() < 0
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw denied();
        }
        // 任何授权组织迁出、停用或角色撤销都会使当前可见集合收窄，拒绝旧文件而非交付部分过滤后的假快照。
        var authorized = mapper.findVisibleOrgIds(current, null, null, MAX_SOURCE_ORGS + 1);
        if (authorized.size() > MAX_SOURCE_ORGS || !authorized.containsAll(ids)) throw denied();
    }

    private static LocalDate toDate(java.time.LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    public record Frozen(String role, List<Long> ids) {}

    private static SystemOperationAccessDeniedException denied() {
        return new SystemOperationAccessDeniedException("机构任务统计导出权限或范围已失效");
    }
}
