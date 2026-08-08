package com.lingdong.learning.learningtask.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 管理角色操作任务实例的锁定查询边界。 */
@Mapper
public interface ManagedTaskAssignmentMapper {
    ManagedTaskAssignmentStateRow findStateForUpdate(@Param("assignmentId") Long assignmentId);
}
