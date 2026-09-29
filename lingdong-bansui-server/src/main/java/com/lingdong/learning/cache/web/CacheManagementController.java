package com.lingdong.learning.cache.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.cache.application.CacheOperationApplicationService;
import com.lingdong.learning.cache.application.CreateHighRiskCacheOperationCommand;
import com.lingdong.learning.cache.application.ExecuteCacheOperationCommand;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.feature.application.FeatureAccessService;
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

import java.util.List;

/** 提供受功能开关和动态 RBAC 共同保护的缓存管理 Web 接口。 */
@RestController
@RequestMapping("/api/v1/cache-management")
public class CacheManagementController {
    private static final String FEATURE_CODE = "CACHE_MANAGEMENT";

    private final CacheOperationApplicationService cacheOperationApplicationService;
    private final FeatureAccessService featureAccessService;

    public CacheManagementController(
            CacheOperationApplicationService cacheOperationApplicationService,
            FeatureAccessService featureAccessService
    ) {
        this.cacheOperationApplicationService = cacheOperationApplicationService;
        this.featureAccessService = featureAccessService;
    }

    @RequirePermission("CACHE_READ")
    @GetMapping("/operations")
    public List<CacheOperationResponse> listOperations(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        requireFeature();
        return cacheOperationApplicationService.listRecent(currentUser.userId()).stream()
                .map(CacheOperationResponse::from)
                .toList();
    }

    @RequirePermission("CACHE_MANAGE")
    @PostMapping("/operations")
    public CacheOperationResponse execute(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ExecuteCacheOperationRequest request
    ) {
        requireFeature();
        return CacheOperationResponse.from(cacheOperationApplicationService.execute(
                new ExecuteCacheOperationCommand(
                        currentUser.userId(),
                        request.cacheDomain(),
                        request.operationType(),
                        request.impactDescription()
                )));
    }

    @RequirePermission("CACHE_MANAGE")
    @PostMapping("/review-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public CacheOperationResponse submitReview(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateCacheReviewSubmissionRequest request
    ) {
        requireFeature();
        return CacheOperationResponse.from(cacheOperationApplicationService.createAndSubmitHighRisk(
                new CreateHighRiskCacheOperationCommand(
                        currentUser.userId(),
                        request.cacheDomain(),
                        request.operationType(),
                        request.title(),
                        request.description(),
                        request.confirmed()
                )));
    }

    @RequirePermission("CACHE_REVIEW")
    @GetMapping("/review-queue")
    public List<CacheReviewQueueResponse> listReviewQueue(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        requireFeature();
        return cacheOperationApplicationService.listPendingReviews(currentUser.userId()).stream()
                .map(CacheReviewQueueResponse::from)
                .toList();
    }

    @RequirePermission("CACHE_REVIEW")
    @PostMapping("/review-queue/{taskId}/approve")
    public CacheOperationResponse approve(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody CacheReviewRequest request
    ) {
        requireFeature();
        return CacheOperationResponse.from(cacheOperationApplicationService.approveAndExecute(
                taskId, currentUser.userId(), request.comment()));
    }

    @RequirePermission("CACHE_REVIEW")
    @PostMapping("/review-queue/{taskId}/reject")
    public CacheOperationResponse reject(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody CacheReviewRequest request
    ) {
        requireFeature();
        return CacheOperationResponse.from(cacheOperationApplicationService.reject(
                taskId, currentUser.userId(), request.comment()));
    }

    private void requireFeature() {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
    }
}
