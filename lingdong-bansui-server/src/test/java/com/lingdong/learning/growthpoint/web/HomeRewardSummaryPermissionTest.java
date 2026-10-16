package com.lingdong.learning.growthpoint.web;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class HomeRewardSummaryPermissionTest {
@Test void summaryKeepsExactMiniappReviewPermission() throws Exception {
var method=ParentRewardExchangeController.class.getMethod("summary",com.lingdong.learning.auth.application.AuthenticatedUser.class);
var permission=method.getAnnotation(com.lingdong.learning.common.security.RequirePermission.class);
assertThat(permission.value()).isEqualTo("MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD");assertThat(permission.anyOf()).isEmpty();
}
}
