package com.lingdong.learning.student.web;

import jakarta.validation.constraints.NotNull;

/** 创建机构家长绑定邀请的请求体。 */
public record CreateParentBindingInvitationRequest(@NotNull(message = "机构标识不能为空") Long organizationId) {
}
