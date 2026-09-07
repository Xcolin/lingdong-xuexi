package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长关系邀请响应前的手机号核验命令。 */
public record ParentRelationshipMobileVerificationCommand(
        String mobile,
        String smsCode,
        ParentSmsPurpose purpose,
        AuthClientType clientType,
        boolean agreementAccepted,
        String agreementVersion,
        String sourceAddressHash
) {
}
