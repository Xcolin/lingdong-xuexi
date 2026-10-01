package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.AuthenticatedSession;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.AccountSecurityEventService;
import com.lingdong.learning.auth.application.AccountSecurityEventView;
import com.lingdong.learning.auth.application.DeviceSession;
import com.lingdong.learning.auth.application.PasswordLoginCommand;
import com.lingdong.learning.auth.application.OrganizationPasswordLoginCommand;
import com.lingdong.learning.auth.application.RefreshSessionCommand;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供平台账号真实认证、当前身份和设备会话管理接口。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationApplicationService authenticationApplicationService;
    private final AccountSecurityEventService accountSecurityEventService;
    private final PermissionDecisionService permissionDecisionService;

    public AuthenticationController(
            AuthenticationApplicationService authenticationApplicationService,
            AccountSecurityEventService accountSecurityEventService,
            PermissionDecisionService permissionDecisionService
    ) {
        this.authenticationApplicationService = authenticationApplicationService;
        this.accountSecurityEventService = accountSecurityEventService;
        this.permissionDecisionService = permissionDecisionService;
    }

    @PostMapping("/sessions/password")
    public SessionResponse loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return toSessionResponse(authenticationApplicationService.loginByPassword(new PasswordLoginCommand(
                request.username(), request.password(), request.deviceId(), request.deviceName()
        )));
    }

    @PostMapping("/organization-sessions/password")
    public SessionResponse loginOrganizationByPassword(
            @Valid @RequestBody OrganizationPasswordLoginRequest request
    ) {
        return toSessionResponse(authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        request.username(), request.password(), request.deviceId(), request.deviceName())));
    }

    @PostMapping("/sessions/refresh")
    public SessionResponse refreshSession(@Valid @RequestBody RefreshSessionRequest request) {
        return toSessionResponse(authenticationApplicationService.refreshSession(new RefreshSessionCommand(request.refreshToken())));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authenticationApplicationService.changePassword(currentUser.userId(), currentUser.sessionId(),
                request.oldPassword(), request.newPassword());
    }

    @DeleteMapping("/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutCurrentSession(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        authenticationApplicationService.logoutCurrentSession(currentUser.userId(), currentUser.sessionId());
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return new CurrentUserResponse(currentUser.userId(), currentUser.sessionId(), currentUser.username(),
                currentUser.displayName(), currentUser.clientType(), currentUser.roleCodes(),
                permissionDecisionService.findAllowedCodes(
                        currentUser.userId(), PermissionClient.valueOf(currentUser.clientType().name())));
    }

    @GetMapping("/devices")
    public List<DeviceSessionResponse> listCurrentUserDevices(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return authenticationApplicationService.listCurrentUserDevices(currentUser.userId()).stream()
                .map(session -> toDeviceSessionResponse(session, currentUser.sessionId()))
                .toList();
    }

    @DeleteMapping("/devices/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOutDevice(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long sessionId) {
        authenticationApplicationService.signOutDevice(currentUser.userId(), sessionId);
    }

    @PostMapping("/devices/sign-out-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOutAllDevices(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        authenticationApplicationService.signOutAllDevices(currentUser.userId(), currentUser.sessionId());
    }

    @GetMapping("/security-events")
    public List<AccountSecurityEventResponse> listSecurityEvents(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return accountSecurityEventService.findRecent(currentUser.userId(), unreadOnly).stream()
                .map(this::toSecurityEventResponse)
                .toList();
    }

    @PostMapping("/security-events/{eventId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSecurityEventRead(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long eventId
    ) {
        accountSecurityEventService.markRead(currentUser.userId(), eventId);
    }

    @PostMapping("/security-events/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllSecurityEventsRead(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        accountSecurityEventService.markAllRead(currentUser.userId());
    }

    private SessionResponse toSessionResponse(AuthenticatedSession session) {
        return new SessionResponse(session.sessionId(), session.accessToken(), session.refreshToken(),
                session.accessExpiresAt(), session.refreshExpiresAt());
    }

    private DeviceSessionResponse toDeviceSessionResponse(DeviceSession session, Long currentSessionId) {
        return new DeviceSessionResponse(session.id(), session.clientType(), session.deviceName(),
                session.id().equals(currentSessionId),
                session.accessExpiresAt(), session.refreshExpiresAt(), session.lastActiveAt());
    }

    private AccountSecurityEventResponse toSecurityEventResponse(AccountSecurityEventView event) {
        return new AccountSecurityEventResponse(
                event.id(), event.eventType(), event.riskLevel(), event.clientType(), event.deviceName(),
                event.status(), event.occurredAt(), event.readAt());
    }
}
