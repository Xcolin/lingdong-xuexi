package com.lingdong.learning.auth.application;

/** 家长短信验证码用途，不同用途之间严格隔离。 */
public enum ParentSmsPurpose {
    REGISTER_OR_LOGIN,
    RESET_PASSWORD,
    WECHAT_BIND,
    STUDENT_WECHAT_BIND,
    SECONDARY_PARENT_BIND,
    PRIMARY_PARENT_TRANSFER,
    CHANGE_MOBILE_CURRENT,
    CHANGE_MOBILE_NEW,
    ACCOUNT_CANCELLATION,
    MANUAL_MOBILE_RECOVERY_NEW
}
