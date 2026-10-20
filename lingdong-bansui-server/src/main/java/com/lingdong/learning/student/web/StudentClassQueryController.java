package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.web.StudentOptionResponse;
import com.lingdong.learning.student.application.StudentClassQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/students")
public class StudentClassQueryController {
    private final StudentClassQueryService service;
    public StudentClassQueryController(StudentClassQueryService service) { this.service = service; }

    @GetMapping("/class-assignment-options") @RequirePermission("STUDENT_CLASS_ASSIGN")
    public List<StudentOptionResponse> candidates(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long classOrganizationId) { return query(user, classOrganizationId, false); }

    @GetMapping("/class-members") @RequirePermission("STUDENT_READ")
    public List<StudentOptionResponse> members(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long classOrganizationId) { return query(user, classOrganizationId, true); }

    private List<StudentOptionResponse> query(AuthenticatedUser user, Long classId, boolean membersOnly) {
        return service.options(user, classId, membersOnly).stream().map(option -> new StudentOptionResponse(
                option.id(), option.studentName(), option.studentAccountMasked(), option.currentClassId(),
                option.currentClassName(), option.relationshipRole(), option.schoolName())).toList();
    }
}
