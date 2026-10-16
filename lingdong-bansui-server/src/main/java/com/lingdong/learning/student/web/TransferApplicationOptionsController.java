package com.lingdong.learning.student.web;
import com.lingdong.learning.student.application.transfer.TransferApplicationOptionsService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.Positive;
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/student-transfer-applications")
public class TransferApplicationOptionsController {
 private final TransferApplicationOptionsService service;private final com.lingdong.learning.student.application.transfer.TransferApplicationService applications;
 public TransferApplicationOptionsController(TransferApplicationOptionsService service,com.lingdong.learning.student.application.transfer.TransferApplicationService applications){this.service=service;this.applications=applications;}
 @GetMapping("/institution-options") @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public TransferApplicationOptionsService.Options institutionOptions(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam @Positive long studentId,@RequestParam @Positive long sourceInstitutionId){applications.institutionContext(actor,studentId,sourceInstitutionId);return service.institutionOptions(actor,studentId,sourceInstitutionId);}
 @GetMapping("/family-options") @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public TransferApplicationOptionsService.Options options(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam @Positive long studentId,@RequestParam @Positive long familyOrganizationId){return service.familyOptions(actor,studentId,familyOrganizationId);}
}
