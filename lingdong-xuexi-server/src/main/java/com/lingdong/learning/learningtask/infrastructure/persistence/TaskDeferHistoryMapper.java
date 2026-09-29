package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskDeferHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 不可变任务顺延历史持久化边界。 */
@Mapper
public interface TaskDeferHistoryMapper {
    int insert(@Param("history") TaskDeferHistory history);
}
