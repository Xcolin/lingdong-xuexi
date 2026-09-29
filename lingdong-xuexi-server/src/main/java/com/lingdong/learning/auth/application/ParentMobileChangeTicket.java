package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 旧手机号验证通过后签发的服务端票据上下文，不包含手机号明文。 */
public record ParentMobileChangeTicket(
        Long userId,
        AuthClientType clientType,
        String currentMobileDigest
) {
}
