package com.lingdong.learning.common.security;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.AuthenticationFailedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 从 Bearer 访问凭证解析受控会话，并在请求内建立最小认证主体。 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationApplicationService authenticationApplicationService;
    private final AuthenticationRequiredEntryPoint entryPoint;

    public SessionAuthenticationFilter(
            AuthenticationApplicationService authenticationApplicationService,
            AuthenticationRequiredEntryPoint entryPoint
    ) {
        this.authenticationApplicationService = authenticationApplicationService;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String accessToken = authorization.substring(BEARER_PREFIX.length());
        try {
            AuthenticatedUser user = authenticationApplicationService.authenticateAccessToken(accessToken);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    user.roleCodes().stream().map(roleCode -> new SimpleGrantedAuthority("ROLE_" + roleCode)).toList()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AuthenticationFailedException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new org.springframework.security.authentication.InsufficientAuthenticationException("认证失败"));
        }
    }
}
