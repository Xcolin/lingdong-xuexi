package com.lingdong.learning.auth.application;

/** 注销申请不满足关系前置条件或当前状态不允许操作。 */
public class ParentAccountCancellationConflictException extends RuntimeException {
    public ParentAccountCancellationConflictException(String message) {
        super(message);
    }
}
