package com.lingdong.learning.auth.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationApplicationServiceTest {
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void allowsOnlySystemAdministratorsToSetAStrongPasswordForPlatformUsers() {
        User administrator = createUserWithRole("auth_password_admin", "认证密码管理员", "SYS_ADMIN");
        User ordinaryUser = createUser("auth_password_ordinary", "认证普通用户", UserType.PLATFORM);
        User platformUser = createUser("auth_password_target", "认证目标用户", UserType.PLATFORM);
        User organizationUser = createUser("auth_password_org", "认证机构用户", UserType.ORGANIZATION);

        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), platformUser.id(), "Password123"
        ));

        String passwordHash = userMapper.findById(platformUser.id()).passwordHash();
        assertThat(passwordHash).startsWith("$2").isNotEqualTo("Password123");
        assertThatThrownBy(() -> authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(ordinaryUser.id(), platformUser.id(), "Password456")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统管理员");
        assertThatThrownBy(() -> authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(administrator.id(), organizationUser.id(), "Password456")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("平台账号");
        assertThatThrownBy(() -> authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(administrator.id(), platformUser.id(), "weakpass")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码");
    }

    @Test
    void createsRevocableSessionAndRejectsInvalidPasswordOrDisabledUser() {
        User administrator = createUserWithRole("auth_login_admin", "认证登录管理员", "SYS_ADMIN");
        User platformUser = createUser("auth_login_user", "认证登录用户", UserType.PLATFORM);
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), platformUser.id(), "Password123"
        ));

        AuthenticatedSession session = authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                "auth_login_user", "Password123", "web-device-001", "测试浏览器"
        ));

        assertThat(Long.toString(session.sessionId())).hasSize(19);
        assertThat(session.accessToken()).isNotBlank().isNotEqualTo(session.refreshToken());
        assertThat(jdbcTemplate.queryForObject(
                "select access_token_hash from auth_device_session where id = ?", String.class, session.sessionId()
        )).hasSize(64).isNotEqualTo(session.accessToken());
        assertThat(authenticationApplicationService.authenticateAccessToken(session.accessToken()).userId())
                .isEqualTo(platformUser.id());
        assertThatThrownBy(() -> authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                "auth_login_user", "WrongPassword1", "web-device-002", "错误密码浏览器"
        ))).isInstanceOf(AuthenticationFailedException.class);

        jdbcTemplate.update("update sys_user set status = 'DISABLED' where id = ?", platformUser.id());
        assertThatThrownBy(() -> authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                "auth_login_user", "Password123", "web-device-003", "停用用户浏览器"
        ))).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void rotatesRefreshTokensAndRevokesOnlySessionsOwnedByTheCurrentUser() {
        User administrator = createUserWithRole("auth_session_admin", "认证会话管理员", "SYS_ADMIN");
        User owner = createUser("auth_session_owner", "会话归属用户", UserType.PLATFORM);
        User otherUser = createUser("auth_session_other", "其他会话用户", UserType.PLATFORM);
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), owner.id(), "Password123"
        ));
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), otherUser.id(), "Password456"
        ));
        AuthenticatedSession original = authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                "auth_session_owner", "Password123", "owner-device-001", "归属用户浏览器"
        ));
        AuthenticatedSession otherSession = authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                "auth_session_other", "Password456", "other-device-001", "其他用户浏览器"
        ));

        AuthenticatedSession refreshed = authenticationApplicationService.refreshSession(new RefreshSessionCommand(original.refreshToken()));

        assertThat(refreshed.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThatThrownBy(() -> authenticationApplicationService.refreshSession(new RefreshSessionCommand(original.refreshToken())))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> authenticationApplicationService.authenticateAccessToken(original.accessToken()))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> authenticationApplicationService.signOutDevice(owner.id(), otherSession.sessionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无权");

        authenticationApplicationService.logoutCurrentSession(owner.id(), refreshed.sessionId());

        assertThatThrownBy(() -> authenticationApplicationService.authenticateAccessToken(refreshed.accessToken()))
                .isInstanceOf(AuthenticationFailedException.class);

        AuthenticatedSession anotherOwnerSession = authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                "auth_session_owner", "Password123", "owner-device-002", "归属用户第二浏览器"
        ));
        authenticationApplicationService.signOutAllDevices(owner.id());

        assertThatThrownBy(() -> authenticationApplicationService.authenticateAccessToken(anotherOwnerSession.accessToken()))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThat(authenticationApplicationService.authenticateAccessToken(otherSession.accessToken()).userId())
                .isEqualTo(otherUser.id());
    }

    @Test
    void keepsTheRefreshTokenUsableWhenOnlyTheAccessTokenHasExpired() {
        User administrator = createUserWithRole("auth_expiry_admin", "认证过期管理员", "SYS_ADMIN");
        User platformUser = createUser("auth_expiry_user", "认证过期用户", UserType.PLATFORM);
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), platformUser.id(), "Password123"
        ));
        AuthenticatedSession session = authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                "auth_expiry_user", "Password123", "expiry-device-001", "过期验证浏览器"
        ));
        jdbcTemplate.update("update auth_device_session set access_expires_at = ? where id = ?",
                java.time.LocalDateTime.now().minusMinutes(1), session.sessionId());

        assertThatThrownBy(() -> authenticationApplicationService.authenticateAccessToken(session.accessToken()))
                .isInstanceOf(AuthenticationFailedException.class);

        AuthenticatedSession refreshed = authenticationApplicationService.refreshSession(new RefreshSessionCommand(session.refreshToken()));

        assertThat(refreshed.accessToken()).isNotEqualTo(session.accessToken());
        assertThat(authenticationApplicationService.authenticateAccessToken(refreshed.accessToken()).userId())
                .isEqualTo(platformUser.id());
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = createUser(username, displayName, UserType.PLATFORM);
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private User createUser(String username, String displayName, UserType type) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, type));
    }
}
