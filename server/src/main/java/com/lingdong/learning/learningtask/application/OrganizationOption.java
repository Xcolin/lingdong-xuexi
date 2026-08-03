package com.lingdong.learning.learningtask.application;

/** 任务表单可选择的最小组织信息。 */
public record OrganizationOption(
        Long id,
        String name,
        String organizationType,
        Long parentId,
        String organizationPath
) {
}
