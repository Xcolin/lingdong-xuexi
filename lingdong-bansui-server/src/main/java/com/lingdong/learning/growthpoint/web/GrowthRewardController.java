package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.growthpoint.application.GrowthRewardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Web 主家长管理奖励和小程序学生浏览本人奖励的独立入口。 */
@RestController
@RequestMapping("/api/v1/rewards")
public class GrowthRewardController {
    private final GrowthRewardService rewardService;

    public GrowthRewardController(GrowthRewardService rewardService) {
        this.rewardService = rewardService;
    }

    @RequirePermission("REWARD_MANAGE_CHILD")
    @GetMapping("/students/{studentId}")
    public GrowthRewardPageResponse findManaged(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthRewardPageResponse.from(
                rewardService.findManaged(currentUser, studentId, page, pageSize));
    }

    @RequirePermission("REWARD_MANAGE_CHILD")
    @PostMapping("/students/{studentId}")
    @ResponseStatus(HttpStatus.CREATED)
    public GrowthRewardResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @Valid @RequestBody SaveGrowthRewardRequest request
    ) {
        return GrowthRewardResponse.from(
                rewardService.create(currentUser, studentId, request.toCommand()));
    }

    @RequirePermission("REWARD_MANAGE_CHILD")
    @PatchMapping("/{rewardId}")
    public GrowthRewardResponse update(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long rewardId,
            @Valid @RequestBody SaveGrowthRewardRequest request
    ) {
        return GrowthRewardResponse.from(
                rewardService.update(currentUser, rewardId, request.toCommand()));
    }

    @RequirePermission("REWARD_MANAGE_CHILD")
    @DeleteMapping("/{rewardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long rewardId
    ) {
        rewardService.delete(currentUser, rewardId);
    }

    @RequirePermission("REWARD_EXCHANGE_SELF")
    @GetMapping("/me")
    public GrowthRewardPageResponse findMine(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthRewardPageResponse.from(rewardService.findMine(currentUser, page, pageSize));
    }

    @RequirePermission("REWARD_EXCHANGE_SELF")
    @GetMapping("/me/summary")
    public GrowthRewardAccountSummaryResponse findMyAccountSummary(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return GrowthRewardAccountSummaryResponse.from(
                rewardService.findMyAccountSummary(currentUser));
    }
}
