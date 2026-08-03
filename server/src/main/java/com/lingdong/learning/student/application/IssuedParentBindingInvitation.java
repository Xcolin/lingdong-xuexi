package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.ParentBindingInvitation;

/** 仅在创建调用返回时携带明文邀请令牌的临时结果。 */
public record IssuedParentBindingInvitation(ParentBindingInvitation invitation, String acceptToken) {
}
