package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 任务原始目标持久化边界。 */
@Mapper
public interface LearningTaskTargetMapper {
    List<LearningTaskTarget> findByTaskId(@Param("taskId") Long taskId);

    @org.apache.ibatis.annotations.Select("SELECT EXISTS(SELECT 1 FROM learn_task_target t JOIN edu_student s ON t.target_type='STUDENT' AND t.target_id=s.id JOIN sys_user_organization m ON m.user_id=s.student_user_id WHERE t.task_id=#{taskId} AND m.organization_id=#{organizationId})")
    boolean existsStudentInOrganization(@Param("taskId") Long taskId, @Param("organizationId") Long organizationId);

    int insert(@Param("target") LearningTaskTarget target);

    int deleteByTaskId(@Param("taskId") Long taskId);
}

