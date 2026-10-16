package com.lingdong.learning.organization.onboarding.web;
import com.lingdong.learning.organization.onboarding.application.OrganizationOnboardingService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/organization-onboarding")
public class OrganizationOnboardingController {
 private final OrganizationOnboardingService service;private final SystemTaskApplicationService tasks;
 public OrganizationOnboardingController(OrganizationOnboardingService service,SystemTaskApplicationService tasks){this.service=service;this.tasks=tasks;}
 public record Family(String name,String idempotencyKey,Long regionId){} public record Version(Integer versionNo,Integer submissionBatch){} public record Review(String comment){}
 @GetMapping("/context") @RequirePermission("ORGANIZATION_ONBOARDING_READ") public Object context(@AuthenticationPrincipal AuthenticatedUser user){return service.context(user.userId());}
 @GetMapping("/regions") @RequirePermission("ORGANIZATION_ONBOARDING_READ") public Object regions(@AuthenticationPrincipal AuthenticatedUser user){return service.regions(user.userId());}
 @GetMapping("/my-organizations") @RequirePermission("ORGANIZATION_ONBOARDING_READ") public Object mine(@AuthenticationPrincipal AuthenticatedUser user){return service.myOrganizations(user.userId());}
 @PostMapping("/families") @RequirePermission("ORGANIZATION_ONBOARDING_SUBMIT") public Object family(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody Family form){return service.family(user.userId(),form.name(),form.idempotencyKey(),form.regionId());}
 @GetMapping("/applications") @RequirePermission("ORGANIZATION_ONBOARDING_READ") public Object applications(@AuthenticationPrincipal AuthenticatedUser user){return service.applications(user.userId());}
 @GetMapping("/applications/{id}") @RequirePermission("ORGANIZATION_ONBOARDING_READ") public Object application(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id){return service.application(user.userId(),id);}
 @PostMapping("/applications") @RequirePermission("ORGANIZATION_ONBOARDING_SUBMIT") public Object submit(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody OrganizationOnboardingService.Form form){return service.submit(user.userId(),form);}
 @PostMapping("/applications/{id}/withdraw") @RequirePermission("ORGANIZATION_ONBOARDING_SUBMIT") public Object withdraw(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@RequestBody Version form){return service.withdraw(user.userId(),id,form.versionNo(),form.submissionBatch());}
 @PostMapping("/applications/{id}/resubmit") @RequirePermission("ORGANIZATION_ONBOARDING_SUBMIT") public Object resubmit(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@RequestBody OrganizationOnboardingService.Form form){return service.resubmit(user.userId(),id,form);}
 @PostMapping("/tasks/{taskId}/approve") @RequirePermission("ORGANIZATION_ONBOARDING_REVIEW") public Object approve(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long taskId,@RequestBody Review form){service.requireOnboardingTask(taskId);return tasks.approve(taskId,user.userId(),form.comment());}
 @PostMapping("/tasks/{taskId}/reject") @RequirePermission("ORGANIZATION_ONBOARDING_REVIEW") public Object reject(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long taskId,@RequestBody Review form){service.requireOnboardingTask(taskId);return tasks.reject(taskId,user.userId(),form.comment());}
}

