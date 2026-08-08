package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.growthpoint.application.CorrectGrowthPointCommand;
import com.lingdong.learning.growthpoint.application.GrowthPointCorrectionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 主家长积分纠错入口。 */
@RestController
@RequestMapping("/api/v1/growth-points/students/{studentId}/corrections")
public class GrowthPointCorrectionController {
    private final GrowthPointCorrectionService correctionService;

    public GrowthPointCorrectionController(GrowthPointCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @PostMapping
    @RequirePermission("GROWTH_POINT_CORRECT_CHILD")
    public GrowthPointCorrectionResponse correct(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @Valid @RequestBody CorrectGrowthPointRequest request
    ) {
        return GrowthPointCorrectionResponse.from(correctionService.correct(
                currentUser, studentId,
                new CorrectGrowthPointCommand(request.originalLedgerId(), request.reason())));
    }
}
