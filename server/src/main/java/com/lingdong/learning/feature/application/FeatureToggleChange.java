package com.lingdong.learning.feature.application;
import com.lingdong.learning.feature.domain.FeatureStatus;
/** Links one approved system task to its requested global feature state. */
public record FeatureToggleChange(Long taskId, String featureCode, FeatureStatus targetStatus) { }
