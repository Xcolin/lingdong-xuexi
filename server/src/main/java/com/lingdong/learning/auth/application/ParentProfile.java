package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 家长认证档案中的首次登录与引导状态。 */
public record ParentProfile(
        Long id,
        Long userId,
        ParentOnboardingStatus onboardingStatus,
        LocalDateTime firstLoginAt,
        LocalDateTime onboardingCompletedAt
) {
}
