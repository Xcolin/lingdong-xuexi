package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.IssueParentMobileManualRecoveryCodeCommand;
import com.lingdong.learning.auth.application.ParentMobileManualRecoveryCommand;
import com.lingdong.learning.auth.application.ParentMobileManualRecoveryService;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.common.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 机构管理员执行家长手机号人工核验换绑的独立接口。 */
@RestController
@RequestMapping("/api/v1/organization-parent-mobile-recoveries")
public class ParentMobileManualRecoveryController {
    private static final String PERMISSION = "PARENT_MOBILE_MANUAL_RECOVERY_MANAGE";

    private final ParentMobileManualRecoveryService recoveryService;
    private final SessionTokenService tokenService;

    public ParentMobileManualRecoveryController(
            ParentMobileManualRecoveryService recoveryService,
            SessionTokenService tokenService
    ) {
        this.recoveryService = recoveryService;
        this.tokenService = tokenService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/candidates")
    public List<ParentMobileManualRecoveryCandidateResponse> candidates(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return recoveryService.listCandidates(currentUser).stream()
                .map(ParentMobileManualRecoveryCandidateResponse::from)
                .toList();
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public ParentSmsCodeResponse issueCode(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ParentMobileManualRecoveryCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        return ParentSmsCodeResponse.from(recoveryService.issueCode(
                currentUser,
                new IssueParentMobileManualRecoveryCodeCommand(
                        request.studentId(), request.parentUserId(), request.newMobile(),
                        tokenService.hash(servletRequest.getRemoteAddr()))));
    }

    @RequirePermission(PERMISSION)
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recover(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ParentMobileManualRecoveryRequest request
    ) {
        recoveryService.recover(currentUser, new ParentMobileManualRecoveryCommand(
                request.studentId(), request.parentUserId(), request.newMobile(),
                request.smsCode(), request.reason(), request.confirmation()));
    }
}
