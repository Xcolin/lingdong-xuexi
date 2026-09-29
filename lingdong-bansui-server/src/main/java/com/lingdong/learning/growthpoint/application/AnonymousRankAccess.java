package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

/** 排行读取前的动态对象鉴权，不替代家长主动开启偏好的独立检查。 */
@Service
public class AnonymousRankAccess {
    private final UserMapper users;
    private final UserRoleMapper roles;
    private final ParentStudentMapper parents;
    private final StudentOrganizationMapper classes;
    private final PermissionDecisionService permissions;
    private final FeatureAccessService features;

    public AnonymousRankAccess(UserMapper users, UserRoleMapper roles, ParentStudentMapper parents,
            StudentOrganizationMapper classes, PermissionDecisionService permissions, FeatureAccessService features) {
        this.users=users; this.roles=roles; this.parents=parents; this.classes=classes;
        this.permissions=permissions; this.features=features;
    }

    public void requireRead(AuthenticatedUser user, Long studentId, Long classId) {
        requireOwner(user,studentId,classId);
        requireStudent(user,studentId);
        if (!classes.existsActiveClass(studentId,classId)) throw denied();
    }

    public void requireStudent(AuthenticatedUser user, Long studentId) {
        requireParent(user);
        if (studentId == null || studentId < 1000000000000000000L)
            throw new IllegalArgumentException("学生标识必须为19位正整数");
        features.requireEnabled("ANONYMOUS_CLASS_RANK",null);
        if (!canRead(user)
                || !parents.existsActiveByParentAndStudent(user.userId(),studentId)) throw denied();
    }

    public record StudentOption(String studentId, String studentName) { }
    public java.util.List<StudentOption> students(AuthenticatedUser user) {
        requireParent(user);
        features.requireEnabled("ANONYMOUS_CLASS_RANK",null);
        if (!canRead(user)) throw denied();
        return parents.findActiveStudentsByParent(user.userId()).stream()
                .map(row -> new StudentOption(row.studentId().toString(),row.studentName())).toList();
    }

    /** 撤回本人偏好不依赖已经失去的班级关系或功能开启状态。 */
    public void requireOwner(AuthenticatedUser user, Long studentId, Long classId) {
        requireParent(user);
        if (studentId == null || classId == null || studentId < 1000000000000000000L || classId < 1000000000000000000L)
            throw new IllegalArgumentException("学生和班级标识必须为19位正整数");
    }

    public void requireParent(AuthenticatedUser user) {
        requireSession(user);
        var account=users.findById(user.userId());
        if (account == null || account.status() != UserStatus.ENABLED
                || !roles.hasRoleCode(user.userId(),"PARENT") || roles.hasRoleCode(user.userId(),"SYS_AUDITOR")) throw denied();
    }

    /** 仅验证会话形状，不在写事务获取行锁前建立数据库快照。 */
    public void requireSession(AuthenticatedUser user) {
        if (user == null || user.userId() == null
                || (user.clientType() != AuthClientType.WEB && user.clientType() != AuthClientType.MINIAPP)) throw denied();
    }

    private boolean canRead(AuthenticatedUser user) {
        return user.clientType() == AuthClientType.MINIAPP
                ? permissions.isAllowed(user.userId(),PermissionClient.MINIAPP,"MINIAPP_ANONYMOUS_CLASS_RANK_READ")
                : permissions.isAllowed(user.userId(),PermissionClient.WEB,"ANONYMOUS_CLASS_RANK_READ");
    }

    private static SystemOperationAccessDeniedException denied() {
        return new SystemOperationAccessDeniedException("当前账号无权查看该班级排行");
    }
}
