package com.lingdong.learning.student.web;
import com.lingdong.learning.student.application.BatchClassConfigurationService;import com.lingdong.learning.auth.application.AuthenticatedUser;import com.lingdong.learning.common.security.RequirePermission;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.List;
@RestController @RequestMapping("/api/v1/students")
public class BatchClassConfigurationController {
 private final BatchClassConfigurationService service;public BatchClassConfigurationController(BatchClassConfigurationService service){this.service=service;}
 public record Request(@Positive long sourceClassOrganizationId,@Positive long targetClassOrganizationId,@NotNull @Size(min=1,max=50) List<@NotNull @Positive Long> studentIds,@NotBlank @Size(max=200) String reason){}
 public record Result(int affectedCount,List<String> studentIds){}
 @PostMapping("/class-configurations:batch") @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
 public Result configure(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody Request request){var ids=service.configure(actor,request.sourceClassOrganizationId(),request.targetClassOrganizationId(),request.studentIds(),request.reason());return new Result(ids.size(),ids.stream().map(String::valueOf).toList());}
}
