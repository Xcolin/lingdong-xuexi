package com.lingdong.learning.auth.domain;

/** 用户可追溯的账号安全事件类型。 */
public enum AccountSecurityEventType {
    NEW_DEVICE_LOGIN,
    DEVICE_REVOKED,
    ALL_SESSIONS_REVOKED
}
