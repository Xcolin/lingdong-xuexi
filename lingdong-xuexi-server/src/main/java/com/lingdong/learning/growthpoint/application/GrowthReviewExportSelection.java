package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import java.time.LocalDate;

/** 单份标识与完整周期区间互斥，不把周/月报告截成任意日期片段。 */
public record GrowthReviewExportSelection(Long studentId, Long reviewId, GrowthReviewPeriodType periodType,
        LocalDate dateFrom, LocalDate dateTo) {
    public GrowthReviewExportSelection {
        if (studentId == null || studentId <= 0) throw new IllegalArgumentException("学生标识不合法");
        if (reviewId != null) {
            if (reviewId <= 0 || periodType != null || dateFrom != null || dateTo != null)
                throw new IllegalArgumentException("单份复盘与日期区间不能同时指定");
        } else if (periodType == null || dateFrom == null || dateTo == null || dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("请选择完整且有序的复盘周期区间");
        }
    }
}
