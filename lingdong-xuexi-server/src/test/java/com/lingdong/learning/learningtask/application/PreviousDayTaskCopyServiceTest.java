package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.PreviousDayTaskCopyMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopyBatchRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopyItemRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopySourceRow;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreviousDayTaskCopyServiceTest {
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final PreviousDayTaskCopyMapper copyMapper = mock(PreviousDayTaskCopyMapper.class);
    private final PreviousDayTaskCopyTransactionService transactionService =
            mock(PreviousDayTaskCopyTransactionService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final PreviousDayTaskCopyService service = new PreviousDayTaskCopyService(
            featureAccessService, parentStudentMapper, studentMapper, copyMapper,
            transactionService, clock);
    private final AuthenticatedUser parent = new AuthenticatedUser(
            44L, 55L, "parent", "家长", AuthClientType.WEB, List.of("PARENT"));

    @BeforeEach
    void setUp() {
        when(parentStudentMapper.existsActivePrimaryByParentAndStudent(44L, 22L)).thenReturn(true);
        when(studentMapper.findById(22L)).thenReturn(Student.create(22L, "小灵", "GRADE_3", 66L));
        when(copyMapper.findSourceTasks(44L, 22L, LocalDate.of(2026, 8, 7)))
                .thenReturn(List.of(new TaskCopySourceRow(101L, "每日阅读"),
                        new TaskCopySourceRow(102L, "口算练习")));
        when(copyMapper.findDuplicateTitles(
                44L, 22L, LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8)))
                .thenReturn(List.of("每日阅读"));
    }

    @Test
    void previewsYesterdayUsingShanghaiBusinessDateAndReportsDuplicateTitles() {
        PreviousDayTaskCopyPreview preview = service.preview(parent, 22L);

        assertThat(preview.sourceDate()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(preview.targetDate()).isEqualTo(LocalDate.of(2026, 8, 8));
        assertThat(preview.candidateCount()).isEqualTo(2);
        assertThat(preview.duplicateTitles()).containsExactly("每日阅读");
        assertThat(preview.alreadyCopied()).isFalse();
    }

    @Test
    void rejectsNonParentWithoutReadingStudentTasks() {
        AuthenticatedUser teacher = new AuthenticatedUser(
                77L, 88L, "teacher", "教师", AuthClientType.WEB, List.of("TEACHER"));

        assertThatThrownBy(() -> service.preview(teacher, 22L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅活动主家长可复制昨日任务");

        verify(copyMapper, never()).findSourceTasks(77L, 22L, LocalDate.of(2026, 8, 7));
    }

    @Test
    void processesEachNewBatchItemIndependentlyAndReturnsFailureSummary() {
        PreparedTaskCopyBatch prepared = new PreparedTaskCopyBatch(
                9001L, false, List.of(9101L, 9102L));
        when(transactionService.prepare(
                44L, 22L, LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8), true))
                .thenReturn(prepared);
        RuntimeException itemFailure = new IllegalStateException("任务标签复制失败");
        org.mockito.Mockito.doThrow(itemFailure)
                .when(transactionService).processItem(9001L, 9102L, 44L, false);
        when(copyMapper.findBatchById(9001L)).thenReturn(batch("PARTIAL_FAILED", 2, 1, 1));
        when(copyMapper.findItemsByBatchId(9001L)).thenReturn(List.of(
                item(9101L, "每日阅读", "SUCCESS", null),
                item(9102L, "口算练习", "FAILED", "任务复制失败")));

        TaskCopyBatchResult result = service.copy(parent, 22L, true);

        verify(transactionService).processItem(9001L, 9101L, 44L, false);
        verify(transactionService).processItem(9001L, 9102L, 44L, false);
        verify(transactionService).markItemFailed(
                9001L, 9102L, "TASK_COPY_FAILED", "任务复制失败", false);
        verify(transactionService).refreshBatch(9001L);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
    }

    @Test
    void repeatedRequestReturnsExistingBatchWithoutReplayingItems() {
        PreparedTaskCopyBatch prepared = new PreparedTaskCopyBatch(9001L, true, List.of());
        when(transactionService.prepare(
                44L, 22L, LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8), true))
                .thenReturn(prepared);
        when(copyMapper.findBatchById(9001L)).thenReturn(batch("COMPLETED", 1, 1, 0));
        when(copyMapper.findItemsByBatchId(9001L)).thenReturn(List.of(
                item(9101L, "每日阅读", "SUCCESS", null)));

        TaskCopyBatchResult result = service.copy(parent, 22L, true);

        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(transactionService, never()).processItem(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void repeatedRequestResumesOnlyPendingItemsFromInterruptedBatch() {
        PreparedTaskCopyBatch prepared = new PreparedTaskCopyBatch(
                9001L, true, List.of(9102L));
        when(transactionService.prepare(
                44L, 22L, LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8), true))
                .thenReturn(prepared);
        when(copyMapper.findBatchById(9001L)).thenReturn(batch("COMPLETED", 2, 2, 0));
        when(copyMapper.findItemsByBatchId(9001L)).thenReturn(List.of(
                item(9101L, "每日阅读", "SUCCESS", null),
                item(9102L, "口算练习", "SUCCESS", null)));

        TaskCopyBatchResult result = service.copy(parent, 22L, true);

        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(transactionService).processItem(9001L, 9102L, 44L, false);
        verify(transactionService, never()).processItem(9001L, 9101L, 44L, false);
        verify(transactionService).refreshBatch(9001L);
    }

    @Test
    void rejectsRetryForSuccessfulItem() {
        when(copyMapper.findBatchById(9001L)).thenReturn(batch("COMPLETED", 1, 1, 0));
        when(copyMapper.findItemsByBatchId(9001L)).thenReturn(List.of(
                item(9101L, "每日阅读", "SUCCESS", null)));

        assertThatThrownBy(() -> service.retry(parent, 9001L, 9101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("只有复制失败的条目可以重试");

        verify(transactionService, never()).processItem(9001L, 9101L, 44L, true);
    }

    private TaskCopyBatchRow batch(String status, int total, int success, int failure) {
        return new TaskCopyBatchRow(
                9001L, 22L, LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8),
                44L, true, status, total, success, failure, null, null);
    }

    private TaskCopyItemRow item(Long id, String title, String status, String failureMessage) {
        return new TaskCopyItemRow(
                id, 9001L, id + 100L, "SUCCESS".equals(status) ? id + 200L : null,
                title, status, failureMessage == null ? null : "TASK_COPY_FAILED",
                failureMessage, 0, null, null, null);
    }
}
