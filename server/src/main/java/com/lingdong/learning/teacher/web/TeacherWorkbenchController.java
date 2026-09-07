package com.lingdong.learning.teacher.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.teacher.application.TeacherWorkbenchQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供当前教师小程序工作台的可信身份与班级上下文。 */
@RestController
@RequestMapping("/api/v1/teacher-workbench")
public class TeacherWorkbenchController {
    private final TeacherWorkbenchQueryService queryService;

    public TeacherWorkbenchController(TeacherWorkbenchQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/context")
    public TeacherWorkbenchContextResponse context(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return TeacherWorkbenchContextResponse.from(queryService.getContext(currentUser));
    }
}
