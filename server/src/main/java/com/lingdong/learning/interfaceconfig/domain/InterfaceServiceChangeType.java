package com.lingdong.learning.interfaceconfig.domain;

/** 必须关联系统审核任务的高风险接口服务变更。 */
public enum InterfaceServiceChangeType {
    CREATE,
    ENABLE,
    DISABLE,
    CHANGE_AUTHORIZATION
}
