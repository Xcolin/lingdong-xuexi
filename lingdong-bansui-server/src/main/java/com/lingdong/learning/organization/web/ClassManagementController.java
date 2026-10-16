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
import org.springframework.web.bind.annotation.RequestParam;
import com.lingdong.learning.organization.domain.Organization;

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

    @GetMapping("/lookup")
    public List<ClassOrganizationResponse> lookup(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String permission,@RequestParam(defaultValue="CLASS")String kind,
            @RequestParam(required=false)Long schoolOrganizationId,@RequestParam(required=false)Long gradeOrganizationId,@RequestParam(required=false)Long campusOrganizationId){
        return classManagementApplicationService.lookup(user,permission,kind,schoolOrganizationId,gradeOrganizationId,campusOrganizationId).stream().map(this::response).toList();
    }

    @RequirePermission("CLASS_READ")
    @GetMapping("/schools")
    public List<ClassOrganizationResponse> listSchools(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return classManagementApplicationService.listManageableSchools(currentUser).stream()
                .map(this::response)
                .toList();
    }

    @RequirePermission("CLASS_READ")
    @GetMapping
    public List<ClassOrganizationResponse> listClasses(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) Long schoolOrganizationId,
            @RequestParam(required = false) Long gradeOrganizationId,
            @RequestParam(required = false) Long campusOrganizationId
    ) {
        return classManagementApplicationService.listClasses(currentUser,schoolOrganizationId,gradeOrganizationId,campusOrganizationId).stream()
                .map(this::response)
                .toList();
    }

    @RequirePermission("CLASS_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassOrganizationResponse createClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateClassRequest request
    ) {
        return response(classManagementApplicationService.createClass(
                currentUser, new CreateClassCommand(
                        request.schoolOrganizationId(), request.gradeOrganizationId(), request.name(), request.sortOrder())));
    }

    @RequirePermission("CLASS_UPDATE")
    @PutMapping("/{classId}")
    public ClassOrganizationResponse updateClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody UpdateClassRequest request
    ) {
        return response(classManagementApplicationService.updateClass(
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
        return response(classManagementApplicationService.disableClass(
                currentUser, classId, request.versionNo()));
    }

    @RequirePermission("CLASS_STATUS_CHANGE")
    @PostMapping("/{classId}/enable")
    public ClassOrganizationResponse enableClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody ClassStatusRequest request
    ) {
        return response(classManagementApplicationService.enableClass(
                currentUser, classId, request.versionNo()));
    }

    private ClassOrganizationResponse response(Organization organization) {
        return ClassOrganizationResponse.from(organization,
                classManagementApplicationService.ancestor(organization,"SCHOOL"),
                classManagementApplicationService.ancestor(organization,"GRADE"),
                classManagementApplicationService.ancestor(organization,"CAMPUS"),classManagementApplicationService.namePath(organization));
    }

    @RequirePermission("CLASS_READ") @GetMapping("/grades")
    public List<ClassOrganizationResponse> listGrades(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false) Long schoolOrganizationId,@RequestParam(required=false) Long campusOrganizationId) {
        return classManagementApplicationService.listGrades(user,schoolOrganizationId,campusOrganizationId).stream().map(this::response).toList();
    }
    @RequirePermission("CLASS_CREATE") @PostMapping("/grades") @ResponseStatus(HttpStatus.CREATED)
    public ClassOrganizationResponse createGrade(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody CreateGradeRequest request) {
        return response(classManagementApplicationService.createGrade(user,request.schoolOrganizationId(),request.campusOrganizationId(),request.name(),request.sortOrder()));
    }
    @RequirePermission("CLASS_UPDATE") @PutMapping("/grades/{gradeId}")
    public ClassOrganizationResponse updateGrade(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long gradeId,@Valid @RequestBody UpdateClassRequest request) {
        return response(classManagementApplicationService.updateGrade(user,new UpdateClassCommand(gradeId,request.name(),request.sortOrder(),request.versionNo())));
    }
    @RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/grades/{gradeId}/disable")
    public ClassOrganizationResponse disableGrade(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long gradeId,@Valid @RequestBody ClassStatusRequest request) {
        return response(classManagementApplicationService.disableGrade(user,gradeId,request.versionNo()));
    }
    @RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/grades/{gradeId}/enable")
    public ClassOrganizationResponse enableGrade(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long gradeId,@Valid @RequestBody ClassStatusRequest request) {
        return response(classManagementApplicationService.enableGrade(user,gradeId,request.versionNo()));
    }
    @RequirePermission("CLASS_UPDATE") @PostMapping("/{classId}/assign-grade")
    public ClassOrganizationResponse assignGrade(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long classId,@Valid @RequestBody AssignClassGradeRequest request) {
        return response(classManagementApplicationService.assignGrade(user,classId,request.gradeOrganizationId(),request.versionNo()));
    }

 @RequirePermission("CLASS_READ") @GetMapping("/campuses")
 public List<ClassOrganizationResponse> listCampuses(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(required=false) Long schoolOrganizationId) { return classManagementApplicationService.listCampuses(user,schoolOrganizationId).stream().map(this::response).toList(); }
 @RequirePermission("CLASS_CREATE") @PostMapping("/campuses") @ResponseStatus(HttpStatus.CREATED)
 public ClassOrganizationResponse createCampus(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody CreateCampusRequest request) { return response(classManagementApplicationService.createCampus(user,request.schoolOrganizationId(),request.name(),request.sortOrder())); }
 @RequirePermission("CLASS_UPDATE") @PutMapping("/campuses/{campusId}")
 public ClassOrganizationResponse updateCampus(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long campusId,@Valid @RequestBody UpdateClassRequest request) { return response(classManagementApplicationService.updateCampus(user,new UpdateClassCommand(campusId,request.name(),request.sortOrder(),request.versionNo()))); }
 @RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/campuses/{campusId}/disable")
 public ClassOrganizationResponse disableCampus(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long campusId,@Valid @RequestBody ClassStatusRequest request) { return response(classManagementApplicationService.disableCampus(user,campusId,request.versionNo())); }
 @RequirePermission("CLASS_STATUS_CHANGE") @PostMapping("/campuses/{campusId}/enable")
 public ClassOrganizationResponse enableCampus(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long campusId,@Valid @RequestBody ClassStatusRequest request) { return response(classManagementApplicationService.enableCampus(user,campusId,request.versionNo())); }
}
