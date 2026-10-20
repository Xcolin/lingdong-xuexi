package com.lingdong.learning.auth.web;

/** 家长账号注销冷静期申请（reason/note 为 8.4 独立表单新增可选字段）。 */
public record ParentAccountCancellationRequest(String code, String confirmation, String reason, String note) {
}
