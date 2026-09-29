package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证认证失败抛出异常后，会话撤销状态仍独立提交且不可复活。 */
@SpringBootTest
@ActiveProfiles("test")
class OrganizationSessionRevocationPersistenceTest {
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private DeviceSessionMapper deviceSessionMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IdGenerator idGenerator;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void keepsARefreshSessionRevokedAfterTheFeatureIsEnabledAgain() {
        User administrator = userAccessApplicationService.createUser(new CreateUserCommand(
                "org_revocation_persistence", "撤销持久化管理员", null, UserType.ORGANIZATION));
        assertThat(userMapper.updatePasswordHash(
                administrator.id(), passwordEncoder.encode("ValidPass123!"))).isEqualTo(1);
        Role role = roleMapper.findByCode("ORG_ADMIN");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(administrator.id(), role.id(), null));
        Organization school = organizationApplicationService.createOrganization(new CreateOrganizationCommand(
                "ORG_REVOCATION_PERSISTENCE", "撤销持久化学校", "SCHOOL", null, 10));
        assertThat(organizationAdminMapper.insert(
                idGenerator.nextId(), administrator.id(), school.id())).isEqualTo(1);
        AuthenticatedSession session = authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        administrator.username(), "ValidPass123!", "revocation-persistence", "机构管理员手机"));

        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'ORGANIZATION_MINIAPP_AUTH' and scope_key = 'GLOBAL'
                """);
        try {
            assertThatThrownBy(() -> authenticationApplicationService.refreshSession(
                    new RefreshSessionCommand(session.refreshToken())))
                    .isInstanceOf(AuthenticationFailedException.class);
        } finally {
            jdbcTemplate.update("""
                    update sys_feature_toggle set status = 'ENABLED'
                    where feature_code = 'ORGANIZATION_MINIAPP_AUTH' and scope_key = 'GLOBAL'
                    """);
        }

        assertThat(deviceSessionMapper.findById(session.sessionId()).status().name()).isEqualTo("REVOKED");
        assertThatThrownBy(() -> authenticationApplicationService.refreshSession(
                new RefreshSessionCommand(session.refreshToken())))
                .isInstanceOf(AuthenticationFailedException.class);
    }
}
