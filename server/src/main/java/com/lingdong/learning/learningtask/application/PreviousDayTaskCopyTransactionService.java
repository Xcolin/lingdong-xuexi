package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTaskAssignment;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskTag;
import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTagMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTargetMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.PreviousDayTaskCopyMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopyBatchRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopyCountRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopyItemRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCopySourceRow;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** 为复制批次准备、单条复制和失败留痕提供明确事务边界。 */
@Service
public class PreviousDayTaskCopyTransactionService {
    private static final String TASK_FEATURE = "LEARNING_TASK_MANAGEMENT";
    private static final String COPY_FEATURE = "COPY_PREVIOUS_DAY_TASK";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime DUE_TIME = LocalTime.of(23, 59, 59);

    private final FeatureAccessService featureAccessService;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentMapper studentMapper;
    private final PreviousDayTaskCopyMapper copyMapper;
    private final LearningTaskMapper taskMapper;
    private final LearningTaskTagMapper tagMapper;
    private final LearningTaskTargetMapper targetMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PreviousDayTaskCopyTransactionService(
            FeatureAccessService featureAccessService,
            ParentStudentMapper parentStudentMapper,
            StudentMapper studentMapper,
            PreviousDayTaskCopyMapper copyMapper,
            LearningTaskMapper taskMapper,
            LearningTaskTagMapper tagMapper,
            LearningTaskTargetMapper targetMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.featureAccessService = featureAccessService;
        this.parentStudentMapper = parentStudentMapper;
        this.studentMapper = studentMapper;
        this.copyMapper = copyMapper;
        this.taskMapper = taskMapper;
        this.tagMapper = tagMapper;
        this.targetMapper = targetMapper;
        this.assignmentMapper = assignmentMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public PreparedTaskCopyBatch prepare(
            Long parentUserId,
            Long studentId,
            LocalDate sourceDate,
            LocalDate targetDate,
            boolean confirmDuplicateTitles
    ) {
        requireFeatures();
        Student student = studentMapper.findByIdForUpdate(studentId);
        if (student == null || student.status() != StudentStatus.ENABLED
                || !parentStudentMapper.existsActivePrimaryByParentAndStudent(parentUserId, studentId)) {
            throw new ResourceNotFoundException("学生不存在或不可管理");
        }
        TaskCopyBatchRow existing = copyMapper.findBatchByStudentAndTargetDate(studentId, targetDate);
        if (existing != null) {
            List<Long> pendingItemIds = copyMapper.findItemsByBatchId(existing.id()).stream()
                    .filter(item -> "PENDING".equals(item.status()))
                    .map(TaskCopyItemRow::id)
                    .toList();
            return new PreparedTaskCopyBatch(existing.id(), true, pendingItemIds);
        }
        List<TaskCopySourceRow> sources = copyMapper.findSourceTasks(parentUserId, studentId, sourceDate);
        if (sources.isEmpty()) {
            throw new IllegalStateException("昨日暂无可复制任务");
        }
        List<String> duplicates = copyMapper.findDuplicateTitles(
                parentUserId, studentId, sourceDate, targetDate);
        if (!confirmDuplicateTitles && !duplicates.isEmpty()) {
            throw new IllegalStateException("今天已存在同名任务，请确认后继续复制");
        }

        Long batchId = idGenerator.nextId();
        TaskCopyBatchRow batch = new TaskCopyBatchRow(
                batchId, studentId, sourceDate, targetDate, parentUserId,
                confirmDuplicateTitles, "PROCESSING", sources.size(), 0, 0, null, null);
        if (copyMapper.insertBatch(batch) != 1) {
            throw new IllegalStateException("复制批次创建失败");
        }
        List<Long> itemIds = new ArrayList<>();
        for (TaskCopySourceRow source : sources) {
            Long itemId = idGenerator.nextId();
            TaskCopyItemRow item = new TaskCopyItemRow(
                    itemId, batchId, source.taskId(), null, source.title(), "PENDING",
                    null, null, 0, null, null, null);
            if (copyMapper.insertItem(item) != 1) {
                throw new IllegalStateException("复制条目创建失败");
            }
            itemIds.add(itemId);
        }
        return new PreparedTaskCopyBatch(batchId, false, itemIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processItem(
            Long batchId, Long itemId, Long parentUserId, boolean retry
    ) {
        requireFeatures();
        TaskCopyBatchRow batch = copyMapper.findBatchByIdForUpdate(batchId);
        TaskCopyItemRow item = copyMapper.findItemByIdForUpdate(itemId);
        if (batch == null || item == null || !batch.id().equals(item.batchId())
                || !batch.createdByUserId().equals(parentUserId)
                || !parentStudentMapper.existsActivePrimaryByParentAndStudent(
                parentUserId, batch.studentId())) {
            throw new ResourceNotFoundException("复制批次或条目不存在");
        }
        String expectedStatus = retry ? "FAILED" : "PENDING";
        if (!expectedStatus.equals(item.status())) {
            throw new IllegalStateException("复制条目当前状态不允许处理");
        }

        Long targetTaskId = idGenerator.nextId();
        if (taskMapper.insertPreviousDayCopy(
                targetTaskId, item.sourceTaskId(), batch.targetDate(), parentUserId) != 1) {
            throw new IllegalStateException("任务配置复制失败");
        }
        for (String tagCode : tagMapper.findCodesByTaskId(item.sourceTaskId())) {
            if (tagMapper.insert(new LearningTaskTag(
                    idGenerator.nextId(), targetTaskId, tagCode, null)) != 1) {
                throw new IllegalStateException("任务标签复制失败");
            }
        }
        if (targetMapper.insert(new LearningTaskTarget(
                idGenerator.nextId(), targetTaskId, LearningTaskTargetType.STUDENT,
                batch.studentId(), null)) != 1) {
            throw new IllegalStateException("任务目标复制失败");
        }
        LearningTaskAssignment assignment = new LearningTaskAssignment(
                idGenerator.nextId(), targetTaskId, batch.studentId(),
                LearningTaskSourceType.FAMILY, null, TaskAssignmentStatus.PENDING_CLAIM.name(),
                parentUserId, batch.targetDate(), batch.targetDate().atTime(DUE_TIME));
        if (assignmentMapper.insertBatch(List.of(assignment)) != 1) {
            throw new IllegalStateException("学生任务复制失败");
        }
        if (copyMapper.markItemSuccess(
                itemId, targetTaskId, expectedStatus, retry) != 1) {
            throw new IllegalStateException("复制条目状态已变化");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markItemFailed(
            Long batchId,
            Long itemId,
            String failureCode,
            String failureMessage,
            boolean retry
    ) {
        TaskCopyItemRow item = copyMapper.findItemByIdForUpdate(itemId);
        if (item == null || !batchId.equals(item.batchId())) {
            return;
        }
        String expectedStatus = retry ? "FAILED" : "PENDING";
        copyMapper.markItemFailed(
                itemId, failureCode, failureMessage, expectedStatus, retry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshBatch(Long batchId) {
        TaskCopyBatchRow batch = copyMapper.findBatchByIdForUpdate(batchId);
        if (batch == null) {
            return;
        }
        TaskCopyCountRow counts = copyMapper.countItems(batchId);
        int processed = counts.successCount() + counts.failureCount();
        String status;
        if (processed < counts.totalCount()) {
            status = "PROCESSING";
        } else if (counts.failureCount() == 0) {
            status = "COMPLETED";
        } else if (counts.successCount() == 0) {
            status = "FAILED";
        } else {
            status = "PARTIAL_FAILED";
        }
        copyMapper.updateBatchSummary(
                batchId, status, counts.successCount(), counts.failureCount(),
                processed == counts.totalCount());
    }

    private void requireFeatures() {
        featureAccessService.requireEnabled(TASK_FEATURE, null);
        featureAccessService.requireEnabled(COPY_FEATURE, null);
    }
}
