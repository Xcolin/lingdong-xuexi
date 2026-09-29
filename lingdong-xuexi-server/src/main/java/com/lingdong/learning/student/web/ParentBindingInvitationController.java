package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.ParentBindingInvitationApplicationService;
import com.lingdong.learning.student.application.RespondParentBindingInvitationCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 由既有家长账号接受或拒绝机构发出的绑定邀请。 */
@RestController
@RequestMapping("/api/v1/parent-invitations")
public class ParentBindingInvitationController {
    private final ParentBindingInvitationApplicationService parentBindingInvitationApplicationService;

    public ParentBindingInvitationController(
            ParentBindingInvitationApplicationService parentBindingInvitationApplicationService
    ) {
        this.parentBindingInvitationApplicationService = parentBindingInvitationApplicationService;
    }

    @RequirePermission("STUDENT_PARENT_INVITE_RESPOND")
    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RespondParentBindingInvitationRequest request
    ) {
        parentBindingInvitationApplicationService.accept(
                currentUser, new RespondParentBindingInvitationCommand(id, request.acceptToken())
        );
    }

    @RequirePermission("STUDENT_PARENT_INVITE_RESPOND")
    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RespondParentBindingInvitationRequest request
    ) {
        parentBindingInvitationApplicationService.reject(
                currentUser, new RespondParentBindingInvitationCommand(id, request.acceptToken())
        );
    }
}
