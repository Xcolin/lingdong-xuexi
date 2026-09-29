package com.lingdong.learning.auth.application;

/** 最终绑定前由服务端固定的学生、主监护人手机号、微信身份和设备。 */
public record StudentWechatVerificationTicket(
        ParentWechatIdentity identity,
        Long studentId,
        Long studentUserId,
        Long primaryParentUserId,
        String primaryParentMobile,
        String deviceId
) {
}
