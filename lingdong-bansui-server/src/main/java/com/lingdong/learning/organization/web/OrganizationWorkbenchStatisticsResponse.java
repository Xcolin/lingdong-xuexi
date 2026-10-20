package com.lingdong.learning.organization.web;

import com.lingdong.learning.organization.application.OrganizationWorkbenchStatistics;

/** 工作台统计汇总响应。 */
public record OrganizationWorkbenchStatisticsResponse(
        Long campusCount,
        Long gradeCount,
        Long classCount,
        Long teacherCount,
        Long studentCount
) {
    public static OrganizationWorkbenchStatisticsResponse from(OrganizationWorkbenchStatistics statistics) {
        return new OrganizationWorkbenchStatisticsResponse(
                statistics.campusCount(),
                statistics.gradeCount(),
                statistics.classCount(),
                statistics.teacherCount(),
                statistics.studentCount()
        );
    }
}
