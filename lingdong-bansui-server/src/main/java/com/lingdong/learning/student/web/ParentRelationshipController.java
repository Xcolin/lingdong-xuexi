package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.auth.web.ParentSessionResponse;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.CreateParentRelationshipInvitationCommand;
import com.lingdong.learning.student.application.ParentRelationshipApplicationService;
import com.lingdong.learning.student.application.RespondParentRelationshipInvitationCommand;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 家长当前关系、邀请、解绑与监护权转移入口。 */
@RestController
@RequestMapping("/api/v1")
public class ParentRelationshipController {
    private final ParentRelationshipApplicationService service;
    private final SessionTokenService tokenService;

    public ParentRelationshipController(
            ParentRelationshipApplicationService service,
            SessionTokenService tokenService
    ) {
        this.service = service;
        this.tokenService = tokenService;
    }

    @GetMapping("/students/{studentId}/parent-relationships")
    @RequirePermission("PARENT_RELATIONSHIP_READ")
    public ParentRelationshipResponse getRelationships(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return ParentRelationshipResponse.from(
                service.getRelationships(currentUser.userId(), studentId));
    }

    @GetMapping("/parent-relationships/students")
    @RequirePermission("PARENT_RELATIONSHIP_READ")
    public List<ParentRelationshipStudentResponse> getRelationshipStudents(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return service.getRelationshipStudents(currentUser.userId()).stream()
                .map(ParentRelationshipStudentResponse::from)
                .toList();
    }

    @PostMapping("/students/{studentId}/secondary-parent-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("SECONDARY_PARENT_INVITE_CREATE")
    public ParentRelationshipInvitationResponse createSecondaryInvitation(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestBody ParentRelationshipInvitationRequest request,
            HttpServletRequest servletRequest
    ) {
        return createInvitation(currentUser, studentId, request, servletRequest,
                ParentRelationshipInvitationType.SECONDARY_BIND);
    }

    @DeleteMapping("/students/{studentId}/secondary-parent-relationships/{parentUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("SECONDARY_PARENT_UNBIND")
    public void unbindSecondary(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @PathVariable Long parentUserId
    ) {
        service.unbindSecondary(currentUser.userId(), studentId, parentUserId);
    }

    @DeleteMapping("/students/{studentId}/primary-parent-relationship")
    @RequirePermission("PRIMARY_PARENT_SELF_UNBIND")
    public ParentRelationshipResponse unbindPrimary(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return ParentRelationshipResponse.from(
                service.unbindPrimary(currentUser.userId(), studentId));
    }

    @PostMapping("/students/{studentId}/primary-transfer-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("PRIMARY_PARENT_TRANSFER_CREATE")
    public ParentRelationshipInvitationResponse createPrimaryTransferInvitation(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestBody ParentRelationshipInvitationRequest request,
            HttpServletRequest servletRequest
    ) {
        return createInvitation(currentUser, studentId, request, servletRequest,
                ParentRelationshipInvitationType.PRIMARY_TRANSFER);
    }

    @PostMapping("/parent-relationship-invitations/{invitationId}/acceptance")
    public ParentSessionResponse acceptInvitation(
            @PathVariable Long invitationId,
            @RequestBody RespondParentRelationshipInvitationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ParentSessionResponse.from(service.acceptInvitationAndCreateSession(
                responseCommand(invitationId, request, servletRequest),
                request.deviceId(), request.deviceName()));
    }

    @PostMapping("/parent-relationship-invitations/{invitationId}/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectInvitation(
            @PathVariable Long invitationId,
            @RequestBody RespondParentRelationshipInvitationRequest request,
            HttpServletRequest servletRequest
    ) {
        service.rejectInvitation(responseCommand(invitationId, request, servletRequest));
    }

    private ParentRelationshipInvitationResponse createInvitation(
            AuthenticatedUser currentUser,
            Long studentId,
            ParentRelationshipInvitationRequest request,
            HttpServletRequest servletRequest,
            ParentRelationshipInvitationType type
    ) {
        return ParentRelationshipInvitationResponse.from(service.createInvitation(
                new CreateParentRelationshipInvitationCommand(
                        currentUser.userId(), studentId, request.mobile(), type,
                        request.clientType(), tokenService.hash(servletRequest.getRemoteAddr()))));
    }

    private RespondParentRelationshipInvitationCommand responseCommand(
            Long invitationId,
            RespondParentRelationshipInvitationRequest request,
            HttpServletRequest servletRequest
    ) {
        return new RespondParentRelationshipInvitationCommand(
                invitationId, request.mobile(), request.smsCode(), request.clientType(),
                request.agreementAccepted(), request.agreementVersion(),
                tokenService.hash(servletRequest.getRemoteAddr()));
    }
}
