package com.lingdong.learning.audit.application;

/** High-risk system operations that require a system auditor. */
public enum SystemTaskType {
    GLOBAL_ANNOUNCEMENT, ORGANIZATION_DISABLE, DATA_RESTORE, SENSITIVE_DATA_EXPORT,
    GLOBAL_FEATURE_TOGGLE, CACHE_CLEAR, INTERFACE_SERVICE_CHANGE,
    ORGANIZATION_ADMIN_SCOPE_EXPANSION, KEY_DICTIONARY_CHANGE, DATA_SCOPE_EXPANSION
}
