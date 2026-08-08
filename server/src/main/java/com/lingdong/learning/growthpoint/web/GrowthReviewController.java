package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.growthpoint.application.GrowthReviewQueryService;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
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

/** Web 主家长和小程序学生彼此独立的成长复盘接口。 */
@RestController
@RequestMapping("/api/v1/growth-reviews")
public class GrowthReviewController {
    private final GrowthReviewQueryService queryService;

    public GrowthReviewController(GrowthReviewQueryService queryService) {
        this.queryService = queryService;
    }

    @RequirePermission("GROWTH_REVIEW_READ_SELF")
    @GetMapping("/me")
    public GrowthReviewPageResponse findMyReviews(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "DAY") GrowthReviewPeriodType periodType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthReviewPageResponse.from(
                queryService.findMyReviews(currentUser, periodType, page, pageSize));
    }

    @RequirePermission("GROWTH_REVIEW_READ_SELF")
    @GetMapping("/me/{reviewId}")
    public GrowthReviewDetailResponse findMyReview(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long reviewId
    ) {
        return GrowthReviewDetailResponse.from(
                queryService.findMyReview(currentUser, reviewId));
    }

    @RequirePermission("GROWTH_REVIEW_SUPPLEMENT_SELF")
    @PostMapping("/me/{reviewId}/supplements")
    @ResponseStatus(HttpStatus.CREATED)
    public GrowthReviewSupplementResponse addMySupplement(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long reviewId,
            @Valid @RequestBody AddGrowthReviewSupplementRequest request
    ) {
        return GrowthReviewSupplementResponse.from(
                queryService.addMySupplement(currentUser, reviewId, request.toCommand()));
    }

    @RequirePermission("GROWTH_REVIEW_READ_CHILD")
    @GetMapping("/students/{studentId}")
    public GrowthReviewPageResponse findChildReviews(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "DAY") GrowthReviewPeriodType periodType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return GrowthReviewPageResponse.from(
                queryService.findChildReviews(currentUser, studentId, periodType, page, pageSize));
    }

    @RequirePermission("GROWTH_REVIEW_READ_CHILD")
    @GetMapping("/students/{studentId}/{reviewId}")
    public GrowthReviewDetailResponse findChildReview(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @PathVariable Long reviewId
    ) {
        return GrowthReviewDetailResponse.from(
                queryService.findChildReview(currentUser, studentId, reviewId));
    }

    @RequirePermission("GROWTH_REVIEW_SUPPLEMENT_CHILD")
    @PostMapping("/students/{studentId}/{reviewId}/supplements")
    @ResponseStatus(HttpStatus.CREATED)
    public GrowthReviewSupplementResponse addChildSupplement(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @PathVariable Long reviewId,
            @Valid @RequestBody AddGrowthReviewSupplementRequest request
    ) {
        return GrowthReviewSupplementResponse.from(queryService.addChildSupplement(
                currentUser, studentId, reviewId, request.toCommand()));
    }
}
