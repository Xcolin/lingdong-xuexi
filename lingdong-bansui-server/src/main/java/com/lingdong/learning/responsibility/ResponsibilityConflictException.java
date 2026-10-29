package com.lingdong.learning.responsibility;

public class ResponsibilityConflictException extends IllegalStateException {
    public ResponsibilityConflictException(){super("负责人已变更，请刷新重试");}
}
