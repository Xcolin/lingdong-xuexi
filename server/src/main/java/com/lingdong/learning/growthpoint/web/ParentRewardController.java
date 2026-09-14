package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.growthpoint.application.GrowthRewardService;
import com.lingdong.learning.student.domain.ParentRelationshipRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 小程序家长专属权限入口，复用奖励状态机及主副家长关系校验。 */
@RestController
@RequestMapping("/api/v1/parent-rewards")
public class ParentRewardController {
    private final GrowthRewardService service;
    public ParentRewardController(GrowthRewardService service) { this.service = service; }

    @RequirePermission(anyOf = {"MINIAPP_REWARD_MANAGE_CHILD", "MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD"})
    @GetMapping("/students")
    public List<StudentOption> students(@AuthenticationPrincipal AuthenticatedUser user) {
        requireMiniappParent(user);
        return service.findParentStudents(user).stream().map(student -> new StudentOption(
                student.studentId().toString(), student.studentName(), student.relationshipRole())).toList();
    }

    @RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")
    @GetMapping("/students/{studentId}")
    public GrowthRewardPageResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireMiniappParent(user);
        return GrowthRewardPageResponse.from(service.findManaged(user, studentId, page, pageSize));
    }

    @RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")
    @PostMapping("/students/{studentId}")
    @ResponseStatus(HttpStatus.CREATED)
    public GrowthRewardResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId, @Valid @RequestBody SaveGrowthRewardRequest request) {
        requireMiniappParent(user);
        return GrowthRewardResponse.from(service.create(user, studentId, request.toCommand()));
    }

    @RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")
    @PutMapping("/{rewardId}")
    public GrowthRewardResponse update(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long rewardId, @Valid @RequestBody SaveGrowthRewardRequest request) {
        requireMiniappParent(user);
        return GrowthRewardResponse.from(service.update(user, rewardId, request.toCommand()));
    }

    @RequirePermission("MINIAPP_REWARD_MANAGE_CHILD")
    @DeleteMapping("/{rewardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long rewardId) {
        requireMiniappParent(user);
        service.delete(user, rewardId);
    }

    static void requireMiniappParent(AuthenticatedUser user) {
        if (user == null || user.clientType() != AuthClientType.MINIAPP
                || !user.roleCodes().contains("PARENT") || user.roleCodes().contains("SYS_AUDITOR")) {
            throw new SystemOperationAccessDeniedException("仅小程序家长可访问家庭奖励，审核员不能参与业务操作");
        }
    }

    public record StudentOption(String studentId, String studentName, ParentRelationshipRole relationshipRole) { }
}
