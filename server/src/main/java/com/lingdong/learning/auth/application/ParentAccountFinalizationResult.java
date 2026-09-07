package com.lingdong.learning.auth.application;

/** 单个家长注销申请的终结结果。 */
public enum ParentAccountFinalizationResult {
    FINALIZED,
    DEFERRED_ACTIVE_RELATIONSHIP,
    SKIPPED
}
