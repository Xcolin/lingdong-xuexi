package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.attendance.infrastructure.persistence.AttendanceScope;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption;
import com.lingdong.learning.exportjob.infrastructure.persistence.AttendanceLedgerExportMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointStudentOptionRow;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** 考勤台账导出沿用四身份范围（docs/design/09 第 2.6 节）：教师/机构冻结班级集合，家长/学生校验亲子或本人关系；审核员兼任拒绝。 */
@Service
public class AttendanceLedgerExportAccessService {
    private static final int MAX_CLASSES = 50_000;

    private final UserMapper users;
    private final UserRoleMapper roles;
    private final PermissionDecisionService permissions;
    private final FeatureAccessService features;
    private final OrganizationDataScopeService organizations;
    private final AttendanceLedgerExportMapper mapper;

    public AttendanceLedgerExportAccessService(UserMapper users, UserRoleMapper roles,
            PermissionDecisionService permissions, FeatureAccessService features,
            OrganizationDataScopeService organizations, AttendanceLedgerExportMapper mapper) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.features = features;
        this.organizations = organizations;
        this.mapper = mapper;
    }

    /** 角色优先级沿用考勤台账：机构管理员优先于教师；空组织路径失败关闭。 */
    public AttendanceScope require(long userId) {
        var user = users.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED) throw denied();
        features.requireEnabled("ATTENDANCE_MANAGEMENT", null);
        var roleCodes = roles.findEnabledRoleCodesByUserId(userId);
        // 审核员仅处理系统审批，兼任其他角色也不能导出考勤台账。
        // Dynamic permissions below apply to every enabled role.
        for (String permission : List.of("ATTENDANCE_LEDGER_EXPORT", "ATTENDANCE_READ")) {
            if (!permissions.isAllowed(userId, PermissionClient.WEB, permission)) throw denied();
        }
        if (roleCodes.contains("ORG_ADMIN") || roleCodes.stream().noneMatch(role -> List.of("TEACHER", "PARENT", "STUDENT").contains(role))) {
            var scope = organizations.resolve(userId);
            if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) throw denied();
            return new AttendanceScope(userId, "ORGANIZATION", scope.allOrganizations(), scope.rootPaths());
        }
        if (roleCodes.contains("TEACHER")) return new AttendanceScope(userId, "TEACHER", false, List.of());
        if (roleCodes.contains("PARENT")) return new AttendanceScope(userId, "PARENT", false, List.of());
        if (roleCodes.contains("STUDENT")) return new AttendanceScope(userId, "STUDENT", false, List.of());
        throw denied();
    }

    public List<ExceptionReportClassOption> classes(long userId) {
        return mapper.findClassOptions(require(userId));
    }

    public List<GrowthPointStudentOptionRow> familyStudents(long userId) {
        var scope = require(userId);
        if (!"PARENT".equals(scope.mode()) && !"STUDENT".equals(scope.mode())) throw denied();
        return mapper.findFamilyStudents(userId);
    }

    public void requireFamily(long userId, long studentId) {
        var scope = require(userId);
        if (!"PARENT".equals(scope.mode()) && !"STUDENT".equals(scope.mode())) throw denied();
        boolean allowed = mapper.findFamilyStudents(userId).stream()
                .anyMatch(student -> Objects.equals(student.studentId(), studentId));
        if (!allowed) throw denied();
    }

    public Frozen freeze(CreateExportJobCommand command) {
        var scope = require(command.requesterId());
        var authorized = mapper.findVisibleClassIds(scope, MAX_CLASSES + 1);
        if (authorized.size() > MAX_CLASSES) throw new IllegalArgumentException("考勤台账超过允许班级范围，请缩小范围");
        if (command.attClassId() == null) {
            // 家庭身份通过学生标识校验亲子或本人关系，不走班级冻结路径；教师/机构冻结全部有效班级。
            boolean family = "PARENT".equals(scope.mode()) || "STUDENT".equals(scope.mode());
            if (family) return new Frozen(scope.mode(), List.of());
            if (authorized.isEmpty()) throw denied();
            return new Frozen(scope.mode(), List.copyOf(authorized));
        }
        Long selected = Long.valueOf(command.attClassId());
        if (!authorized.contains(selected)) throw denied();
        return new Frozen(scope.mode(), List.of(selected));
    }

    public void requireFrozen(long userId, ExportScopeSnapshot frozen) {
        var scope = require(userId);
        var ids = frozen.attClassIds();
        if (ids == null || ids.isEmpty() || ids.size() > MAX_CLASSES || frozen.upperBound() < 0
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw denied();
        }
        // 班级授权、组织范围或角色撤销都会使当前可见集合收窄，拒绝旧文件而非交付部分过滤后的假快照。
        var authorized = mapper.findVisibleClassIds(scope, MAX_CLASSES + 1);
        if (authorized.size() > MAX_CLASSES || !authorized.containsAll(ids)) throw denied();
    }

    public record Frozen(String role, List<Long> ids) {}

    private static SystemOperationAccessDeniedException denied() {
        return new SystemOperationAccessDeniedException("考勤台账导出权限或范围已失效");
    }
}
