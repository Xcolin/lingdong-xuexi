package com.lingdong.learning.feature.application;
import com.lingdong.learning.feature.domain.FeatureStatus;
/** Input for a high-risk global feature-toggle change request. */
public record CreateGlobalFeatureToggleChangeCommand(Long submitterId, String featureCode, FeatureStatus targetStatus, String title, String description) { }
