package com.lingdong.learning.auth.application;

/** 学生微信临时凭证交换命令。 */
public record StudentWechatSessionCommand(String temporaryCode, String deviceId, String deviceName) {
}
