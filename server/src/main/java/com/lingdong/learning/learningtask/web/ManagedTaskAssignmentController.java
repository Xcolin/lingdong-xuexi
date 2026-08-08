package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.ManagedTaskAssignmentService;
import com.lingdong.learning.learningtask.application.TaskDeferService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web 管理角色对范围内学生任务实例的操作入口。 */
@RestController
@RequestMapping("/api/v1/managed-task-assignments")
public class ManagedTaskAssignmentController {
    private final ManagedTaskAssignmentService assignmentService;
    private final TaskDeferService deferService;

    public ManagedTaskAssignmentController(
            ManagedTaskAssignmentService assignmentService,
            TaskDeferService deferService
    ) {
        this.assignmentService = assignmentService;
        this.deferService = deferService;
    }

    @RequirePermission("TASK_ASSIGNMENT_EXEMPT")
    @PostMapping("/{id}/exempt")
    public ManagedTaskAssignmentActionResponse exempt(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ExemptTaskAssignmentRequest request
    ) {
        return ManagedTaskAssignmentActionResponse.from(
                assignmentService.exempt(currentUser, id, request.toCommand()));
    }

    @RequirePermission("TASK_ASSIGNMENT_DEFER")
    @GetMapping
    public ManagedDeferCandidatePageResponse findManagedCandidates(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ManagedDeferCandidatePageResponse.from(
                deferService.findManagedCandidates(currentUser, page, pageSize));
    }

    @RequirePermission("TASK_ASSIGNMENT_DEFER")
    @PostMapping("/{id}/defer")
    public TaskDeferResponse defer(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody DeferTaskAssignmentRequest request
    ) {
        return TaskDeferResponse.from(
                deferService.deferManually(currentUser, id, request.targetDate()));
    }
}
