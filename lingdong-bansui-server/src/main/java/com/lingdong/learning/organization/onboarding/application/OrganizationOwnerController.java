package com.lingdong.learning.organization.onboarding.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/v1/organization-onboarding/organizations") public class OrganizationOwnerController{
 private final OrganizationOwnerGuard guard;public OrganizationOwnerController(OrganizationOwnerGuard guard){this.guard=guard;}
 @com.lingdong.learning.common.security.RequirePermission("ORGANIZATION_OWNER_TRANSFER") @GetMapping("/{id}/owner-transfer-options")public java.util.List<java.util.Map<String,String>> options(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id){return guard.options(actor.userId(),id);}
 public record Transfer(@jakarta.validation.constraints.NotNull Long targetUserId){}
 @com.lingdong.learning.common.security.RequirePermission("ORGANIZATION_OWNER_TRANSFER") @PostMapping("/{id}/owner-transfer")@ResponseStatus(HttpStatus.NO_CONTENT)public void transfer(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@jakarta.validation.Valid @RequestBody Transfer input){guard.transfer(actor.userId(),id,input.targetUserId());}
}
