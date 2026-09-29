package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 任务实例审计事件持久化边界。 */
@Mapper
public interface TaskAssignmentEventMapper {
    int insert(@Param("event") TaskAssignmentEvent event);
}
