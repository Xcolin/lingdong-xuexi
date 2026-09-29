package com.lingdong.learning.iam.audit.application;

/** 审计记录的主要业务对象类型。 */
public enum IamChangeTargetType {
    USER,
    ROLE,
    PERMISSION,
    ROLE_PERMISSION,
    USER_PERMISSION,
    ROLE_DATA_SCOPE,
    ORGANIZATION_ADMIN,
    USER_ORGANIZATION,
    USER_ROLE
}
