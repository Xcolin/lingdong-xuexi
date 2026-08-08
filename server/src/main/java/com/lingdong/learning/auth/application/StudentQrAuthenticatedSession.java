package com.lingdong.learning.auth.application;

/** 扫码登录成功后的会话及学生账号，供小程序本地展示。 */
public record StudentQrAuthenticatedSession(AuthenticatedSession session, String studentAccount) {
}
