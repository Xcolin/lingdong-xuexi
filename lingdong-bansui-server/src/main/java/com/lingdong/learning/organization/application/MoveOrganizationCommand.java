package com.lingdong.learning.organization.application;
/** 组织改父级移动命令：直接生效并重建子树路径。 */
public record MoveOrganizationCommand(Long operatorId, Long organizationId, Long targetParentId, Integer expectedVersion) { }
