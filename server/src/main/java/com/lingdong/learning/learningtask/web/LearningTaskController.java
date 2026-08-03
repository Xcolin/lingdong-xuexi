package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.LearningTaskBatchPublishService;
import com.lingdong.learning.learningtask.application.LearningTaskManagementService;
import com.lingdong.learning.learningtask.application.LearningTaskPublishService;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Web 端学习任务草稿、查询和发布入口。 */
@RestController
@RequestMapping("/api/v1/learning-tasks")
public class LearningTaskController {
    private final LearningTaskManagementService managementService;
    private final LearningTaskPublishService publishService;
    private final LearningTaskBatchPublishService batchPublishService;

    public LearningTaskController(
            LearningTaskManagementService managementService,
            LearningTaskPublishService publishService,
            LearningTaskBatchPublishService batchPublishService
    ) {
        this.managementService = managementService;
        this.publishService = publishService;
        this.batchPublishService = batchPublishService;
    }

    @RequirePermission("LEARNING_TASK_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LearningTaskResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody LearningTaskRequest request
    ) {
        return LearningTaskResponse.from(managementService.create(currentUser, request.toCommand()));
    }

    @RequirePermission("LEARNING_TASK_CREATE")
    @PatchMapping("/{id}")
    public LearningTaskResponse update(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody LearningTaskRequest request
    ) {
        return LearningTaskResponse.from(managementService.update(currentUser, id, request.toCommand()));
    }

    @RequirePermission("LEARNING_TASK_READ_MANAGED")
    @GetMapping("/{id}")
    public LearningTaskResponse findById(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return LearningTaskResponse.from(managementService.findById(currentUser, id));
    }

    @RequirePermission("LEARNING_TASK_READ_MANAGED")
    @GetMapping
    public LearningTaskPageResponse findPage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) LearningTaskSourceType sourceType,
            @RequestParam(required = false) LearningTaskStatus status,
            @RequestParam(required = false) LocalDate scheduledDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return LearningTaskPageResponse.from(managementService.findPage(
                currentUser, sourceType, status, scheduledDate, keyword, page, pageSize));
    }

    @RequirePermission("LEARNING_TASK_PUBLISH")
    @PostMapping("/batch-publish")
    public BatchPublishLearningTasksResponse batchPublish(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody BatchPublishLearningTasksRequest request
    ) {
        return BatchPublishLearningTasksResponse.from(
                batchPublishService.publish(currentUser, request.taskIds()));
    }

    @RequirePermission("LEARNING_TASK_PUBLISH")
    @PostMapping("/{id}/publish")
    public PublishLearningTaskResponse publish(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return PublishLearningTaskResponse.from(publishService.publish(currentUser, id));
    }
}
