package com.lingdong.learning.organization.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.organization.application.OrganizationWorkbenchQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供当前机构管理员小程序工作台的最小上下文。 */
@RestController
@RequestMapping("/api/v1/organization-workbench")
public class OrganizationWorkbenchController {
    private final OrganizationWorkbenchQueryService queryService;

    public OrganizationWorkbenchController(OrganizationWorkbenchQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/context")
    public OrganizationWorkbenchContextResponse context(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return OrganizationWorkbenchContextResponse.from(queryService.getContext(currentUser));
    }
}
