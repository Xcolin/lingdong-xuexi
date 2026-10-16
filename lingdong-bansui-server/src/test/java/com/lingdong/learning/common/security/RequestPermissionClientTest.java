package com.lingdong.learning.common.security;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RequestPermissionClientTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void legacyCallsKeepWebClient() {
        assertThat(RequestPermissionClient.forOperator(1L)).isEqualTo(PermissionClient.WEB);
    }
    @Test void miniappRequestUsesMiniappAndCannotImpersonateAnotherOperator() {
        var user = new AuthenticatedUser(1L, null, "operator", "operator", AuthClientType.MINIAPP, List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        assertThat(RequestPermissionClient.forOperator(1L)).isEqualTo(PermissionClient.MINIAPP);
        assertThatThrownBy(() -> RequestPermissionClient.forOperator(2L))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }
}
