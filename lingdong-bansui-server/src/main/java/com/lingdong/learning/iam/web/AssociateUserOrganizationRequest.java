package com.lingdong.learning.iam.web;

import jakarta.validation.constraints.NotNull;

/** 建立用户与组织关联的 HTTP 请求。 */
public record AssociateUserOrganizationRequest(@NotNull Long organizationId) { }
