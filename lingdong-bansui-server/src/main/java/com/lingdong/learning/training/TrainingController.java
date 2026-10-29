package com.lingdong.learning.training;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/institution-enrollment")
public class TrainingController {
 private final TrainingService service;
 public TrainingController(TrainingService service){this.service=service;}
 public record Registration(@NotNull Long institutionOrganizationId,@NotBlank String programType,@NotBlank String enrollable){}
 public record Submission(@NotNull Long studentId,@NotNull Long familyOrganizationId,@NotNull Long institutionOrganizationId,@NotNull Long programOrganizationId,@NotBlank String requestType,@Size(max=200) String reason){}
 public record Review(@Size(max=200) String reviewReason){}
 @GetMapping("/training-institutions") @RequirePermission(TrainingService.CREATE)
 public java.util.List<Map<String,Object>> institutions(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long studentId,@RequestParam Long familyOrganizationId){return service.institutions(actor,studentId,familyOrganizationId);}
 @GetMapping("/training-programs") @RequirePermission(TrainingService.CREATE)
 public TrainingService.Page programs(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long institutionOrganizationId,@RequestParam Long studentId,@RequestParam Long familyOrganizationId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.programs(actor,institutionOrganizationId,studentId,familyOrganizationId,page,pageSize,false);}
 @GetMapping("/training-program-registrations") @RequirePermission(TrainingService.MANAGE)
 public TrainingService.Page registrations(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long institutionOrganizationId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.programs(actor,institutionOrganizationId,null,null,page,pageSize,true);}
 @GetMapping("/training-program-candidates") @RequirePermission(TrainingService.MANAGE)
 public TrainingService.Page programCandidates(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long institutionOrganizationId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.programCandidates(actor,institutionOrganizationId,page,pageSize);}
 @PutMapping("/training-programs/{programOrganizationId}") @RequirePermission(TrainingService.MANAGE)
 public Map<String,Object> register(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long programOrganizationId,@Valid @RequestBody Registration body){return service.register(actor,programOrganizationId,body.institutionOrganizationId(),body.programType(),body.enrollable());}
 @PostMapping("/training-requests") @RequirePermission(TrainingService.CREATE)
 public Map<String,Object> create(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody Submission body){return service.create(actor,body.studentId(),body.familyOrganizationId(),body.institutionOrganizationId(),body.programOrganizationId(),body.requestType(),body.reason());}
 @GetMapping("/training-requests") @RequirePermission(anyOf={TrainingService.CREATE,TrainingService.REVIEW})
 public TrainingService.Page requests(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(required=false) Long institutionOrganizationId,@RequestParam(required=false) Long familyOrganizationId,@RequestParam(required=false) Long studentId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.requests(actor,institutionOrganizationId,familyOrganizationId,studentId,status,page,pageSize);}
 @GetMapping("/training-requests/{id}") @RequirePermission(anyOf={TrainingService.CREATE,TrainingService.REVIEW})
 public Map<String,Object> detail(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id){return service.detail(actor,id);}
 @PostMapping("/training-requests/{id}/withdraw") @RequirePermission(TrainingService.CREATE)
 public Map<String,Object> withdraw(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id){return service.withdraw(actor,id);}
 @PostMapping("/training-requests/{id}/approve") @RequirePermission(TrainingService.REVIEW)
 public Map<String,Object> approve(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@Valid @RequestBody(required=false) Review body){return service.review(actor,id,true,body==null?null:body.reviewReason());}
 @PostMapping("/training-requests/{id}/reject") @RequirePermission(TrainingService.REVIEW)
 public Map<String,Object> reject(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@Valid @RequestBody Review body){return service.review(actor,id,false,body.reviewReason());}
 @GetMapping("/training-participations") @RequirePermission(anyOf={TrainingService.CREATE,TrainingService.REVIEW})
 public TrainingService.Page participations(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(required=false) Long institutionOrganizationId,@RequestParam(required=false) Long familyOrganizationId,@RequestParam(required=false) Long studentId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.participations(actor,institutionOrganizationId,familyOrganizationId,studentId,status,page,pageSize);}
}


