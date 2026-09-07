package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.StudentQrTicketApplicationService;
import com.lingdong.learning.auth.web.StudentQrTicketResponse;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.CreateStudentCommand;
import com.lingdong.learning.student.application.CreateParentBindingInvitationCommand;
import com.lingdong.learning.student.application.ParentBindingInvitationApplicationService;
import com.lingdong.learning.student.application.StudentApplicationService;
import com.lingdong.learning.student.application.StudentCredentialManagementService;
import com.lingdong.learning.student.application.DeactivateStudentOrganizationCommand;
import com.lingdong.learning.student.application.StudentOrganizationLifecycleService;
import com.lingdong.learning.student.application.TransferStudentClassCommand;
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

import java.util.List;

/** 学生档案的家长、机构管理员和系统管理员共同入口。 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentManagementController {
    private static final String LEGACY_CLASS_ASSIGNMENT_REASON = "兼容接口配置班级";

    private final StudentApplicationService studentApplicationService;
    private final StudentCredentialManagementService studentCredentialManagementService;
    private final ParentBindingInvitationApplicationService parentBindingInvitationApplicationService;
    private final StudentQrTicketApplicationService studentQrTicketApplicationService;
    private final StudentOrganizationLifecycleService studentOrganizationLifecycleService;

    public StudentManagementController(
            StudentApplicationService studentApplicationService,
            StudentCredentialManagementService studentCredentialManagementService,
            ParentBindingInvitationApplicationService parentBindingInvitationApplicationService,
            StudentQrTicketApplicationService studentQrTicketApplicationService,
            StudentOrganizationLifecycleService studentOrganizationLifecycleService
    ) {
        this.studentApplicationService = studentApplicationService;
        this.studentCredentialManagementService = studentCredentialManagementService;
        this.parentBindingInvitationApplicationService = parentBindingInvitationApplicationService;
        this.studentQrTicketApplicationService = studentQrTicketApplicationService;
        this.studentOrganizationLifecycleService = studentOrganizationLifecycleService;
    }

    @RequirePermission("STUDENT_READ")
    @GetMapping
    public StudentDirectoryPageResponse listStudents(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return StudentDirectoryPageResponse.from(
                studentApplicationService.listStudents(currentUser, keyword, page, pageSize)
        );
    }

    @RequirePermission("STUDENT_READ")
    @GetMapping("/{id}")
    public StudentResponse findStudent(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return StudentResponse.from(studentApplicationService.findStudent(currentUser, id));
    }

    @RequirePermission("STUDENT_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedStudentResponse createStudent(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateStudentRequest request
    ) {
        return CreatedStudentResponse.from(studentApplicationService.createStudent(currentUser,
                new CreateStudentCommand(request.studentName(), request.gradeCode(), request.organizationId())));
    }

    @RequirePermission("STUDENT_CLASS_ASSIGN")
    @PutMapping("/{studentId}/class")
    public StudentClassAssignmentResponse assignClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @Valid @RequestBody AssignStudentClassRequest request
    ) {
        var relationship = studentOrganizationLifecycleService.transferClass(
                currentUser, studentId,
                new TransferStudentClassCommand(
                        request.classOrganizationId(), LEGACY_CLASS_ASSIGNMENT_REASON));
        return new StudentClassAssignmentResponse(
                relationship.studentId(), relationship.currentClassOrganizationId(), relationship.status());
    }

    @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
    @GetMapping("/organization-relationships")
    public List<StudentOrganizationRelationshipSummaryResponse> listOrganizationRelationships(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return studentOrganizationLifecycleService.list(currentUser).stream()
                .map(StudentOrganizationRelationshipSummaryResponse::from)
                .toList();
    }

    @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
    @GetMapping("/organization-relationship-classes")
    public List<StudentOrganizationClassOptionResponse> listOrganizationRelationshipClasses(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return studentOrganizationLifecycleService.listClassOptions(currentUser).stream()
                .map(StudentOrganizationClassOptionResponse::from)
                .toList();
    }

    @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
    @GetMapping("/{studentId}/organization-relationships")
    public StudentOrganizationRelationshipResponse findOrganizationRelationship(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return StudentOrganizationRelationshipResponse.from(
                studentOrganizationLifecycleService.find(currentUser, studentId));
    }

    @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
    @PostMapping("/{studentId}/class-transfers")
    public StudentOrganizationRelationshipResponse transferClass(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @Valid @RequestBody TransferStudentClassRequest request
    ) {
        return StudentOrganizationRelationshipResponse.from(
                studentOrganizationLifecycleService.transferClass(currentUser, studentId,
                        new TransferStudentClassCommand(
                                request.classOrganizationId(), request.reason())));
    }

    @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
    @PostMapping("/{studentId}/organization-deactivations")
    public StudentOrganizationRelationshipResponse deactivateOrganizationRelationship(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @Valid @RequestBody DeactivateStudentOrganizationRequest request
    ) {
        return StudentOrganizationRelationshipResponse.from(
                studentOrganizationLifecycleService.deactivate(currentUser, studentId,
                        new DeactivateStudentOrganizationCommand(
                                request.organizationId(), request.reason())));
    }

    @RequirePermission("STUDENT_CREDENTIAL_INITIALIZE")
    @PostMapping("/{studentId}/credentials/initialize")
    public StudentCredentialIssueResponse initializeCredential(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return StudentCredentialIssueResponse.from(
                studentCredentialManagementService.initialize(currentUser, studentId));
    }

    @RequirePermission("STUDENT_LOGIN_CODE_RESET")
    @PostMapping("/{studentId}/login-code-resets")
    public StudentCredentialIssueResponse resetLoginCode(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return StudentCredentialIssueResponse.from(
                studentCredentialManagementService.resetLoginCode(currentUser, studentId));
    }

    @RequirePermission("STUDENT_LOGIN_QR_CREATE")
    @PostMapping("/{studentId}/login-qr-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentQrTicketResponse issueLoginQrTicket(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId
    ) {
        return StudentQrTicketResponse.from(studentQrTicketApplicationService.issue(currentUser, studentId));
    }

    @RequirePermission("STUDENT_PARENT_INVITE_CREATE")
    @PostMapping("/{studentId}/parent-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ParentBindingInvitationResponse createParentBindingInvitation(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @Valid @RequestBody CreateParentBindingInvitationRequest request
    ) {
        return ParentBindingInvitationResponse.from(parentBindingInvitationApplicationService.create(
                currentUser, studentId, new CreateParentBindingInvitationCommand(request.organizationId())
        ));
    }
}
