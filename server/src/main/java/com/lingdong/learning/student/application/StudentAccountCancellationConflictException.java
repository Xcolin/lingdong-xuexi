package com.lingdong.learning.student.application;

/** 学生仍有关联关系或注销条件在提交期间已经变化。 */
public class StudentAccountCancellationConflictException extends RuntimeException {
    public StudentAccountCancellationConflictException(String message) {
        super(message);
    }

    public StudentAccountCancellationConflictException(Throwable cause) {
        super("学生账号注销条件已发生变化", cause);
    }
}

