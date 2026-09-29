package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrganizationMiniappAuthenticationTest {
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private DeviceSessionMapper deviceSessionMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @SpyBean private PasswordEncoder passwordEncoder;
    @Autowired private IdGenerator idGenerator;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void logsInOnlyAnEnabledOrganizationAdministratorWithAnEnabledManagedOrganization() {
        User administrator = createOrganizationUser("org_mini_valid", "有效机构管理员", "ValidPass123!");
        assignOrganizationAdministratorRole(administrator);
        Organization school = createSchool("ORG_MINI_VALID", "机构认证学校");
        assignManagedOrganization(administrator, school);

        AuthenticatedSession session = authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        administrator.username(), "ValidPass123!", "organization-device", "机构管理员手机"));

        assertThat(deviceSessionMapper.findById(session.sessionId()).clientType())
                .isEqualTo(AuthClientType.MINIAPP);
        assertThat(authenticationApplicationService.authenticateAccessToken(session.accessToken()).userId())
                .isEqualTo(administrator.id());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from auth_security_event
                where user_id = ? and session_id = ? and event_type = 'NEW_DEVICE_LOGIN'
                """, Integer.class, administrator.id(), session.sessionId())).isEqualTo(1);
    }

    @Test
    void logsInAnEnabledTeacherWithAnActiveClass() {
        User teacher = createOrganizationUser("teacher_mini_valid", "有效班级教师", "ValidPass123!");
        Role teacherRole = roleMapper.findByCode("TEACHER");
        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(teacher.id(), teacherRole.id(), null));
        Organization school = createSchool("TEACHER_MINI_SCHOOL", "教师认证学校");
        Organization teacherClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "TEACHER_MINI_CLASS", "教师认证班级", "CLASS", school.id(), 10));
        jdbcTemplate.update("""
                insert into edu_teacher_class (
                    id, teacher_user_id, class_organization_id, status
                ) values (?, ?, ?, 'ACTIVE')
                """, idGenerator.nextId(), teacher.id(), teacherClass.id());

        AuthenticatedSession session = authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        teacher.username(), "ValidPass123!", "teacher-device", "教师手机"));

        AuthenticatedUser authenticated = authenticationApplicationService
                .authenticateAccessToken(session.accessToken());
        assertThat(authenticated.userId()).isEqualTo(teacher.id());
        assertThat(authenticated.clientType()).isEqualTo(AuthClientType.MINIAPP);
        assertThat(authenticated.roleCodes()).contains("TEACHER");
    }

    @Test
    void rejectsWrongPasswordMissingRoleAndMissingEnabledManagedOrganizationWithOneFailureType() {
        User valid = createOrganizationUser("org_mini_wrong_password", "错误密码管理员", "ValidPass123!");
        assignOrganizationAdministratorRole(valid);
        assignManagedOrganization(valid, createSchool("ORG_MINI_WRONG_PASSWORD", "错误密码学校"));

        User withoutRole = createOrganizationUser("org_mini_without_role", "无角色机构用户", "ValidPass123!");
        assignManagedOrganization(withoutRole, createSchool("ORG_MINI_WITHOUT_ROLE", "无角色学校"));

        User withoutOrganization = createOrganizationUser(
                "org_mini_without_organization", "无管理组织机构用户", "ValidPass123!");
        assignOrganizationAdministratorRole(withoutOrganization);

        User disabledOrganization = createOrganizationUser(
                "org_mini_disabled_organization", "停用组织机构用户", "ValidPass123!");
        assignOrganizationAdministratorRole(disabledOrganization);
        Organization stoppedSchool = createSchool("ORG_MINI_DISABLED", "停用机构认证学校");
        assignManagedOrganization(disabledOrganization, stoppedSchool);
        jdbcTemplate.update("update sys_organization set status = 'DISABLED' where id = ?", stoppedSchool.id());

        assertAuthenticationFailed(valid.username(), "WrongPass123!");
        assertAuthenticationFailed(withoutRole.username(), "ValidPass123!");
        assertAuthenticationFailed(withoutOrganization.username(), "ValidPass123!");
        assertAuthenticationFailed(disabledOrganization.username(), "ValidPass123!");
    }

    @Test
    void rejectsNonOrganizationAndDisabledUsers() {
        User platformUser = createUser("org_mini_platform", "平台用户", UserType.PLATFORM, "ValidPass123!");
        User familyUser = createUser("org_mini_family", "家长用户", UserType.FAMILY, "ValidPass123!");
        User studentUser = createUser("org_mini_student", "学生用户", UserType.STUDENT, "ValidPass123!");
        User disabledUser = createOrganizationUser("org_mini_disabled_user", "停用机构用户", "ValidPass123!");
        assignOrganizationAdministratorRole(disabledUser);
        assignManagedOrganization(disabledUser, createSchool("ORG_MINI_DISABLED_USER", "停用用户学校"));
        jdbcTemplate.update("update sys_user set status = 'DISABLED' where id = ?", disabledUser.id());

        assertAuthenticationFailed(platformUser.username(), "ValidPass123!");
        assertAuthenticationFailed(familyUser.username(), "ValidPass123!");
        assertAuthenticationFailed(studentUser.username(), "ValidPass123!");
        assertAuthenticationFailed(disabledUser.username(), "ValidPass123!");
    }

    @Test
    void verifiesAPasswordHashEvenWhenTheOrganizationAccountDoesNotExist() {
        clearInvocations(passwordEncoder);

        assertAuthenticationFailed("org_mini_missing_account", "UnknownPass123!");

        verify(passwordEncoder).matches(anyString(), anyString());
    }

    @Test
    void invalidatesExistingMiniappSessionsWhenTheFeatureIsDisabledWithoutAffectingWeb() {
        ValidOrganizationAdministrator fixture = createValidAdministrator(
                "org_mini_feature_off", "功能关闭管理员", "ORG_MINI_FEATURE_OFF", "功能关闭学校");
        AuthenticatedSession accessSession = login(fixture.user(), "feature-access");
        AuthenticatedSession refreshSession = login(fixture.user(), "feature-refresh");
        AuthenticatedSession webSession = authenticationApplicationService.loginByPassword(
                new PasswordLoginCommand(fixture.user().username(), "ValidPass123!", "feature-web", "机构浏览器"));

        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'ORGANIZATION_MINIAPP_AUTH' and scope_key = 'GLOBAL'
                """);

        assertSessionInvalid(accessSession, refreshSession);
        assertThat(authenticationApplicationService.authenticateAccessToken(webSession.accessToken()).userId())
                .isEqualTo(fixture.user().id());
    }

    @Test
    void invalidatesExistingMiniappSessionsWhenRoleOrManagedOrganizationIsRemoved() {
        ValidOrganizationAdministrator roleFixture = createValidAdministrator(
                "org_mini_role_removed", "角色移除管理员", "ORG_MINI_ROLE_REMOVED", "角色移除学校");
        AuthenticatedSession roleAccess = login(roleFixture.user(), "role-access");
        AuthenticatedSession roleRefresh = login(roleFixture.user(), "role-refresh");
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", roleFixture.user().id());
        assertSessionInvalid(roleAccess, roleRefresh);

        ValidOrganizationAdministrator relationFixture = createValidAdministrator(
                "org_mini_relation_removed", "关系移除管理员", "ORG_MINI_RELATION_REMOVED", "关系移除学校");
        AuthenticatedSession relationAccess = login(relationFixture.user(), "relation-access");
        AuthenticatedSession relationRefresh = login(relationFixture.user(), "relation-refresh");
        jdbcTemplate.update("delete from sys_organization_admin where user_id = ?", relationFixture.user().id());
        assertSessionInvalid(relationAccess, relationRefresh);
    }

    @Test
    void invalidatesExistingMiniappSessionsWhenTheOrganizationAdministratorRoleIsDisabled() {
        ValidOrganizationAdministrator fixture = createValidAdministrator(
                "org_mini_role_disabled", "角色停用管理员", "ORG_MINI_ROLE_DISABLED", "角色停用学校");
        AuthenticatedSession accessSession = login(fixture.user(), "disabled-role-access");
        AuthenticatedSession refreshSession = login(fixture.user(), "disabled-role-refresh");
        jdbcTemplate.update("update sys_role set status = 'DISABLED' where role_code = 'ORG_ADMIN'");

        assertSessionInvalid(accessSession, refreshSession);
    }

    @Test
    void invalidatesExistingMiniappSessionsWhenOrganizationOrUserIsDisabled() {
        ValidOrganizationAdministrator organizationFixture = createValidAdministrator(
                "org_mini_org_disabled", "组织停用管理员", "ORG_MINI_ORG_DISABLED", "会话组织停用学校");
        AuthenticatedSession organizationAccess = login(organizationFixture.user(), "organization-access");
        AuthenticatedSession organizationRefresh = login(organizationFixture.user(), "organization-refresh");
        jdbcTemplate.update("update sys_organization set status = 'DISABLED' where id = ?",
                organizationFixture.organization().id());
        assertSessionInvalid(organizationAccess, organizationRefresh);

        ValidOrganizationAdministrator userFixture = createValidAdministrator(
                "org_mini_user_disabled", "用户停用管理员", "ORG_MINI_USER_DISABLED", "会话用户停用学校");
        AuthenticatedSession userAccess = login(userFixture.user(), "user-access");
        AuthenticatedSession userRefresh = login(userFixture.user(), "user-refresh");
        jdbcTemplate.update("update sys_user set status = 'DISABLED' where id = ?", userFixture.user().id());
        assertSessionInvalid(userAccess, userRefresh);
    }

    @Test
    void rejectsLoginAndInvalidatesSessionWhenManagedOrganizationIsEffectivelyDisabled() {
        ValidOrganizationAdministrator fixture = createValidAdministrator(
                "org_mini_effective_disabled", "上级停用管理员",
                "ORG_MINI_EFFECTIVE_DISABLED", "上级停用学校");
        AuthenticatedSession accessSession = login(fixture.user(), "effective-disabled-access");
        AuthenticatedSession refreshSession = login(fixture.user(), "effective-disabled-refresh");

        jdbcTemplate.update(
                "update sys_organization set effective_status = 'DISABLED' where id = ?",
                fixture.organization().id());

        assertThat(jdbcTemplate.queryForObject(
                "select status from sys_organization where id = ?",
                String.class, fixture.organization().id())).isEqualTo("ENABLED");
        assertSessionInvalid(accessSession, refreshSession);
        assertAuthenticationFailed(fixture.user().username(), "ValidPass123!");
    }

    private ValidOrganizationAdministrator createValidAdministrator(
            String username,
            String displayName,
            String organizationCode,
            String organizationName
    ) {
        User user = createOrganizationUser(username, displayName, "ValidPass123!");
        assignOrganizationAdministratorRole(user);
        Organization organization = createSchool(organizationCode, organizationName);
        assignManagedOrganization(user, organization);
        return new ValidOrganizationAdministrator(user, organization);
    }

    private AuthenticatedSession login(User user, String deviceId) {
        return authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        user.username(), "ValidPass123!", deviceId, "机构管理员手机"));
    }

    private void assertSessionInvalid(AuthenticatedSession accessSession, AuthenticatedSession refreshSession) {
        assertThatThrownBy(() -> authenticationApplicationService.authenticateAccessToken(accessSession.accessToken()))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> authenticationApplicationService.refreshSession(
                new RefreshSessionCommand(refreshSession.refreshToken())))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    private void assertAuthenticationFailed(String username, String password) {
        assertThatThrownBy(() -> authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(username, password, "rejected-device", "拒绝登录设备")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    private User createOrganizationUser(String username, String displayName, String password) {
        return createUser(username, displayName, UserType.ORGANIZATION, password);
    }

    private User createUser(String username, String displayName, UserType type, String password) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, type));
        assertThat(userMapper.updatePasswordHash(user.id(), passwordEncoder.encode(password))).isEqualTo(1);
        return userMapper.findById(user.id());
    }

    private void assignOrganizationAdministratorRole(User user) {
        Role role = roleMapper.findByCode("ORG_ADMIN");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
    }

    private Organization createSchool(String code, String name) {
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(code, name, "SCHOOL", null, 10));
    }

    private void assignManagedOrganization(User user, Organization organization) {
        assertThat(organizationAdminMapper.insert(idGenerator.nextId(), user.id(), organization.id())).isEqualTo(1);
    }

    private record ValidOrganizationAdministrator(User user, Organization organization) { }
}
