package com.lingdong.learning.studentdirectory;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/students/institution-directory")
public class InstitutionStudentDirectoryController {
 private final InstitutionStudentDirectoryService service;
 public InstitutionStudentDirectoryController(InstitutionStudentDirectoryService service){this.service=service;}
 @GetMapping @RequirePermission("STUDENT_READ")
 public InstitutionStudentDirectoryService.Page list(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long institutionOrganizationId,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long gradeOrganizationId,@RequestParam(required=false) Long classOrganizationId,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.list(actor,institutionOrganizationId,keyword,gradeOrganizationId,classOrganizationId,status,page,pageSize);}
 @GetMapping("/options") @RequirePermission("STUDENT_READ")
 public InstitutionStudentDirectoryService.Options options(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long institutionOrganizationId){return service.options(actor,institutionOrganizationId);}
 @GetMapping("/{studentId}") @RequirePermission("STUDENT_READ")
 public InstitutionStudentDirectoryService.Item detail(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long institutionOrganizationId,@PathVariable Long studentId){return service.detail(actor,institutionOrganizationId,studentId);}
}
