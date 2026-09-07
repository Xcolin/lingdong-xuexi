package com.lingdong.learning.auth.application;

/** 当前协议版本尚未接受时阻止建立普通业务会话。 */
public class ParentAgreementAcceptanceRequiredException extends RuntimeException {
    private final String currentAgreementVersion;

    public ParentAgreementAcceptanceRequiredException(String currentAgreementVersion) {
        super("请先阅读并同意当前用户协议");
        this.currentAgreementVersion = currentAgreementVersion;
    }

    public String getCurrentAgreementVersion() {
        return currentAgreementVersion;
    }
}
