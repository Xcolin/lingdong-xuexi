package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

/** 在发布时把原始组织或学生目标展开为启用学生主键集合。 */
@Service
public class LearningTaskTargetExpansionService {
    private final StudentMapper studentMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;

    public LearningTaskTargetExpansionService(
            StudentMapper studentMapper,
            StudentOrganizationMapper studentOrganizationMapper
    ) {
        this.studentMapper = studentMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
    }

    public List<Long> expand(List<LearningTaskTarget> targets) {
        TreeSet<Long> studentIds = new TreeSet<>();
        for (LearningTaskTarget target : targets) {
            if (target.targetType() == LearningTaskTargetType.STUDENT) {
                Student student = studentMapper.findById(target.targetId());
                if (student != null && student.status() == StudentStatus.ENABLED) {
                    studentIds.add(student.id());
                }
            } else {
                studentIds.addAll(studentOrganizationMapper
                        .findEnabledStudentIdsByOrganizationTarget(target.targetId()));
            }
        }
        return List.copyOf(studentIds);
    }
}
