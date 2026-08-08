package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.StudentQrTicketApplicationService;
import com.lingdong.learning.auth.web.StudentQrTicketResponse;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.CreateStudentCommand;
import com.lingdong.learning.student.application.CreateParentBindingInvitationCommand;
import com.lingdong.learning.student.application.AssignStudentClassCommand;
import com.lingdong.learning.student.application.ParentBindingInvitationApplicationService;
import com.lingdong.learning.student.application.StudentApplicationService;
import com.lingdong.learning.student.application.StudentCredentialManagementService;
import com.lingdong.learning.student.application.StudentClassAssignmentService;
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

/** 学生档案的家长、机构管理员和系统管理员共同入口。 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentManagementController {
    private final StudentApplicationService studentApplicationService;
    private final StudentCredentialManagementService studentCredentialManagementService;
    private final ParentBindingInvitationApplicationService parentBindingInvitationApplicationService;
    private final StudentClassAssignmentService studentClassAssignmentService;
    private final StudentQrTicketApplicationService studentQrTicketApplicationService;

    public StudentManagementController(
            StudentApplicationService studentApplicationService,
            StudentCredentialManagementService studentCredentialManagementService,
            ParentBindingInvitationApplicationService parentBindingInvitationApplicationService,
            StudentClassAssignmentService studentClassAssignmentService,
            StudentQrTicketApplicationService studentQrTicketApplicationService
    ) {
        this.studentApplicationService = studentApplicationService;
        this.studentCredentialManagementService = studentCredentialManagementService;
        this.parentBindingInvitationApplicationService = parentBindingInvitationApplicationService;
        this.studentClassAssignmentService = studentClassAssignmentService;
        this.studentQrTicketApplicationService = studentQrTicketApplicationService;
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
        return StudentClassAssignmentResponse.from(studentClassAssignmentService.assign(
                currentUser, studentId, new AssignStudentClassCommand(request.classOrganizationId())));
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
