package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningTaskBatchPublishServiceTest {
    private final LearningTaskPublishTransactionService transactionService =
            mock(LearningTaskPublishTransactionService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final LearningTaskBatchPublishService service =
            new LearningTaskBatchPublishService(transactionService, featureAccessService);
    private final AuthenticatedUser currentUser = new AuthenticatedUser(
            101L, 201L, "parent", "家长", AuthClientType.WEB, List.of("PARENT"));

    @Test
    void rejectsEmptyDuplicateAndOversizedRequests() {
        assertThatThrownBy(() -> service.publish(currentUser, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.publish(currentUser, List.of(1L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.publish(
                currentUser, java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void continuesAfterFailureAndReturnsNeutralFailureReason() {
        when(transactionService.publish(currentUser, 11L))
                .thenReturn(new PublishLearningTaskResult(11L, 2, LearningTaskStatus.PUBLISHED));
        when(transactionService.publish(currentUser, 12L))
                .thenThrow(new ResourceNotFoundException("机密任务属于其他家长"));
        when(transactionService.publish(currentUser, 13L))
                .thenReturn(new PublishLearningTaskResult(13L, 1, LearningTaskStatus.PUBLISHED));

        BatchPublishLearningTasksResult result = service.publish(currentUser, List.of(11L, 12L, 13L));

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.items()).extracting(BatchPublishLearningTaskItemResult::taskId)
                .containsExactly(11L, 12L, 13L);
        assertThat(result.items().get(0).assignmentCount()).isEqualTo(2);
        assertThat(result.items().get(1).success()).isFalse();
        assertThat(result.items().get(1).failureReason())
                .isEqualTo("任务不可发布，请检查状态或数据范围")
                .doesNotContain("机密", "其他家长");
        assertThat(result.items().get(2).assignmentCount()).isEqualTo(1);
        verify(transactionService).publish(currentUser, 13L);
    }

    @Test
    void transactionWorkerUsesRequiresNewBoundary() throws Exception {
        Method method = LearningTaskPublishTransactionService.class.getMethod(
                "publish", AuthenticatedUser.class, Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
