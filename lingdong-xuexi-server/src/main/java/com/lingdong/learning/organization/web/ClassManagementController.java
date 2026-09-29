package com.lingdong.learning.organization.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.organization.application.ClassManagementApplicationService;
import com.lingdong.learning.organization.application.CreateClassCommand;
import com.lingdong.learning.organization.application.UpdateClassCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 机构管理员在 Web 与小程序中共用的班级基础管理接口。 */
@RestController
@RequestMapping("/api/v1/classes")
public class ClassManagementController {
    private final ClassManagementApplicationService classManagementApplicationService;

    public ClassManagementController(
            ClassManagementApplicationService classManagementApplicationService
    ) {
        this.classManagementApplicationService = classManagementApplicationService;
    }

    @RequirePermission("CLASS_READ")
    @GetMapping("/schools")
    public List<ClassOrganizationResponse> listSchools(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return classManagementApplicationService.listManageableSchools(currentUser).stream()
                .map(ClassOrganizationResponse::from)
                .toList();
    }

    @RequirePermission("CLASS_READ")
    @GetMapping
    public List<ClassOrganizationResponse> listClasses(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return classManagementApplicationService.listClasses(currentUser).stream()
                .map(ClassOrganizationResponse::from)
                .toList();
    }

    @RequirePermission("CLASS_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassOrganizationResponse createClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateClassRequest request
    ) {
        return ClassOrganizationResponse.from(classManagementApplicationService.createClass(
                currentUser, new CreateClassCommand(
                        request.schoolOrganizationId(), request.name(), request.sortOrder())));
    }

    @RequirePermission("CLASS_UPDATE")
    @PutMapping("/{classId}")
    public ClassOrganizationResponse updateClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody UpdateClassRequest request
    ) {
        return ClassOrganizationResponse.from(classManagementApplicationService.updateClass(
                currentUser, new UpdateClassCommand(
                        classId, request.name(), request.sortOrder(), request.versionNo())));
    }

    @RequirePermission("CLASS_STATUS_CHANGE")
    @PostMapping("/{classId}/disable")
    public ClassOrganizationResponse disableClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassStatusRequest request
    ) {
        return ClassOrganizationResponse.from(classManagementApplicationService.disableClass(
                currentUser, classId, request.versionNo()));
    }

    @RequirePermission("CLASS_STATUS_CHANGE")
    @PostMapping("/{classId}/enable")
    public ClassOrganizationResponse enableClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassStatusRequest request
    ) {
        return ClassOrganizationResponse.from(classManagementApplicationService.enableClass(
                currentUser, classId, request.versionNo()));
    }
}
