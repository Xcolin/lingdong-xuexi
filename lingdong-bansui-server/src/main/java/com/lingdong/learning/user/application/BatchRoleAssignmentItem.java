package com.lingdong.learning.user.application;
/** 批量授予条目：用户与组织范围角色所需的最小组织归属。 */
public record BatchRoleAssignmentItem(Long userId, Long organizationId) { }
