package com.lingdong.learning.organization.onboarding.web;
import com.lingdong.learning.organization.onboarding.application.MobileVerificationService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/organization-onboarding") public class MobileVerificationController {
 private final MobileVerificationService service;public MobileVerificationController(MobileVerificationService service){this.service=service;}
 public record Code(String code){}
 @PostMapping("/mobile-code") @RequirePermission("ORGANIZATION_ONBOARDING_SUBMIT") public Object issue(@AuthenticationPrincipal AuthenticatedUser user){return service.issue(user.userId(),user.clientType());}
 @PostMapping("/mobile-verify") @RequirePermission("ORGANIZATION_ONBOARDING_SUBMIT") public void verify(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody Code code){service.verify(user.userId(),user.clientType(),code.code());}
}
