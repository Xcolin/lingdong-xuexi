package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.PreviousDayTaskCopyMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopyBatchRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopyItemRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopySourceRow;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 编排复制昨日任务的预览、逐条执行、幂等返回和失败重试。 */
@Service
public class PreviousDayTaskCopyService {
    private static final String TASK_FEATURE = "LEARNING_TASK_MANAGEMENT";
    private static final String COPY_FEATURE = "COPY_PREVIOUS_DAY_TASK";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final FeatureAccessService featureAccessService;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentMapper studentMapper;
    private final PreviousDayTaskCopyMapper copyMapper;
    private final PreviousDayTaskCopyTransactionService transactionService;
    private final Clock clock;

    public PreviousDayTaskCopyService(
            FeatureAccessService featureAccessService,
            ParentStudentMapper parentStudentMapper,
            StudentMapper studentMapper,
            PreviousDayTaskCopyMapper copyMapper,
            PreviousDayTaskCopyTransactionService transactionService,
            Clock clock
    ) {
        this.featureAccessService = featureAccessService;
        this.parentStudentMapper = parentStudentMapper;
        this.studentMapper = studentMapper;
        this.copyMapper = copyMapper;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    public PreviousDayTaskCopyPreview preview(AuthenticatedUser currentUser, Long studentId) {
        Student student = authorize(currentUser, studentId);
        LocalDate targetDate = businessDate();
        LocalDate sourceDate = targetDate.minusDays(1);
        List<TaskCopySourceRow> sources = copyMapper.findSourceTasks(
                currentUser.userId(), studentId, sourceDate);
        List<String> duplicates = copyMapper.findDuplicateTitles(
                currentUser.userId(), studentId, sourceDate, targetDate);
        TaskCopyBatchRow existing = copyMapper.findBatchByStudentAndTargetDate(studentId, targetDate);
        return new PreviousDayTaskCopyPreview(
                studentId, student.studentName(), sourceDate, targetDate, sources.size(),
                duplicates, existing != null, existing == null ? null : toResult(existing.id()));
    }

    public TaskCopyBatchResult copy(
            AuthenticatedUser currentUser, Long studentId, boolean confirmDuplicateTitles
    ) {
        authorize(currentUser, studentId);
        LocalDate targetDate = businessDate();
        PreparedTaskCopyBatch prepared = transactionService.prepare(
                currentUser.userId(), studentId, targetDate.minusDays(1), targetDate,
                confirmDuplicateTitles);
        if (!prepared.itemIds().isEmpty()) {
            for (Long itemId : prepared.itemIds()) {
                try {
                    transactionService.processItem(
                            prepared.batchId(), itemId, currentUser.userId(), false);
                } catch (RuntimeException exception) {
                    transactionService.markItemFailed(
                            prepared.batchId(), itemId, "TASK_COPY_FAILED", "任务复制失败", false);
                }
            }
            transactionService.refreshBatch(prepared.batchId());
        }
        return toResult(prepared.batchId());
    }

    public TaskCopyBatchResult retry(
            AuthenticatedUser currentUser, Long batchId, Long itemId
    ) {
        requirePositiveId(batchId, "复制批次标识不合法");
        requirePositiveId(itemId, "复制条目标识不合法");
        TaskCopyBatchRow batch = copyMapper.findBatchById(batchId);
        if (batch == null || !batch.createdByUserId().equals(currentUser.userId())) {
            throw new ResourceNotFoundException("复制批次不存在或不可管理");
        }
        authorize(currentUser, batch.studentId());
        TaskCopyItemRow item = copyMapper.findItemsByBatchId(batchId).stream()
                .filter(candidate -> candidate.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("复制条目不存在或不可管理"));
        if (!"FAILED".equals(item.status())) {
            throw new IllegalStateException("只有复制失败的条目可以重试");
        }
        try {
            transactionService.processItem(batchId, itemId, currentUser.userId(), true);
        } catch (RuntimeException exception) {
            transactionService.markItemFailed(
                    batchId, itemId, "TASK_COPY_FAILED", "任务复制失败", true);
        }
        transactionService.refreshBatch(batchId);
        return toResult(batchId);
    }

    private Student authorize(AuthenticatedUser currentUser, Long studentId) {
        featureAccessService.requireEnabled(TASK_FEATURE, null);
        featureAccessService.requireEnabled(COPY_FEATURE, null);
        requirePositiveId(studentId, "学生标识不合法");
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB
                || !currentUser.roleCodes().contains("PARENT")) {
            throw new IllegalArgumentException("仅活动主家长可复制昨日任务");
        }
        Student student = studentMapper.findById(studentId);
        if (student == null || student.status() != StudentStatus.ENABLED
                || !parentStudentMapper.existsActivePrimaryByParentAndStudent(
                currentUser.userId(), studentId)) {
            throw new ResourceNotFoundException("学生不存在或不可管理");
        }
        return student;
    }

    private TaskCopyBatchResult toResult(Long batchId) {
        TaskCopyBatchRow batch = copyMapper.findBatchById(batchId);
        if (batch == null) {
            throw new ResourceNotFoundException("复制批次不存在");
        }
        List<TaskCopyItemResult> items = copyMapper.findItemsByBatchId(batchId).stream()
                .map(this::toItemResult)
                .toList();
        return new TaskCopyBatchResult(
                batch.id(), batch.studentId(), batch.sourceDate(), batch.targetDate(),
                batch.status(), batch.totalCount(), batch.successCount(), batch.failureCount(), items);
    }

    private TaskCopyItemResult toItemResult(TaskCopyItemRow item) {
        return new TaskCopyItemResult(
                item.id(), item.sourceTaskId(), item.targetTaskId(), item.taskTitleSnapshot(),
                item.status(), item.failureCode(), item.failureMessage(), item.retryCount());
    }

    private LocalDate businessDate() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    private void requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
