package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.exportjob.application.GrowthReviewExportAccessService;
import com.lingdong.learning.exportjob.application.GrowthReviewPdfTemplateService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 创建表单专用选项，先复核孩子范围及生成资格，不借用管理员配置接口。 */
@RestController
public class GrowthReviewExportOptionController {
    private final GrowthReviewExportAccessService access;
    private final GrowthReviewPdfTemplateService templates;

    public GrowthReviewExportOptionController(GrowthReviewExportAccessService access, GrowthReviewPdfTemplateService templates) {
        this.access = access;
        this.templates = templates;
    }

    @GetMapping("/api/v1/growth-review-export-jobs/options")
    @RequirePermission("EXPORT_JOB_CREATE")
    public List<GrowthReviewPdfTemplateService.Option> options(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long studentId) {
        access.requireCreate(user, studentId);
        return templates.findOptions();
    }
}
