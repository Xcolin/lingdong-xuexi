package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthPointChangeType;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDateTime;

/** 积分明细导出的数据库受控投影。 */
public record GrowthPointExportRow(
        Long id,
        LocalDateTime occurredAt,
        String studentName,
        GrowthPointChangeType changeType,
        Long amount,
        Long availableDelta,
        LearningTaskSourceType sourceType,
        String sourceOrganizationName,
        String taskTitle,
        String reviewerName,
        String remark
) { }
