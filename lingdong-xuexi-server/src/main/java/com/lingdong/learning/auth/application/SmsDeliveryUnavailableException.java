package com.lingdong.learning.auth.application;

/** 短信供应商未配置或暂不可用时的失败关闭异常。 */
public class SmsDeliveryUnavailableException extends RuntimeException {
    public SmsDeliveryUnavailableException() {
        super("短信服务暂不可用");
    }

    public SmsDeliveryUnavailableException(Throwable cause) {
        super("短信服务暂不可用", cause);
    }
}
