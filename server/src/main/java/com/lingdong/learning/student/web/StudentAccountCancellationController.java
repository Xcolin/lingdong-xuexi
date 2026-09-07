package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.StudentAccountCancellationCommand;
import com.lingdong.learning.student.application.StudentAccountCancellationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 机构管理员查询候选并执行学生账号注销的独立接口。 */
@RestController
@RequestMapping("/api/v1")
public class StudentAccountCancellationController {
    private static final String PERMISSION = "STUDENT_ACCOUNT_CANCELLATION_MANAGE";

    private final StudentAccountCancellationService cancellationService;

    public StudentAccountCancellationController(
            StudentAccountCancellationService cancellationService
    ) {
        this.cancellationService = cancellationService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/student-account-cancellations/candidates")
    public List<StudentAccountCancellationCandidateResponse> candidates(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return cancellationService.listCandidates(currentUser).stream()
                .map(StudentAccountCancellationCandidateResponse::from)
                .toList();
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/students/{studentId}/cancellations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @Valid @RequestBody StudentAccountCancellationRequest request
    ) {
        cancellationService.cancel(currentUser, new StudentAccountCancellationCommand(
                studentId, request.reason(), request.confirmation()));
    }
}
