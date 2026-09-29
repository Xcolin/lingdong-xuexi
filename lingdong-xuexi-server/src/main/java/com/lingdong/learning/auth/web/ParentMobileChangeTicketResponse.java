package com.lingdong.learning.auth.web;

/** 当前手机号验证通过后仅返回一次的不透明换绑票据。 */
public record ParentMobileChangeTicketResponse(String ticket) {
}
