package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.growthpoint.application.AnonymousRankQueryService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.AnonymousRankMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 本人偏好与匿名查询入口，业务服务逐次检查当前身份和对象关系。 */
@RestController
@RequestMapping("/api/v1/anonymous-ranks")
public class AnonymousRankController {
    private final AnonymousRankQueryService service;
    public AnonymousRankController(AnonymousRankQueryService service) { this.service=service; }
    @GetMapping("/preferences")
    public List<AnonymousRankQueryService.WithdrawalOption> withdrawals(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.withdrawals(user);
    }
    @GetMapping("/students")
    public List<com.lingdong.learning.growthpoint.application.AnonymousRankAccess.StudentOption> students(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.students(user);
    }
    @GetMapping("/students/{studentId}/classes")
    public List<AnonymousRankMapper.ClassOption> classes(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId) {
        return service.classes(user,studentId);
    }
    @GetMapping("/students/{studentId}/classes/{classId}")
    public List<AnonymousRankMapper.Row> ranking(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId, @PathVariable Long classId) {
        return service.ranking(user,studentId,classId);
    }
    @GetMapping("/students/{studentId}/classes/{classId}/preference")
    public AnonymousRankQueryService.Preference preference(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId, @PathVariable Long classId) {
        return service.preference(user,studentId,classId);
    }
    @PutMapping("/students/{studentId}/classes/{classId}/preference")
    public AnonymousRankQueryService.Preference set(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId, @PathVariable Long classId, @Valid @RequestBody Request request) {
        return service.set(user,studentId,classId,request.enabled(),request.version());
    }
    public record Request(@NotNull Boolean enabled, @NotNull @Min(0) Long version) { }
}
