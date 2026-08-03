package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTagMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTagRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.StudentTaskAssignmentRow;
import com.lingdong.learning.student.domain.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仅按认证学生档案读取本人发布任务，不接受客户端传入学生主键。 */
@Service
public class StudentTaskAssignmentService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";

    private final LearningTaskAssignmentMapper assignmentMapper;
    private final LearningTaskTagMapper tagMapper;
    private final CurrentStudentAccessService currentStudentAccessService;
    private final FeatureAccessService featureAccessService;

    public StudentTaskAssignmentService(
            LearningTaskAssignmentMapper assignmentMapper,
            LearningTaskTagMapper tagMapper,
            CurrentStudentAccessService currentStudentAccessService,
            FeatureAccessService featureAccessService
    ) {
        this.assignmentMapper = assignmentMapper;
        this.tagMapper = tagMapper;
        this.currentStudentAccessService = currentStudentAccessService;
        this.featureAccessService = featureAccessService;
    }

    @Transactional(readOnly = true)
    public StudentTaskAssignmentPage findPage(
            AuthenticatedUser currentUser,
            LearningTaskSourceType sourceType,
            LocalDate scheduledDate,
            int page,
            int pageSize
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = currentStudentAccessService.require(currentUser);
        int validatedPage = requireRange(page, "页码", 1, 1_000_000);
        int validatedPageSize = requireRange(pageSize, "每页数量", 1, 100);
        StudentTaskAssignmentQuery query = new StudentTaskAssignmentQuery(
                student.id(), sourceType, scheduledDate,
                (validatedPage - 1) * validatedPageSize, validatedPageSize);
        List<StudentTaskAssignmentRow> rows = assignmentMapper.findPage(query);
        Map<Long, List<String>> tagsByTask = tagsByTaskIds(
                rows.stream().map(StudentTaskAssignmentRow::taskId).distinct().toList());
        List<StudentTaskAssignmentView> items = rows.stream()
                .map(row -> row.toView(tagsByTask.getOrDefault(row.taskId(), List.of())))
                .toList();
        return new StudentTaskAssignmentPage(
                items, validatedPage, validatedPageSize, assignmentMapper.count(query));
    }

    @Transactional(readOnly = true)
    public StudentTaskAssignmentView findById(
            AuthenticatedUser currentUser, Long assignmentId
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = currentStudentAccessService.require(currentUser);
        StudentTaskAssignmentRow row = assignmentMapper.findByIdAndStudentId(
                assignmentId, student.id());
        if (row == null) {
            throw new ResourceNotFoundException("学生任务不存在或不可访问");
        }
        return row.toView(tagMapper.findCodesByTaskId(row.taskId()));
    }

    private Map<Long, List<String>> tagsByTaskIds(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> mutable = new LinkedHashMap<>();
        for (LearningTaskTagRow row : tagMapper.findByTaskIds(taskIds)) {
            mutable.computeIfAbsent(row.taskId(), ignored -> new ArrayList<>()).add(row.tagCode());
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        mutable.forEach((taskId, codes) -> result.put(taskId, List.copyOf(codes)));
        return Map.copyOf(result);
    }

    private int requireRange(int value, String fieldName, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return value;
    }
}
