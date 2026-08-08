package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.LearningTaskTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Web 家长查询系统模板并管理本人个人模板的入口。 */
@RestController
@RequestMapping("/api/v1/task-templates")
public class LearningTaskTemplateController {
    private final LearningTaskTemplateService templateService;

    public LearningTaskTemplateController(LearningTaskTemplateService templateService) {
        this.templateService = templateService;
    }

    @RequirePermission("LEARNING_TASK_TEMPLATE_READ")
    @GetMapping
    public List<LearningTaskTemplateResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return templateService.list(currentUser).stream()
                .map(LearningTaskTemplateResponse::from)
                .toList();
    }

    @RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LearningTaskTemplateResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody LearningTaskTemplateRequest request
    ) {
        return LearningTaskTemplateResponse.from(
                templateService.create(currentUser, request.toInput()));
    }

    @RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")
    @PatchMapping("/{templateId}")
    public LearningTaskTemplateResponse update(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long templateId,
            @RequestBody UpdateLearningTaskTemplateRequest request
    ) {
        return LearningTaskTemplateResponse.from(templateService.update(
                currentUser, templateId, request.versionNo(), request.toInput()));
    }

    @RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")
    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long templateId,
            @RequestParam Long versionNo
    ) {
        templateService.delete(currentUser, templateId, versionNo);
    }

    @RequirePermission("LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL")
    @PutMapping("/personal-order")
    public List<LearningTaskTemplateResponse> reorder(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody PersonalTemplateOrderRequest request
    ) {
        return templateService.reorder(currentUser, request.toItems()).stream()
                .map(LearningTaskTemplateResponse::from)
                .toList();
    }
}
