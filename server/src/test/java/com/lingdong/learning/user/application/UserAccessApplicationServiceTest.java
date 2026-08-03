package com.lingdong.learning.user.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserAccessApplicationServiceTest {
    @Autowired
    private UserAccessApplicationService userAccessApplicationService;

    @Autowired
    private OrganizationApplicationService organizationApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void associatesUserBeforeGrantingOrganizationScopedRole() {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand("teacher_zhang", "张老师", "13800000001", UserType.ORGANIZATION)
        );
        assertThat(Long.toString(user.id())).hasSize(19);
        Organization organization = createSchool("REGION_USER_A", "SCHOOL_USER_A", "用户授权学校A");
        Role organizationAdmin = roleMapper.findByCode("ORG_ADMIN");

        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(user.id(), organization.id())
        );
        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(user.id(), organizationAdmin.id(), organization.id())
        );

        assertThat(userRoleMapper.exists(user.id(), organizationAdmin.id(), "ORG:" + organization.id())).isTrue();
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_user_organization where user_id = ? and organization_id = ?",
                Long.class, user.id(), organization.id()
        ))).hasSize(19);
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_user_role where user_id = ? and role_id = ? and organization_scope_key = ?",
                Long.class, user.id(), organizationAdmin.id(), "ORG:" + organization.id()
        ))).hasSize(19);
    }

    @Test
    void rejectsOrganizationScopedRoleBeforeUserIsAssociated() {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand("teacher_wang", "王老师", "13800000002", UserType.ORGANIZATION)
        );
        Organization organization = createSchool("REGION_USER_B", "SCHOOL_USER_B", "用户授权学校B");
        Role teacher = roleMapper.findByCode("TEACHER");

        assertThatThrownBy(() -> userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(user.id(), teacher.id(), organization.id())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("组织关联");
    }

    @Test
    void grantsGlobalRoleToIndependentParent() {
        User parent = userAccessApplicationService.createUser(
                new CreateUserCommand("parent_liu", "刘家长", "13800000003", UserType.FAMILY)
        );
        Role parentRole = roleMapper.findByCode("PARENT");

        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(parent.id(), parentRole.id(), null)
        );

        assertThat(userRoleMapper.exists(parent.id(), parentRole.id(), "GLOBAL")).isTrue();
    }

    private Organization createSchool(String regionCode, String schoolCode, String schoolName) {
        Organization region = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(regionCode, regionCode, "REGION", null, 10)
        );
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(schoolCode, schoolName, "SCHOOL", region.id(), 10)
        );
    }
}
