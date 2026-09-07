package com.lingdong.learning.auth.web;

/** 学生微信临时凭证交换请求。 */
public record StudentWechatSessionRequest(String temporaryCode, String deviceId, String deviceName) {
}
