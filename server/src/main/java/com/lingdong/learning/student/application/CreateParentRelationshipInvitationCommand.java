package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;

/** 活动主家长创建关系邀请的命令。 */
public record CreateParentRelationshipInvitationCommand(
        Long operatorUserId,
        Long studentId,
        String inviteeMobile,
        ParentRelationshipInvitationType invitationType,
        AuthClientType clientType,
        String sourceAddressHash
) {
}
