package com.lingdong.learning.auth.application;

/** 学生微信绑定票据不存在、过期或已经消费。 */
public class StudentWechatTicketInvalidException extends RuntimeException {
    public StudentWechatTicketInvalidException() {
        super("学生微信绑定凭证无效或已过期");
    }
}
