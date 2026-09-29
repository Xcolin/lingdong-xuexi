package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.growthpoint.application.GrowthRewardExchangeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 小程序学生申请/查询与 Web 主家长处理兑换的独立接口。 */
@RestController
@RequestMapping("/api/v1/reward-exchanges")
public class GrowthRewardExchangeController {
    private final GrowthRewardExchangeService exchangeService;

    public GrowthRewardExchangeController(GrowthRewardExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @RequirePermission("REWARD_EXCHANGE_SELF")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrowthRewardExchangeResponse apply(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateRewardExchangeRequest request
    ) {
        return GrowthRewardExchangeResponse.from(
                exchangeService.apply(currentUser, request.rewardId()));
    }

    @RequirePermission("REWARD_EXCHANGE_SELF")
    @GetMapping("/me")
    public GrowthRewardExchangePageResponse findMine(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthRewardExchangePageResponse.from(
                exchangeService.findMine(currentUser, page, pageSize));
    }

    @RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")
    @GetMapping("/students/{studentId}")
    public GrowthRewardExchangePageResponse findManaged(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthRewardExchangePageResponse.from(
                exchangeService.findManaged(currentUser, studentId, page, pageSize));
    }

    @RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")
    @PostMapping("/{exchangeId}/approve")
    public GrowthRewardExchangeResponse approve(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long exchangeId
    ) {
        return GrowthRewardExchangeResponse.from(
                exchangeService.approve(currentUser, exchangeId));
    }

    @RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")
    @PostMapping("/{exchangeId}/reject")
    public GrowthRewardExchangeResponse reject(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long exchangeId,
            @Valid @RequestBody RejectRewardExchangeRequest request
    ) {
        return GrowthRewardExchangeResponse.from(
                exchangeService.reject(currentUser, exchangeId, request.rejectReason()));
    }

    @RequirePermission("REWARD_EXCHANGE_REVIEW_CHILD")
    @PostMapping("/{exchangeId}/verify")
    public GrowthRewardExchangeResponse verify(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long exchangeId
    ) {
        return GrowthRewardExchangeResponse.from(
                exchangeService.verify(currentUser, exchangeId));
    }
}
