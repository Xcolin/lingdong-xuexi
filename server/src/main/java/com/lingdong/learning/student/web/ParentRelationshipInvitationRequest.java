package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 创建副家长或监护权转移邀请的请求。 */
public record ParentRelationshipInvitationRequest(
        String mobile,
        AuthClientType clientType
) {
}
