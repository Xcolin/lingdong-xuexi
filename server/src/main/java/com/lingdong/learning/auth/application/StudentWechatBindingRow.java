package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 当前主监护人可管理学生及其微信绑定的数据库查询行。 */
public record StudentWechatBindingRow(
        Long studentId,
        String studentName,
        String studentAccount,
        Long bindingId,
        LocalDateTime boundAt
) {
}
