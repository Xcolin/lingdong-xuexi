package com.lingdong.learning.teacher.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.teacher.application.TeacherSubjectDictionary;
import com.lingdong.learning.teacher.application.TeacherSubjectService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@RestController
public class TeacherSubjectOptionsController {
    private final TeacherSubjectService service;
    public TeacherSubjectOptionsController(TeacherSubjectService service) { this.service = service; }

    @GetMapping("/api/v1/organization-teachers/subject-options")
    @RequirePermission("TEACHER_READ")
    public List<TeacherSubjectDictionary.Option> options(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.options(actor);
    }

    public record SubjectKey(@NotNull @Positive Long teacherUserId,
                             @NotNull @Positive Long institutionOrganizationId) {}
    public record SummariesRequest(@NotNull @Size(max=100) List<@NotNull @Valid SubjectKey> items) {}

    @PostMapping("/api/v1/organization-teachers/subject-summaries")
    @RequirePermission("TEACHER_READ")
    public List<TeacherSubjectService.Summary> summaries(@AuthenticationPrincipal AuthenticatedUser actor,
                                                       @Valid @RequestBody SummariesRequest request) {
        return service.summaries(actor, request.items().stream().map(key ->
                new com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore.Key(
                        key.teacherUserId(), key.institutionOrganizationId())).toList());
    }
}
