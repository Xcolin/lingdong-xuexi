package com.lingdong.learning.auth.web;

/** 验证家长当前手机号的请求。 */
public record ParentMobileChangeVerificationRequest(String code) {
}
