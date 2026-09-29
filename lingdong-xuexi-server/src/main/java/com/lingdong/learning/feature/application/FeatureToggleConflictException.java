package com.lingdong.learning.feature.application;
/** 快照过期或缺失时拒绝覆盖当前开关。 */
public class FeatureToggleConflictException extends IllegalStateException {
    public FeatureToggleConflictException(String message) { super(message); }
}
