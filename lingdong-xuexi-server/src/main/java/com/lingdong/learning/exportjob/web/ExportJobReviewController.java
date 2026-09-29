package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.exportjob.application.ExportJobQueryService;
import com.lingdong.learning.exportjob.application.ExportJobReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统审核员专用的敏感导出审核 API。 */
@RestController
@RequestMapping("/api/v1/export-job-reviews")
public class ExportJobReviewController {
    private final ExportJobQueryService queryService;
    private final ExportJobReviewService reviewService;

    public ExportJobReviewController(
            ExportJobQueryService queryService,
            ExportJobReviewService reviewService
    ) {
        this.queryService = queryService;
        this.reviewService = reviewService;
    }

    @RequirePermission("EXPORT_SENSITIVE_REVIEW")
    @GetMapping
    public ExportJobReviewPageResponse list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ExportJobReviewPageResponse.from(
                queryService.findPendingReviews(currentUser.userId(), page, pageSize));
    }

    @RequirePermission("EXPORT_SENSITIVE_REVIEW")
    @PostMapping("/{taskId}/approve")
    public ExportJobResponse approve(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody ExportJobReviewRequest request
    ) {
        return ExportJobResponse.from(
                reviewService.approve(taskId, currentUser.userId(), request.comment()));
    }

    @RequirePermission("EXPORT_SENSITIVE_REVIEW")
    @PostMapping("/{taskId}/reject")
    public ExportJobResponse reject(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody ExportJobReviewRequest request
    ) {
        return ExportJobResponse.from(
                reviewService.reject(taskId, currentUser.userId(), request.comment()));
    }
}
