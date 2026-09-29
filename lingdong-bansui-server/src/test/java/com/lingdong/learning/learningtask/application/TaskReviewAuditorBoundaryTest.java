package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskReviewAuditorBoundaryTest {
    @Autowired TaskReviewService service;
    @Autowired FeatureToggleMapper features;
    @Test void auditorCannotEnterBusinessReviewEvenWithAnotherBusinessRole() {
        features.updateGlobalStatus("LEARNING_TASK_MANAGEMENT", FeatureStatus.ENABLED);
        for (var client : AuthClientType.values()) {
            var user = new AuthenticatedUser(1874244142494699001L,1874244142494699002L,"auditor","审核员",client,List.of("PARENT","SYS_AUDITOR"));
            assertThatThrownBy(() -> service.findPage(user,1,20)).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.approve(user,1874244142494699003L,1874244142494699004L)).isInstanceOf(SystemOperationAccessDeniedException.class);
        }
    }
}
