package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.StudentTaskAssignmentService;
import com.lingdong.learning.learningtask.application.StudentTaskExecutionService;
import jakarta.validation.Valid;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 小程序端学生本人任务只读入口。 */
@RestController
@RequestMapping("/api/v1/task-assignments")
public class StudentTaskAssignmentController {
    private final StudentTaskAssignmentService assignmentService;
    private final StudentTaskExecutionService executionService;

    public StudentTaskAssignmentController(
            StudentTaskAssignmentService assignmentService,
            StudentTaskExecutionService executionService
    ) {
        this.assignmentService = assignmentService;
        this.executionService = executionService;
    }

    @RequirePermission("TASK_ASSIGNMENT_READ_SELF")
    @GetMapping
    public StudentTaskAssignmentPageResponse findPage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) LearningTaskSourceType sourceType,
            @RequestParam(required = false) LocalDate scheduledDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return StudentTaskAssignmentPageResponse.from(
                assignmentService.findPage(currentUser, sourceType, scheduledDate, page, pageSize));
    }

    @RequirePermission("TASK_ASSIGNMENT_READ_SELF")
    @GetMapping("/{id}")
    public StudentTaskAssignmentResponse findById(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return StudentTaskAssignmentResponse.from(assignmentService.findById(currentUser, id));
    }

    @RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")
    @PostMapping("/{id}/claim")
    public StudentTaskAssignmentResponse claim(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return StudentTaskAssignmentResponse.from(executionService.claim(currentUser, id));
    }

    @RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")
    @PostMapping("/{id}/pause")
    public StudentTaskAssignmentResponse pause(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody PauseTaskRequest request
    ) {
        return StudentTaskAssignmentResponse.from(
                executionService.pause(currentUser, id, request.toCommand()));
    }

    @RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")
    @PostMapping("/{id}/resume")
    public StudentTaskAssignmentResponse resume(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return StudentTaskAssignmentResponse.from(executionService.resume(currentUser, id));
    }

    @RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")
    @PostMapping("/{id}/abandon")
    public StudentTaskAssignmentResponse abandon(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AbandonTaskRequest request
    ) {
        AbandonTaskRequest normalizedRequest = request == null ? new AbandonTaskRequest(null) : request;
        return StudentTaskAssignmentResponse.from(
                executionService.abandon(currentUser, id, normalizedRequest.toCommand()));
    }

    @RequirePermission("TASK_ASSIGNMENT_EXECUTE_SELF")
    @PostMapping("/{id}/check-ins")
    public StudentTaskAssignmentResponse submitCheckIn(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SubmitTaskCheckInRequest request
    ) {
        return StudentTaskAssignmentResponse.from(
                executionService.submitCheckIn(currentUser, id, request.toCommand()));
    }
}
