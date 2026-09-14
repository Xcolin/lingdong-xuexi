package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.growthpoint.application.GrowthRewardExchangeService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 小程序家长兑换处理入口；批准扣分、驳回与核销沿用既有事务状态机。 */
@RestController
@RequestMapping("/api/v1/parent-reward-exchanges")
public class ParentRewardExchangeController {
    private final GrowthRewardExchangeService service;
    public ParentRewardExchangeController(GrowthRewardExchangeService service) { this.service = service; }

    @RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")
    @GetMapping("/students/{studentId}")
    public GrowthRewardExchangePageResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        ParentRewardController.requireMiniappParent(user);
        return GrowthRewardExchangePageResponse.from(service.findManaged(user, studentId, page, pageSize));
    }

    @RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")
    @PostMapping("/{exchangeId}/approve")
    public GrowthRewardExchangeResponse approve(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long exchangeId) {
        ParentRewardController.requireMiniappParent(user);
        return GrowthRewardExchangeResponse.from(service.approve(user, exchangeId));
    }

    @RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")
    @PostMapping("/{exchangeId}/reject")
    public GrowthRewardExchangeResponse reject(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long exchangeId, @Valid @RequestBody RejectRewardExchangeRequest request) {
        ParentRewardController.requireMiniappParent(user);
        return GrowthRewardExchangeResponse.from(service.reject(user, exchangeId, request.rejectReason()));
    }

    @RequirePermission("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD")
    @PostMapping("/{exchangeId}/verify")
    public GrowthRewardExchangeResponse verify(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long exchangeId) {
        ParentRewardController.requireMiniappParent(user);
        return GrowthRewardExchangeResponse.from(service.verify(user, exchangeId));
    }
}
