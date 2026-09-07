package com.lingdong.learning.auth.web;

/** 完成家长手机号换绑的请求。 */
public record ParentMobileChangeRequest(String ticket, String newMobile, String code) {
}
