package com.lingdong.learning.student.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 家长接受或拒绝邀请的请求体。 */
public record RespondParentBindingInvitationRequest(
        @NotBlank(message = "邀请令牌不能为空") @Size(max = 128, message = "邀请令牌长度不能超过 128 个字符") String acceptToken
) {
}
