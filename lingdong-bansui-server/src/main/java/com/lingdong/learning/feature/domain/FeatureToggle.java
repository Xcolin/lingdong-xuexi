package com.lingdong.learning.feature.domain;
/** Immutable feature-toggle state. */
public record FeatureToggle(Long id, String code, String name, FeatureScope scope, Long organizationId,
                            String scopeKey, FeatureStatus status, boolean builtIn, String description, Long versionNo) {
    public FeatureToggle(Long id, String code, String name, FeatureScope scope, Long organizationId,
                         String scopeKey, FeatureStatus status, boolean builtIn, String description) {
        this(id,code,name,scope,organizationId,scopeKey,status,builtIn,description,0L);
    }
    public static FeatureToggle organizationOverride(Long id, String code, String name, Long organizationId, FeatureStatus status) {
        return new FeatureToggle(id, code, name, FeatureScope.ORGANIZATION, organizationId, "ORG:" + organizationId, status, false, null);
    }
}
