package com.lingdong.learning.teacher.application;

import com.lingdong.learning.teacher.infrastructure.persistence.TeacherManagementMapper;
import org.springframework.stereotype.Service;

/** 集中保护教师审核链，V60 完成自动转交前禁止形成无人审核状态。 */
@Service
public class TeacherPendingReviewGuard {
    private final TeacherManagementMapper teacherManagementMapper;

    public TeacherPendingReviewGuard(TeacherManagementMapper teacherManagementMapper) {
        this.teacherManagementMapper = teacherManagementMapper;
    }

    public void requireNoPendingReviews(Long teacherUserId) {
        if (teacherManagementMapper.hasPendingReviews(teacherUserId)) {
            throw new TeacherPendingReviewException();
        }
    }
}
