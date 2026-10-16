package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountEmailAuthenticationController {
    private final RegistrationVerificationService verification;
    private final JdbcTemplate jdbc;
    private final AuthenticationApplicationService auth;
    public AccountEmailAuthenticationController(RegistrationVerificationService verification, JdbcTemplate jdbc, AuthenticationApplicationService auth) {
        this.verification=verification; this.jdbc=jdbc; this.auth=auth;
    }
    public record Codes(@NotBlank @Email String email, @NotNull AuthClientType clientType) {}
    public record Login(@NotBlank @Email String email, @NotBlank @Pattern(regexp="[0-9]{6}") String code,
                        @NotNull AuthClientType clientType, @NotBlank @Size(max=128) String deviceId,
                        @NotBlank @Size(max=100) String deviceName) {}
    private static void client(AuthClientType type) {
        if(type!=AuthClientType.WEB && type!=AuthClientType.MINIAPP) throw new IllegalArgumentException("客户端无效");
    }
    @PostMapping("/login-email-codes")
    public void issue(@Valid @RequestBody Codes request, HttpServletRequest servlet) {
        client(request.clientType());
        verification.sendForPurpose("EMAIL",RegistrationVerificationService.normalize("EMAIL",request.email()),servlet.getRemoteAddr(),"ACCOUNT_LOGIN:"+request.clientType().name());
    }
    @PostMapping("/sessions/email")
    public AuthenticatedSession login(@Valid @RequestBody Login request, HttpServletRequest servlet) {
        client(request.clientType());
        String email=RegistrationVerificationService.normalize("EMAIL",request.email());
        verification.consumeForPurpose("EMAIL",email,request.code(),servlet.getRemoteAddr(),"ACCOUNT_LOGIN:"+request.clientType().name());
        List<Long> ids=jdbc.queryForList("select user_id from auth_verified_email where email = ?",Long.class,email);
        if(ids.size()!=1) throw new AuthenticationFailedException();
        return auth.loginExistingVerifiedAccount(ids.get(0),request.clientType(),request.deviceId(),request.deviceName());
    }
}

