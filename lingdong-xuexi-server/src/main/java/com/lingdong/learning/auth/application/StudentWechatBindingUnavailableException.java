package com.lingdong.learning.auth.application;

/** 学生当前不具备自助微信绑定条件。 */
public class StudentWechatBindingUnavailableException extends RuntimeException {
    public StudentWechatBindingUnavailableException() {
        super("当前学生无法自助绑定微信，请联系主监护人或机构处理");
    }
}
