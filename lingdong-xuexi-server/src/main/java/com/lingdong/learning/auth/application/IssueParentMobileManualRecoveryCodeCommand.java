package com.lingdong.learning.auth.application;

/** 机构管理员向目标家长的新手机号发送人工换绑专用验证码。 */
public record IssueParentMobileManualRecoveryCodeCommand(
        Long studentId,
        Long parentUserId,
        String newMobile,
        String sourceDigest
) {
}
