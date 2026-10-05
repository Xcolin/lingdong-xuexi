package com.lingdong.learning.organization.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.organization.application.CreateOrganizationChangeCommand;
import com.lingdong.learning.organization.application.OrganizationChangeApplicationService;
import com.lingdong.learning.organization.application.OrganizationChangeReviewItem;
import com.lingdong.learning.user.application.UserDisplayNameResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 系统管理员提交、系统审核员审核高风险组织变更。 */
@RestController
@RequestMapping("/api/v1/organization-changes")
public class OrganizationChangeController {
    private final OrganizationChangeApplicationService organizationChangeApplicationService;
    private final UserDisplayNameResolver userNames;

    public OrganizationChangeController(
            OrganizationChangeApplicationService organizationChangeApplicationService,
            UserDisplayNameResolver userNames
    ) {
        this.organizationChangeApplicationService = organizationChangeApplicationService;
        this.userNames = userNames;
    }

    @RequirePermission("ORG_NODE_CHANGE_SUBMIT")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationChangeResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateOrganizationChangeRequest request
    ) {
        return OrganizationChangeResponse.from(organizationChangeApplicationService.createAndSubmitItem(
                new CreateOrganizationChangeCommand(
                        currentUser.userId(), request.organizationId(), request.changeType(),
                        request.targetParentId(), request.expectedVersion(), request.reason())), userNames::resolve);
    }

    @RequirePermission(anyOf = {"ORG_NODE_CHANGE_SUBMIT", "ORG_NODE_CHANGE_REVIEW"})
    @GetMapping
    public List<OrganizationChangeResponse> listForReview(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        List<OrganizationChangeReviewItem> items = organizationChangeApplicationService.listChanges(currentUser.userId());
        List<Long> ids = new ArrayList<>();
        for (OrganizationChangeReviewItem item : items) {
            ids.add(item.task().submittedBy());
            ids.add(item.task().reviewedBy());
        }
        Map<Long, String> names = userNames.resolveAll(ids);
        return items.stream()
                .map(item -> OrganizationChangeResponse.from(item, id -> userNames.nameOf(names, id)))
                .toList();
    }

    @RequirePermission("ORG_NODE_CHANGE_REVIEW")
    @PostMapping("/{taskId}/approve")
    public OrganizationChangeResponse approve(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody OrganizationChangeReviewRequest request
    ) {
        organizationChangeApplicationService.approveAndApply(taskId, currentUser.userId(), request.comment());
        return OrganizationChangeResponse.from(
                organizationChangeApplicationService.getChange(currentUser.userId(), taskId), userNames::resolve);
    }

    @RequirePermission("ORG_NODE_CHANGE_REVIEW")
    @PostMapping("/{taskId}/reject")
    public OrganizationChangeResponse reject(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody OrganizationChangeReviewRequest request
    ) {
        organizationChangeApplicationService.reject(taskId, currentUser.userId(), request.comment());
        return OrganizationChangeResponse.from(
                organizationChangeApplicationService.getChange(currentUser.userId(), taskId), userNames::resolve);
    }
}
