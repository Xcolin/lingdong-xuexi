package com.lingdong.learning.auth.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
public class AccountSmsAuthenticationController {
    private final AccountSmsAuthenticationService service;
    private final SessionTokenService tokens;
    public AccountSmsAuthenticationController(AccountSmsAuthenticationService service,SessionTokenService tokens){this.service=service;this.tokens=tokens;}
    public record Codes(@NotBlank @Pattern(regexp="1[3-9][0-9]{9}") String mobile,@NotNull AuthClientType clientType){}
    @PostMapping("/login-sms-codes") public IssuedParentSmsCode issue(@Valid @RequestBody Codes request,HttpServletRequest servlet){return service.issue(request.mobile(),request.clientType(),tokens.hash(servlet.getRemoteAddr()));}
    @PostMapping("/sessions/sms") public AccountSmsAuthenticationService.Result login(@Valid @RequestBody AccountSmsAuthenticationService.Command request){return service.login(request);}
}
