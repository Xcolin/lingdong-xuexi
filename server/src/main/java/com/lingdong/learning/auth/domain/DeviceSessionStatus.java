package com.lingdong.learning.auth.domain;

/** 设备会话的可撤销生命周期状态。 */
public enum DeviceSessionStatus {
    ACTIVE,
    SIGNED_OUT,
    REVOKED,
    EXPIRED
}
