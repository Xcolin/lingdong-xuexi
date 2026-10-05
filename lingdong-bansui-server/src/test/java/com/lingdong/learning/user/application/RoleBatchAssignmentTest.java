package com.lingdong.learning.user.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 角色侧批量勾选用户授予：整批事务原子、组织范围角色必填组织、失败回滚并报告原因。 */
@SpringBootTest @ActiveProfiles("test") @Transactional
class RoleBatchAssignmentTest {
    @Autowired UserAccessApplicationService service;
    @Autowired RoleApplicationService roleService;
    @Autowired RoleMapper roles;
    @Autowired OrganizationMapper organizations;
    @Autowired IdGenerator ids;
    @Autowired JdbcTemplate jdbc;

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 6);
    }

    private User newUser() {
        return service.createUser(new CreateUserCommand(
                "batch_" + UUID.randomUUID().toString().substring(0, 10), "批量授予测试", null, UserType.PLATFORM));
    }

    @Test void batchAssignsGlobalRoleToMultipleUsers() {
        var operator = newUser();
        var role = roleService.createCustomRole(new CreateCustomRoleCommand(
                "BATCH_GLOBAL_" + suffix(), "批量全局角色", null, RoleDataScope.ALL));
        var u1 = newUser();
        var u2 = newUser();
        service.batchAssignRole(new BatchAssignRoleToRoleCommand(
                operator.id(), role.id(),
                List.of(new BatchRoleAssignmentItem(u1.id(), null), new BatchRoleAssignmentItem(u2.id(), null))));
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user_role where role_id=? and organization_scope_key='GLOBAL'", Integer.class, role.id());
        assertThat(count).isEqualTo(2);
    }

    @Test void organizationScopedRoleRequiresOrganizationAndRelation() {
        var operator = newUser();
        var role = roleService.createCustomRole(new CreateCustomRoleCommand(
                "BATCH_ORG_" + suffix(), "批量组织角色", null, RoleDataScope.REGION));
        var u1 = newUser();
        var region = organizations.findByCode("500101");
        // 未指定组织被拒绝
        assertThatThrownBy(() -> service.batchAssignRole(new BatchAssignRoleToRoleCommand(
                operator.id(), role.id(), List.of(new BatchRoleAssignmentItem(u1.id(), null)))))
                .isInstanceOf(BatchRoleAssignmentException.class)
                .hasMessageContaining(u1.id().toString());
        // 建立组织关联后授予成功
        service.associateWithOrganization(new AssociateUserWithOrganizationCommand(u1.id(), region.id(), operator.id()));
        service.batchAssignRole(new BatchAssignRoleToRoleCommand(
                operator.id(), role.id(), List.of(new BatchRoleAssignmentItem(u1.id(), region.id()))));
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user_role where role_id=? and user_id=?", Integer.class, role.id(), u1.id());
        assertThat(count).isEqualTo(1);
    }

    @Test void anyFailureRollsBackEntireBatchAndReportsReasons() {
        var operator = newUser();
        var role = roleService.createCustomRole(new CreateCustomRoleCommand(
                "BATCH_ROLL_" + suffix(), "批量回滚角色", null, RoleDataScope.ALL));
        var u1 = newUser();
        var u2 = newUser();
        service.batchAssignRole(new BatchAssignRoleToRoleCommand(
                operator.id(), role.id(), List.of(new BatchRoleAssignmentItem(u2.id(), null))));
        Integer before = jdbc.queryForObject(
                "select count(*) from sys_user_role where role_id=?", Integer.class, role.id());
        assertThat(before).isEqualTo(1);
        // u1 重复授予 + u2 新授予：任一失败整批回滚
        assertThatThrownBy(() -> service.batchAssignRole(new BatchAssignRoleToRoleCommand(
                operator.id(), role.id(),
                List.of(new BatchRoleAssignmentItem(u2.id(), null), new BatchRoleAssignmentItem(u1.id(), null)))))
                .isInstanceOf(BatchRoleAssignmentException.class)
                .hasMessageContaining("已拥有");
        Integer after = jdbc.queryForObject(
                "select count(*) from sys_user_role where role_id=?", Integer.class, role.id());
        assertThat(after).isEqualTo(1);
    }
}
