package com.lingdong.learning.audit.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SystemTaskApplicationServiceTest {
    @Autowired
    private SystemTaskApplicationService systemTaskApplicationService;

    @Autowired
    private UserAccessApplicationService userAccessApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Test
    void submitsSystemAdministratorTaskAndLetsAuditorApproveIt() {
        User administrator = createUserWithRole("sys_admin_task", "系统管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("sys_auditor_task", "系统审核员", "SYS_AUDITOR");

        SystemTask draft = systemTaskApplicationService.createDraft(new CreateSystemTaskCommand(
                administrator.id(),
                SystemTaskType.GLOBAL_FEATURE_TOGGLE,
                "启用全局功能开关",
                "恢复指定功能的全局可用状态",
                ImpactScope.GLOBAL
        ));
        systemTaskApplicationService.submit(draft.id(), administrator.id());
        SystemTask approved = systemTaskApplicationService.approve(draft.id(), auditor.id(), "同意执行");

        assertThat(approved.status()).isEqualTo(SystemTaskStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo(auditor.id());
    }

    @Test
    void requiresReviewCommentWhenRejectingTask() {
        User administrator = createUserWithRole("sys_admin_reject", "系统管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("sys_auditor_reject", "系统审核员", "SYS_AUDITOR");
        SystemTask draft = systemTaskApplicationService.createDraft(new CreateSystemTaskCommand(
                administrator.id(), SystemTaskType.SENSITIVE_DATA_EXPORT,
                "敏感数据导出", "导出已审批的统计数据", ImpactScope.SCHOOL
        ));
        systemTaskApplicationService.submit(draft.id(), administrator.id());

        assertThatThrownBy(() -> systemTaskApplicationService.reject(draft.id(), auditor.id(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批意见");
    }

    @Test
    void rejectsSubmissionAndReviewFromUsersWithoutRequiredRoles() {
        User parent = userAccessApplicationService.createUser(
                new CreateUserCommand("parent_audit", "家长", "13800000011", UserType.FAMILY)
        );

        assertThatThrownBy(() -> systemTaskApplicationService.createDraft(new CreateSystemTaskCommand(
                parent.id(), SystemTaskType.CACHE_CLEAR, "清除缓存", "清除指定缓存", ImpactScope.GLOBAL
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统管理员");
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM)
        );
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
