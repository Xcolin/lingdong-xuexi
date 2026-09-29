package com.lingdong.learning.growthpoint.web;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.growthpoint.application.GrowthReviewSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 本人周报偏好；开启的动态权限由服务层检查，取消不被已撤销的订阅权限阻断。 */
@RestController
@RequestMapping("/api/v1/growth-review-subscriptions/students/{studentId}")
public class GrowthReviewSubscriptionController {
    private final GrowthReviewSubscriptionService service;
    public GrowthReviewSubscriptionController(GrowthReviewSubscriptionService service) { this.service = service; }
    @GetMapping
    public GrowthReviewSubscriptionService.View get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long studentId) {
        return service.get(user, studentId);
    }
    @PutMapping
    public GrowthReviewSubscriptionService.View set(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long studentId,
            @Valid @RequestBody Request request) {
        return service.set(user, studentId, request.enabled(), request.version());
    }
    public record Request(@NotNull Boolean enabled, @NotNull @Min(0) Long version) { }
}
