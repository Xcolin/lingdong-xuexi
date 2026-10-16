package com.lingdong.learning.attendance.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.attendance.infrastructure.persistence.AttendanceScope;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.stereotype.Service;
import java.util.List;

/** 功能权限与对象范围分别校验，动态角色仍必须具备真实组织授权。 */
@Service
public class AttendanceAccessService {
    private final com.lingdong.learning.user.infrastructure.persistence.UserMapper users;
    private final com.lingdong.learning.auth.application.ParentActorAccessService parents;
    private final com.lingdong.learning.teacher.application.TeacherIdentityService teachers;
    private final com.lingdong.learning.student.infrastructure.persistence.StudentMapper students;
    private final com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper teacherClasses;
    private final FeatureAccessService features;
    private final PermissionDecisionService permissions;
    private final OrganizationDataScopeService scopes;

    public AttendanceAccessService(FeatureAccessService features, PermissionDecisionService permissions,
            OrganizationDataScopeService scopes, com.lingdong.learning.user.infrastructure.persistence.UserMapper users,
            com.lingdong.learning.auth.application.ParentActorAccessService parents,
            com.lingdong.learning.teacher.application.TeacherIdentityService teachers,
            com.lingdong.learning.student.infrastructure.persistence.StudentMapper students,
            com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper teacherClasses) {
        this.features = features;
        this.permissions = permissions;
        this.scopes = scopes;
        this.users=users; this.parents=parents; this.teachers=teachers; this.students=students; this.teacherClasses=teacherClasses;
    }

    public AttendanceScope require(AuthenticatedUser user, boolean write) {
        features.requireEnabled("ATTENDANCE_MANAGEMENT", null);
        if (user == null || user.clientType() == null
                || !permissions.isAllowed(user.userId(), PermissionClient.valueOf(user.clientType().name()),
                        write ? "ATTENDANCE_RECORD" : "ATTENDANCE_READ")) throw denied();
        var account=users.findById(user.userId());
        if (account==null || account.status()!=com.lingdong.learning.user.domain.UserStatus.ENABLED) throw denied();
        String mode;
        if (account.type()==com.lingdong.learning.user.domain.UserType.FAMILY) {
            parents.require(user); mode="PARENT";
        } else if (account.type()==com.lingdong.learning.user.domain.UserType.STUDENT) {
            var student=students.findByStudentUserId(user.userId());
            if (student==null || student.status()!=com.lingdong.learning.student.domain.StudentStatus.ENABLED) throw denied();
            mode="STUDENT";
        } else {
            var orgScope=scopes.resolve(user.userId());
            if (orgScope.allOrganizations() || !orgScope.rootPaths().isEmpty()) mode="ORGANIZATION";
            else if (teachers.isTeacher(user.userId()) && !teacherClasses.findActiveByTeacher(user.userId()).isEmpty()) mode="TEACHER";
            else throw denied();
        }
        if (write && (mode.equals("PARENT") || mode.equals("STUDENT"))) throw denied();
        if (!mode.equals("ORGANIZATION")) return new AttendanceScope(user.userId(), mode, false, List.of());
        var scope = scopes.resolve(user.userId());
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) throw denied();
        return new AttendanceScope(user.userId(), mode, scope.allOrganizations(), scope.rootPaths());
    }

    private SystemOperationAccessDeniedException denied() {
        return new SystemOperationAccessDeniedException("无权访问考勤或没有有效数据范围");
    }
}
