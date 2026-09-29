package com.lingdong.learning.auth.web;

/** 携带旧号验证票据向新手机号发送验证码。 */
public record ParentMobileChangeNewCodeRequest(String ticket, String newMobile) {
}
