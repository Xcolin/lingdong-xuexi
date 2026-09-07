package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.ParentAuthContext;
import com.lingdong.learning.auth.application.ParentPhoneAuthenticationService;
import com.lingdong.learning.auth.application.ParentWechatAuthenticationService;
import com.lingdong.learning.auth.application.ParentWechatBindingCommand;
import com.lingdong.learning.auth.application.ParentWechatSessionCommand;
import com.lingdong.learning.auth.application.ParentSmsLoginCommand;
import com.lingdong.learning.auth.application.ParentPasswordResetCommand;
import com.lingdong.learning.auth.application.ParentPasswordLoginCommand;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.ParentAccountCancellationRecord;
import com.lingdong.learning.auth.application.ParentAccountLifecycleService;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.lingdong.learning.auth.application.ParentAuthState;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** 家长手机号认证的公开上下文、验证码发送和会话创建入口。 */
@RestController
@RequestMapping("/api/v1")
public class ParentAuthenticationController {
    private final ParentPhoneAuthenticationService authenticationService;
    private final ParentWechatAuthenticationService wechatAuthenticationService;
    private final ParentAccountLifecycleService accountLifecycleService;
    private final SessionTokenService tokenService;

    public ParentAuthenticationController(
            ParentPhoneAuthenticationService authenticationService,
            ParentWechatAuthenticationService wechatAuthenticationService,
            ParentAccountLifecycleService accountLifecycleService,
            SessionTokenService tokenService
    ) {
        this.authenticationService = authenticationService;
        this.wechatAuthenticationService = wechatAuthenticationService;
        this.accountLifecycleService = accountLifecycleService;
        this.tokenService = tokenService;
    }

    @GetMapping("/public/parent-auth-context")
    public ParentAuthContext getContext() {
        return authenticationService.getPublicContext();
    }

    @GetMapping("/auth/parent-state")
    public ParentAuthState getParentState(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return authenticationService.getParentState(currentUser.userId());
    }

    @PostMapping("/auth/parent-sms-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public ParentSmsCodeResponse issueCode(
            @RequestBody ParentSmsCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        return ParentSmsCodeResponse.from(authenticationService.issueSmsCode(
                request.mobile(), request.purpose(), request.clientType(),
                tokenService.hash(servletRequest.getRemoteAddr())));
    }

    @PostMapping("/auth/parent-sessions/sms")
    public ParentSessionResponse loginBySms(
            @RequestBody ParentSmsLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return ParentSessionResponse.from(authenticationService.loginBySms(new ParentSmsLoginCommand(
                request.mobile(), request.code(), request.clientType(), request.deviceId(), request.deviceName(),
                request.agreementAccepted(), request.agreementVersion(),
                tokenService.hash(servletRequest.getRemoteAddr()))));
    }

    @PostMapping("/auth/parent-sessions/password")
    public ParentSessionResponse loginByPassword(@RequestBody ParentPasswordLoginRequest request) {
        return ParentSessionResponse.from(authenticationService.loginByPassword(new ParentPasswordLoginCommand(
                request.mobile(), request.password(), request.clientType(), request.deviceId(), request.deviceName())));
    }

    @PostMapping("/auth/parent-wechat-sessions")
    public ParentWechatSessionExchangeResponse loginByWechat(
            @RequestBody ParentWechatSessionRequest request
    ) {
        return ParentWechatSessionExchangeResponse.from(wechatAuthenticationService.exchange(
                new ParentWechatSessionCommand(
                        request.temporaryCode(), request.deviceId(), request.deviceName())));
    }

    @PostMapping("/auth/parent-wechat-bindings")
    public ParentSessionResponse bindWechat(
            @RequestBody ParentWechatBindingRequest request,
            HttpServletRequest servletRequest
    ) {
        return ParentSessionResponse.from(wechatAuthenticationService.bind(new ParentWechatBindingCommand(
                request.bindingTicket(), request.mobile(), request.smsCode(), request.deviceId(), request.deviceName(),
                request.agreementAccepted(), request.agreementVersion(),
                tokenService.hash(servletRequest.getRemoteAddr()))));
    }

    @PostMapping("/auth/parent-passwords")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody ParentPasswordRequest request
    ) {
        authenticationService.setPassword(currentUser.userId(), request.password());
    }

    @PostMapping("/auth/parent-password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody ParentPasswordResetRequest request) {
        authenticationService.resetPassword(new ParentPasswordResetCommand(
                request.mobile(), request.code(), request.newPassword(), request.clientType()));
    }

    @PostMapping("/auth/parent-agreement-acceptances")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptAgreement(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody ParentAgreementAcceptanceRequest request,
            HttpServletRequest servletRequest
    ) {
        authenticationService.acceptCurrentAgreement(
                currentUser.userId(), currentUser.clientType(), request.agreementVersion(),
                tokenService.hash(servletRequest.getRemoteAddr()));
    }

    @PostMapping("/parent-onboarding/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeOnboarding(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        authenticationService.completeOnboarding(currentUser.userId());
    }

    @GetMapping("/auth/parent-account-lifecycle")
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public ParentAccountLifecycleResponse getAccountLifecycle(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ParentAccountLifecycleResponse.from(accountLifecycleService.getState(currentUser.userId()));
    }

    @PostMapping("/auth/parent-mobile-change/current-codes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public ParentSmsCodeResponse issueCurrentMobileCode(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ParentSmsCodeResponse.from(accountLifecycleService.issueCurrentMobileCode(
                currentUser.userId(), currentUser.clientType(), tokenService.hash(servletRequest.getRemoteAddr())));
    }

    @PostMapping("/auth/parent-mobile-change-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public ParentMobileChangeTicketResponse verifyCurrentMobile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody ParentMobileChangeVerificationRequest request
    ) {
        return new ParentMobileChangeTicketResponse(accountLifecycleService.verifyCurrentMobile(
                currentUser.userId(), currentUser.clientType(), request.code()));
    }

    @PostMapping("/auth/parent-mobile-change/new-codes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public ParentSmsCodeResponse issueNewMobileCode(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody ParentMobileChangeNewCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        return ParentSmsCodeResponse.from(accountLifecycleService.issueNewMobileCode(
                currentUser.userId(), currentUser.clientType(), request.ticket(), request.newMobile(),
                tokenService.hash(servletRequest.getRemoteAddr())));
    }

    @PostMapping("/auth/parent-mobile-changes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public void changeMobile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody ParentMobileChangeRequest request
    ) {
        accountLifecycleService.changeMobile(
                currentUser.userId(), currentUser.clientType(), request.ticket(), request.newMobile(), request.code());
    }

    @PostMapping("/auth/parent-account-cancellation-codes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public ParentSmsCodeResponse issueCancellationCode(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ParentSmsCodeResponse.from(accountLifecycleService.issueCancellationCode(
                currentUser.userId(), currentUser.clientType(), tokenService.hash(servletRequest.getRemoteAddr())));
    }

    @PostMapping("/auth/parent-account-cancellations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public ParentAccountCancellationResponse requestCancellation(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody ParentAccountCancellationRequest request
    ) {
        ParentAccountCancellationRecord record = accountLifecycleService.requestCancellation(
                currentUser.userId(), currentUser.clientType(), request.code(), request.confirmation());
        return ParentAccountCancellationResponse.from(record);
    }

    @DeleteMapping("/auth/parent-account-cancellations/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("PARENT_ACCOUNT_LIFECYCLE_MANAGE")
    public void revokeCancellation(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        accountLifecycleService.revokeCancellation(currentUser.userId());
    }
}
