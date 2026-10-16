package com.lingdong.learning.student.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.ClassAccountAssociationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController
@RequestMapping("/api/v1/classes/{classId}/account-associations")
public class ClassAccountAssociationController {
 private final ClassAccountAssociationService service;
 public ClassAccountAssociationController(ClassAccountAssociationService service){this.service=service;}
 public record Request(@NotBlank @Size(max=64) String studentAccount,@NotBlank @Pattern(regexp="\\d{4}") String loginCode,@NotBlank @Size(max=128) String deviceId,@NotBlank @Size(max=100) String deviceName,String captchaChallengeId,String captchaAnswer,@NotNull @AssertTrue Boolean consent){@Override public String toString(){return "AccountAssociation[proof=REDACTED]";}}
 @PostMapping @RequirePermission("STUDENT_CLASS_ASSIGN")
 public StudentOrganizationRelationshipResponse associate(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long classId,@Valid @RequestBody Request request,jakarta.servlet.http.HttpServletRequest servlet){return StudentOrganizationRelationshipResponse.from(service.associate(actor,classId,new StudentCodeLoginCommand(request.studentAccount(),request.loginCode(),request.deviceId(),request.deviceName(),request.captchaChallengeId(),request.captchaAnswer(),servlet.getRemoteAddr())));}
}
