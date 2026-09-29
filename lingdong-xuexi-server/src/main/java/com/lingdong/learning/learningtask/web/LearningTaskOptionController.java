package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.LearningTaskOptionService;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学习任务 Web 表单的受控候选项入口。 */
@RestController
@RequestMapping("/api/v1/learning-task-options")
public class LearningTaskOptionController {
    private final LearningTaskOptionService optionService;

    public LearningTaskOptionController(LearningTaskOptionService optionService) {
        this.optionService = optionService;
    }

    @RequirePermission("LEARNING_TASK_CREATE")
    @GetMapping("/organizations")
    public List<OrganizationOptionResponse> organizations(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam LearningTaskSourceType sourceType,
            @RequestParam(required = false) String organizationType
    ) {
        return optionService.organizations(currentUser, sourceType, organizationType).stream()
                .map(OrganizationOptionResponse::from)
                .toList();
    }

    @RequirePermission("LEARNING_TASK_CREATE")
    @GetMapping("/students")
    public List<StudentOptionResponse> students(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam LearningTaskSourceType sourceType,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String keyword
    ) {
        return optionService.students(currentUser, sourceType, organizationId, keyword).stream()
                .map(StudentOptionResponse::from)
                .toList();
    }

    @RequirePermission("TEACHER_CLASS_ASSIGN")
    @GetMapping("/teachers")
    public List<TeacherOptionResponse> teachers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword
    ) {
        return optionService.teachers(currentUser, classId, keyword).stream()
                .map(TeacherOptionResponse::from)
                .toList();
    }
}
