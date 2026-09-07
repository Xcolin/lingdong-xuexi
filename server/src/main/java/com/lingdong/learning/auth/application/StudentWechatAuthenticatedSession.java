package com.lingdong.learning.auth.application;

/** 学生微信快捷登录或绑定成功后的会话和学生账号。 */
public record StudentWechatAuthenticatedSession(AuthenticatedSession session, String studentAccount) {
}
