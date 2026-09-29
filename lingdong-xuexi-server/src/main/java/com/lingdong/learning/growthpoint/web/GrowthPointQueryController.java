package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.growthpoint.application.GrowthPointQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学生本人和主家长的积分账户、不可变台账查询入口。 */
@RestController
@RequestMapping("/api/v1/growth-points")
public class GrowthPointQueryController {
    private final GrowthPointQueryService queryService;

    public GrowthPointQueryController(GrowthPointQueryService queryService) {
        this.queryService = queryService;
    }

    @RequirePermission("GROWTH_POINT_READ_SELF")
    @GetMapping("/me/account")
    public GrowthPointAccountResponse findMyAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return GrowthPointAccountResponse.from(queryService.findMyAccount(currentUser));
    }

    @RequirePermission("GROWTH_POINT_READ_SELF")
    @GetMapping("/me/ledgers")
    public GrowthPointLedgerPageResponse findMyLedgers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthPointLedgerPageResponse.from(
                queryService.findMyLedgers(currentUser, page, pageSize));
    }

    @RequirePermission("GROWTH_POINT_READ_CHILD")
    @GetMapping("/students")
    public List<GrowthPointStudentOptionResponse> findParentStudents(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return queryService.findParentStudents(currentUser).stream()
                .map(GrowthPointStudentOptionResponse::from)
                .toList();
    }

    @RequirePermission("GROWTH_POINT_READ_CHILD")
    @GetMapping("/students/{studentId}/account")
    public GrowthPointAccountResponse findChildAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return GrowthPointAccountResponse.from(
                queryService.findChildAccount(currentUser, studentId));
    }

    @RequirePermission("GROWTH_POINT_READ_CHILD")
    @GetMapping("/students/{studentId}/ledgers")
    public GrowthPointLedgerPageResponse findChildLedgers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthPointLedgerPageResponse.from(
                queryService.findChildLedgers(currentUser, studentId, page, pageSize));
    }
}
