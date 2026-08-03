package com.lingdong.learning.interfaceconfig.domain;

/** High-risk mutations that require a linked system-audit task. */
public enum InterfaceServiceChangeType {
    CREATE,
    DISABLE,
    CHANGE_AUTHORIZATION
}
