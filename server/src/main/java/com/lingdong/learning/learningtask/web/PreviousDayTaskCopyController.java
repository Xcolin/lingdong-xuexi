package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.PreviousDayTaskCopyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web 主家长复制孩子昨日家庭任务的入口。 */
@RestController
@RequestMapping("/api/v1")
public class PreviousDayTaskCopyController {
    private final PreviousDayTaskCopyService copyService;

    public PreviousDayTaskCopyController(PreviousDayTaskCopyService copyService) {
        this.copyService = copyService;
    }

    @RequirePermission("LEARNING_TASK_COPY_PREVIOUS_DAY")
    @GetMapping("/students/{studentId}/previous-day-task-copy/preview")
    public PreviousDayTaskCopyPreviewResponse preview(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return PreviousDayTaskCopyPreviewResponse.from(
                copyService.preview(currentUser, studentId));
    }

    @RequirePermission("LEARNING_TASK_COPY_PREVIOUS_DAY")
    @PostMapping("/students/{studentId}/previous-day-task-copy")
    public TaskCopyBatchResponse copy(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestBody CopyPreviousDayTasksRequest request
    ) {
        return TaskCopyBatchResponse.from(copyService.copy(
                currentUser, studentId, request.confirmDuplicateTitles()));
    }

    @RequirePermission("LEARNING_TASK_COPY_PREVIOUS_DAY")
    @PostMapping("/task-copy-batches/{batchId}/items/{itemId}/retry")
    public TaskCopyBatchResponse retry(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long batchId,
            @PathVariable Long itemId
    ) {
        return TaskCopyBatchResponse.from(copyService.retry(currentUser, batchId, itemId));
    }
}
