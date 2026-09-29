package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 班级停用时查询并条件更新未完成机构任务。 */
@Mapper
public interface ClassTaskInvalidationMapper {
    List<ClassTaskAssignmentStateRow> findUnfinishedByClassForUpdate(
            @Param("classOrganizationId") Long classOrganizationId
    );

    int invalidate(
            @Param("assignmentId") Long assignmentId,
            @Param("expectedStatus") TaskAssignmentStatus expectedStatus,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("occurredAt") LocalDateTime occurredAt
    );
}
