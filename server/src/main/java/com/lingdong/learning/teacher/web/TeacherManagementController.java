package com.lingdong.learning.teacher.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.teacher.application.CreateTeacherCommand;
import com.lingdong.learning.teacher.application.TeacherBatchApplicationService;
import com.lingdong.learning.teacher.application.TeacherBatchCommand;
import com.lingdong.learning.teacher.application.TeacherManagementApplicationService;
import com.lingdong.learning.teacher.application.TeacherQuery;
import com.lingdong.learning.teacher.application.UpdateTeacherProfileCommand;
import com.lingdong.learning.user.domain.UserStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 机构管理员在组织数据范围内维护教师账号。 */
@RestController
@RequestMapping("/api/v1/organization-teachers")
public class TeacherManagementController {
    private final TeacherManagementApplicationService managementService;
    private final TeacherBatchApplicationService batchService;

    public TeacherManagementController(
            TeacherManagementApplicationService managementService,
            TeacherBatchApplicationService batchService
    ) {
        this.managementService = managementService;
        this.batchService = batchService;
    }

    @RequirePermission("TEACHER_READ")
    @GetMapping
    public TeacherPageResponse list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classOrganizationId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return TeacherPageResponse.from(managementService.list(currentUser,
                new TeacherQuery(keyword, schoolId, classOrganizationId, status, page, pageSize)));
    }

    @RequirePermission("TEACHER_READ")
    @GetMapping("/{teacherUserId}")
    public TeacherResponse get(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long teacherUserId
    ) {
        return TeacherResponse.from(managementService.get(currentUser, teacherUserId));
    }

    @RequirePermission("TEACHER_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateTeacherRequest request
    ) {
        return TeacherResponse.from(managementService.create(currentUser, new CreateTeacherCommand(
                request.username(), request.displayName(), request.mobile(), request.initialPassword(),
                request.schoolId(), request.classOrganizationIds())));
    }

    @RequirePermission("TEACHER_UPDATE")
    @PutMapping("/{teacherUserId}/profile")
    public TeacherResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long teacherUserId,
            @Valid @RequestBody UpdateTeacherProfileRequest request
    ) {
        return TeacherResponse.from(managementService.updateProfile(
                currentUser, teacherUserId,
                new UpdateTeacherProfileCommand(
                        request.displayName(), request.mobile(), request.clearMobile())));
    }

    @RequirePermission("TEACHER_STATUS_CHANGE")
    @PutMapping("/{teacherUserId}/status")
    public TeacherResponse changeStatus(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long teacherUserId,
            @Valid @RequestBody ChangeTeacherStatusRequest request
    ) {
        return TeacherResponse.from(managementService.changeStatus(
                currentUser, teacherUserId, request.status()));
    }

    @RequirePermission("TEACHER_PASSWORD_RESET")
    @PostMapping("/{teacherUserId}/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long teacherUserId,
            @Valid @RequestBody ResetTeacherPasswordRequest request
    ) {
        managementService.resetPassword(currentUser, teacherUserId, request.newPassword());
    }

    @RequirePermission("TEACHER_BATCH_MANAGE")
    @PostMapping("/batch")
    public TeacherBatchResponse batch(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody TeacherBatchRequest request
    ) {
        return TeacherBatchResponse.from(batchService.execute(currentUser, new TeacherBatchCommand(
                request.operation(), request.teacherUserIds(), request.classOrganizationId())));
    }
}
