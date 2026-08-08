package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.TaskReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Web 端业务审核待办入口。 */
@RestController
@RequestMapping("/api/v1/task-reviews")
public class TaskReviewController {
    private final TaskReviewService reviewService;

    public TaskReviewController(TaskReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @RequirePermission("TASK_ASSIGNMENT_REVIEW")
    @GetMapping
    public TaskReviewPageResponse findPage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return TaskReviewPageResponse.from(reviewService.findPage(currentUser, page, pageSize));
    }

    @RequirePermission("TASK_ASSIGNMENT_REVIEW")
    @GetMapping("/{assignmentId}")
    public TaskReviewResponse findById(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long assignmentId
    ) {
        return TaskReviewResponse.from(reviewService.findById(currentUser, assignmentId));
    }

    @RequirePermission("TASK_ASSIGNMENT_REVIEW")
    @PostMapping("/{assignmentId}/reject")
    public TaskReviewActionResponse reject(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long assignmentId,
            @Valid @RequestBody RejectTaskCheckInRequest request
    ) {
        return TaskReviewActionResponse.from(
                reviewService.reject(currentUser, assignmentId, request.toCommand()));
    }

    @RequirePermission("TASK_ASSIGNMENT_REVIEW")
    @PostMapping("/{assignmentId}/approve")
    public ApproveTaskReviewResponse approve(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long assignmentId
    ) {
        return ApproveTaskReviewResponse.from(reviewService.approve(currentUser, assignmentId));
    }

    @RequirePermission("TASK_ASSIGNMENT_REVIEW")
    @GetMapping("/{assignmentId}/reviewer-options")
    public List<ReviewerOptionResponse> findReviewerOptions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long assignmentId
    ) {
        return reviewService.findReviewerOptions(currentUser, assignmentId).stream()
                .map(ReviewerOptionResponse::from)
                .toList();
    }

    @RequirePermission("TASK_ASSIGNMENT_REVIEW")
    @PostMapping("/{assignmentId}/transfer")
    public ReviewerTransferResponse transfer(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long assignmentId,
            @Valid @RequestBody TransferTaskReviewRequest request
    ) {
        return ReviewerTransferResponse.from(
                reviewService.transfer(currentUser, assignmentId, request.toCommand()));
    }
}
