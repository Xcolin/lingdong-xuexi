package com.lingdong.learning.teacher.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.teacher.application.TeacherSubjectService;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Separate optional metadata endpoint keeps existing profile updates unchanged. */
@RestController
@RequestMapping("/api/v1/organization-teachers/{teacherUserId}/institutions/{institutionOrganizationId}/subject")
public class TeacherSubjectController {
    private final TeacherSubjectService service;

    public TeacherSubjectController(TeacherSubjectService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("TEACHER_READ")
    public SubjectResponse read(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable long teacherUserId,
                                @PathVariable long institutionOrganizationId) {
        return SubjectResponse.from(service.read(actor, teacherUserId, institutionOrganizationId));
    }

    @PutMapping
    @RequirePermission("TEACHER_UPDATE")
    public SubjectResponse update(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable long teacherUserId,
                                  @PathVariable long institutionOrganizationId,
                                  @Valid @RequestBody UpdateSubjectRequest request) {
        return SubjectResponse.from(service.update(actor, teacherUserId,
                institutionOrganizationId, request.subjectCode(), request.versionNo()));
    }

    public record UpdateSubjectRequest(@Size(max = 64) String subjectCode,
                                       @NotNull @Min(0) Integer versionNo) {}

    public record SubjectResponse(
            @JsonSerialize(using = ToStringSerializer.class) long teacherUserId,
            @JsonSerialize(using = ToStringSerializer.class) long institutionOrganizationId,
            String subjectCode, int versionNo) {
        static SubjectResponse from(TeacherSubjectStore.Metadata value) {
            return new SubjectResponse(value.teacherUserId(), value.institutionOrganizationId(),
                    value.subjectCode(), value.versionNo());
        }
    }
}
