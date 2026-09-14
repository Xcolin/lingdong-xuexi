package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 审核员即使兼任家长，也不能进入业务任务管理和候选查询。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningTaskAuditorBoundaryTest {
    @Autowired LearningTaskManagementService management;
    @Autowired LearningTaskOptionService options;
    @Autowired LearningTaskScopeService scope;
    @Autowired FeatureToggleMapper features;

    @Test void mixedAuditorCannotListFamilyTasks() {
        features.updateGlobalStatus("LEARNING_TASK_MANAGEMENT", FeatureStatus.ENABLED);
        for (var client : AuthClientType.values()) {
            assertThatThrownBy(() -> management.findPage(auditor(client), LearningTaskSourceType.FAMILY,
                    null, null, null, 1, 20)).isInstanceOf(SystemOperationAccessDeniedException.class);
        }
    }

    @Test void mixedAuditorCannotReadFamilyStudentOptions() {
        features.updateGlobalStatus("LEARNING_TASK_MANAGEMENT", FeatureStatus.ENABLED);
        for (var client : AuthClientType.values()) {
            assertThatThrownBy(() -> options.students(auditor(client), LearningTaskSourceType.FAMILY,
                    null, null)).isInstanceOf(SystemOperationAccessDeniedException.class);
        }
    }

    private AuthenticatedUser auditor(AuthClientType client) {
        return new AuthenticatedUser(1874244142494699001L, 1874244142494699002L,
                "auditor", "审核员", client, List.of("PARENT", "SYS_AUDITOR"));
    }

    @Test void mixedAuditorCannotManageOwnDraftOrResolveReviewer() {
        var draft = new ValidatedLearningTaskDraft("边界草稿", 1, 10, 10,
                LocalDate.now().plusDays(1), null, List.of(), null, List.of());
        for (var client : AuthClientType.values()) {
            var user = auditor(client);
            var task = LearningTask.draft(1874244142494699003L, LearningTaskSourceType.FAMILY,
                    null, user.userId(), user.userId(), draft);
            assertThatThrownBy(() -> scope.requireManageable(user, task))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> scope.validateAndResolveReviewer(user, LearningTaskSourceType.FAMILY,
                    null, null, draft)).isInstanceOf(SystemOperationAccessDeniedException.class);
        }
    }
}
