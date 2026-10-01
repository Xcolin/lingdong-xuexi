package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChangePasswordControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthenticationApplicationService auth;
    @Autowired UserAccessApplicationService users;
    @Autowired UserMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test void changesOnlyPrincipalPasswordAndRevokesAllTokensWithoutFeatureOrRole() throws Exception {
        User owner = user("password_owner");
        User other = user("password_other");
        AuthenticatedSession first = login(owner, "first");
        AuthenticatedSession second = login(owner, "second");
        AuthenticatedSession otherSession = login(other, "other");
        jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code='ACCOUNT_SECURITY_MANAGEMENT'");
        try {
            mvc.perform(post("/api/v1/auth/password").header("Authorization", "Bearer " + first.accessToken())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                            "oldPassword", "Password123", "newPassword", "Changed123", "userId", other.id(),
                            "sessionId", otherSession.sessionId())))).andExpect(status().isNoContent());
        } finally {
            jdbc.update("update sys_feature_toggle set status='ENABLED' where feature_code='ACCOUNT_SECURITY_MANAGEMENT'");
        }
        assertThat(encoder.matches("Changed123", mapper.findById(owner.id()).passwordHash())).isTrue();
        assertThat(encoder.matches("Password123", mapper.findById(other.id()).passwordHash())).isTrue();
        for (AuthenticatedSession session : new AuthenticatedSession[]{first, second}) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isUnauthorized());
            assertThatThrownBy(() -> auth.refreshSession(new RefreshSessionCommand(session.refreshToken())))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
        assertThatThrownBy(() -> login(owner, "old")).isInstanceOf(AuthenticationFailedException.class);
        assertThat(auth.loginByPassword(new PasswordLoginCommand(owner.username(), "Changed123", "new", "browser"))).isNotNull();
        assertThat(auth.authenticateAccessToken(otherSession.accessToken()).userId()).isEqualTo(other.id());
    }

    @Test void rejectsWrongOldWeakAndSamePasswordsWithoutChangingHashOrSession() throws Exception {
        User owner = user("password_invalid");
        AuthenticatedSession session = login(owner, "invalid");
        String hash = mapper.findById(owner.id()).passwordHash();
        for (String[] pair : new String[][]{{"Wrong123", "Changed123"}, {"Password123", "short1"},
                {"Password123", "abcdefgh"}, {"Password123", "Password123"}, {"Password123", "Invalid123!"}}) {
            mvc.perform(post("/api/v1/auth/password").header("Authorization", "Bearer " + session.accessToken())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                            "oldPassword", pair[0], "newPassword", pair[1])))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REJECTED"));
            if (pair[0].equals("Wrong123")) {
                mvc.perform(post("/api/v1/auth/password").header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"Wrong123\",\"newPassword\":\"Changed123\"}"))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("旧密码错误"));
            }
            assertThat(mapper.findById(owner.id()).passwordHash()).isEqualTo(hash);
            assertThat(auth.authenticateAccessToken(session.accessToken()).userId()).isEqualTo(owner.id());
        }
    }

    @Test void rejectsAnonymousAndDisabledOrLockedAccounts() throws Exception {
        mvc.perform(post("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"Password123\",\"newPassword\":\"Changed123\"}"))
                .andExpect(status().isUnauthorized());
        for (UserStatus state : new UserStatus[]{UserStatus.DISABLED, UserStatus.LOCKED}) {
            User owner = user("password_status_" + state);
            AuthenticatedSession session = login(owner, "status");
            String hash = mapper.findById(owner.id()).passwordHash();
            mapper.updateStatus(owner.id(), state);
            assertThatThrownBy(() -> auth.changePassword(owner.id(), session.sessionId(), "Password123", "Changed123"))
                    .isInstanceOf(IllegalArgumentException.class);
            mvc.perform(post("/api/v1/auth/password").header("Authorization", "Bearer " + session.accessToken())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"Password123\",\"newPassword\":\"Changed123\"}"))
                    .andExpect(status().isUnauthorized());
            assertThat(mapper.findById(owner.id()).passwordHash()).isEqualTo(hash);
        }
    }

    @Test void preventsStaleHashOverwriteAndRejectsForeignOrRevokedSession() {
        User owner = user("password_atomic");
        User other = user("password_foreign");
        AuthenticatedSession session = login(owner, "atomic");
        AuthenticatedSession foreign = login(other, "foreign");
        String originalHash = mapper.findById(owner.id()).passwordHash();
        assertThatThrownBy(() -> auth.changePassword(owner.id(), foreign.sessionId(), "Password123", "Changed123"))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        String changedHash = encoder.encode("Changed123");
        assertThat(mapper.updatePasswordHashIfExpected(owner.id(), originalHash, changedHash)).isEqualTo(1);
        assertThat(mapper.updatePasswordHashIfExpected(owner.id(), originalHash, encoder.encode("Stale1234"))).isZero();
        assertThat(mapper.findById(owner.id()).passwordHash()).isEqualTo(changedHash);
        auth.logoutCurrentSession(owner.id(), session.sessionId());
        assertThatThrownBy(() -> auth.changePassword(owner.id(), session.sessionId(), "Changed123", "Another123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User user(String username) {
        User user = users.createUser(new CreateUserCommand(username, username, null, UserType.PLATFORM));
        mapper.updatePasswordHash(user.id(), encoder.encode("Password123"));
        return user;
    }

    @Test void serializesOldPasswordLoginWithPasswordChangeOnTheUserRow() throws Exception {
        User owner = user("password_concurrent");
        AuthenticatedSession current = login(owner, "current");
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<AuthenticatedSession>> future =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                mapper.findByIdForUpdate(owner.id());
                future.set(executor.submit(() -> {
                    started.countDown();
                    return login(owner, "concurrent-old-password");
                }));
                try {
                    assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> future.get().get(1, java.util.concurrent.TimeUnit.SECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                    auth.changePassword(owner.id(), current.sessionId(), "Password123", "Changed123");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            });
            assertThatThrownBy(() -> future.get().get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(AuthenticationFailedException.class);
            assertThatThrownBy(() -> auth.authenticateAccessToken(current.accessToken()))
                    .isInstanceOf(AuthenticationFailedException.class);
            assertThat(jdbc.queryForObject("select count(*) from auth_device_session where user_id=? and status='ACTIVE'",
                    Integer.class, owner.id())).isZero();
        } finally {
            executor.shutdownNow();
        }
    }
    private AuthenticatedSession login(User user, String device) {
        return auth.loginByPassword(new PasswordLoginCommand(user.username(), "Password123", device, "browser"));
    }
}
