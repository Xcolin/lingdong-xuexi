package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.growthpoint.application.GrowthReviewExportSelection;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer.Template;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 请求只提供对象和选择条件，申请人及来源由服务器认证上下文取得。 */
public record CreateGrowthReviewExportRequest(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{18}") String studentId,
        @Pattern(regexp = "[1-9][0-9]{18}") String reviewId,
        GrowthReviewPeriodType periodType, LocalDate dateFrom, LocalDate dateTo,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{18}") String templateId,
        @NotNull Template mode,
        @NotBlank @Size(max = 500) String reason) {
    public GrowthReviewExportSelection selection() {
        return new GrowthReviewExportSelection(Long.valueOf(studentId), reviewId == null ? null : Long.valueOf(reviewId),
                periodType, dateFrom, dateTo);
    }
}
