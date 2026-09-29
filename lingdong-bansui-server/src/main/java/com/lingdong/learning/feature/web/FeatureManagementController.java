package com.lingdong.learning.feature.web;

import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.feature.application.FeatureManagementService;
import com.lingdong.learning.feature.application.FeatureManagementService.*;
import com.lingdong.learning.feature.domain.FeatureStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 全局开关查询和领域审批接口；管理入口自身不受可自锁的功能开关控制。 */
@RestController
@RequestMapping("/api/v1/feature-management")
public class FeatureManagementController {
    private final FeatureManagementService service;
    public FeatureManagementController(FeatureManagementService service){this.service=service;}
    @GetMapping("/toggles") @RequirePermission("FEATURE_TOGGLE_READ")
    public List<ToggleView> toggles(@AuthenticationPrincipal AuthenticatedUser user){return service.listToggles(user);}
    @GetMapping("/changes") @RequirePermission("FEATURE_TOGGLE_READ")
    public Page changes(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(required=false) SystemTaskStatus taskStatus,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.listChanges(user,taskStatus,page,pageSize,false);}
    @GetMapping("/review-queue") @RequirePermission("FEATURE_TOGGLE_REVIEW")
    public Page queue(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.listChanges(user,null,page,pageSize,true);}
    @PostMapping("/review-submissions") @ResponseStatus(HttpStatus.CREATED) @RequirePermission("FEATURE_TOGGLE_MANAGE")
    public ChangeView submit(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody SubmitRequest r){return service.submit(user,r.featureCode(),r.targetStatus(),r.expectedVersion(),r.title(),r.description(),r.confirmed());}
    @PostMapping("/review-queue/{taskId}/approve") @RequirePermission("FEATURE_TOGGLE_REVIEW")
    public ChangeView approve(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long taskId,@Valid @RequestBody ReviewRequest r){return service.review(user,taskId,r.comment(),true);}
    @PostMapping("/review-queue/{taskId}/reject") @RequirePermission("FEATURE_TOGGLE_REVIEW")
    public ChangeView reject(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long taskId,@Valid @RequestBody ReviewRequest r){return service.review(user,taskId,r.comment(),false);}
    public record SubmitRequest(@NotBlank @Size(max=64) String featureCode,@NotNull FeatureStatus targetStatus,@NotNull @PositiveOrZero Long expectedVersion,@NotBlank @Size(max=100) String title,@NotBlank @Size(max=1000) String description,@AssertTrue boolean confirmed){}
    public record ReviewRequest(@Size(max=500) String comment){}
}
