package com.lingdong.learning.student.web;
import com.lingdong.learning.student.application.transfer.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/student-transfer-applications")
public class TransferApplicationController {
 private final TransferApplicationService service;private final TransferApplicationPresentation presentation;
 public TransferApplicationController(TransferApplicationService service,TransferApplicationPresentation presentation){this.service=service;this.presentation=presentation;}
 private Response response(TransferApplicationRepository.Stored stored){return Response.from(stored,presentation.display(stored));}
 public record Submit(@NotNull @Positive Long studentId,@NotNull @Positive Long sourceInstitutionId,
  @NotNull @Positive Long familyOrganizationId,@NotBlank @Size(max=200) String targetDescription,
  @NotBlank @Size(max=200) String reason,@NotNull LocalDate effectiveDate,
  @NotNull @Size(max=3) List<@NotNull @Positive Long> attachmentIds){}
 public record InstitutionSubmit(@NotNull @Positive Long studentId,@NotNull @Positive Long sourceInstitutionId,
  @NotBlank @Size(max=200) String targetDescription,@NotBlank @Size(max=200) String reason,
  @NotNull LocalDate effectiveDate,@NotNull @Size(max=3) List<@NotNull @Positive Long> attachmentIds){}
 @PostMapping("/institution-submissions") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public Response submitInstitution(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody InstitutionSubmit r){return response(service.submitInstitution(actor,r.studentId(),r.sourceInstitutionId(),r.targetDescription(),r.reason(),r.effectiveDate(),r.attachmentIds()));}
 public record Review(@Positive int versionNo,@Size(max=200) String reason){}
 public record Version(@Positive int versionNo){}
 public record Page(List<Response> items,long total,int page,int pageSize){}
 @GetMapping @RequirePermission("STUDENT_TRANSFER_VIEW")
 public Page list(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam @Positive long organizationId,
   @RequestParam(required=false) String status,@RequestParam(defaultValue="1") @Min(1) int page,
   @RequestParam(defaultValue="10") @Min(1) @Max(100) int pageSize){
  var result=service.list(actor,organizationId,status,page,pageSize);
  return new Page(result.items().stream().map(this::response).toList(),result.total(),result.page(),result.pageSize());
 }
 public record Display(String studentName,String institutionName,String reviewReason,java.time.LocalDateTime reviewedAt,java.time.LocalDateTime createdAt,java.time.LocalDateTime completedAt,List<TransferMaterialController.Uploaded> proofs){}
 public record Response(String id,String studentId,String sourceInstitutionId,String familyOrganizationId,
  String applicantUserId,String targetDescription,String reason,LocalDate effectiveDate,List<String> attachmentIds,
  String status,int versionNo,boolean executionBlocked,String executionErrorCode,Display display){
  static Response from(TransferApplicationRepository.Stored stored){return from(stored,null);}
  static Response from(TransferApplicationRepository.Stored stored,Display display){var a=stored.application();return new Response(Long.toString(stored.id()),Long.toString(a.studentId()),Long.toString(a.sourceInstitutionId()),Long.toString(stored.familyId()),Long.toString(a.applicantId()),a.targetDescription(),a.reason(),a.effectiveDate(),a.attachmentIds().stream().map(String::valueOf).toList(),a.status().name(),stored.version(),stored.executionBlocked(),stored.executionErrorCode(),display);}
 }
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public Response submit(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody Submit r){return response(service.submit(actor,r.studentId(),r.sourceInstitutionId(),r.familyOrganizationId(),r.targetDescription(),r.reason(),r.effectiveDate(),r.attachmentIds()));}
 @GetMapping("/{id}") @RequirePermission("STUDENT_TRANSFER_VIEW")
 public Response read(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id){return response(service.read(actor,id));}
 @PostMapping("/{id}/approve") @RequirePermission("STUDENT_TRANSFER_REVIEW")
 public Response approve(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id,@Valid @RequestBody Review r){return response(service.review(actor,id,r.versionNo(),true,r.reason()));}
 @PostMapping("/{id}/reject") @RequirePermission("STUDENT_TRANSFER_REVIEW")
 public Response reject(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id,@Valid @RequestBody Review r){return response(service.review(actor,id,r.versionNo(),false,r.reason()));}
 @PostMapping("/{id}/reject-blocked") @RequirePermission("STUDENT_TRANSFER_REVIEW")
 public Response rejectBlocked(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id,@Valid @RequestBody Review r){return response(service.rejectBlocked(actor,id,r.versionNo(),r.reason()));}
 @PostMapping("/{id}/withdraw") @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public Response withdraw(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id,@Valid @RequestBody Version r){return response(service.withdraw(actor,id,r.versionNo()));}
}
