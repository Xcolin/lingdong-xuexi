package com.lingdong.learning.auth.application;

/** 机构管理员完成线下核验后提交的家长手机号换绑命令。 */
public record ParentMobileManualRecoveryCommand(
        Long studentId,
        Long parentUserId,
        String newMobile,
        String smsCode,
        String reason,
        String confirmation
) {
}
