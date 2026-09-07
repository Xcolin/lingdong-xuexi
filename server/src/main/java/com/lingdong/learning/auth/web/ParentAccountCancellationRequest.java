package com.lingdong.learning.auth.web;

/** 家长账号注销冷静期申请。 */
public record ParentAccountCancellationRequest(String code, String confirmation) {
}
