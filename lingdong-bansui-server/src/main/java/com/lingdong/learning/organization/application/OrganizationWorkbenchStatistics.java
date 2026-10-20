package com.lingdong.learning.organization.application;

/** 工作台统计汇总：无对应 read 权限的计数为 null，由前端按缺失展示。 */
public record OrganizationWorkbenchStatistics(
        Long campusCount,
        Long gradeCount,
        Long classCount,
        Long teacherCount,
        Long studentCount
) {
}
