package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.application.ManagedTaskProgressQuery;
import com.lingdong.learning.learningtask.application.ManagedTaskProgressRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 管理者任务进度 SQL 分页边界。 */
@Mapper
public interface ManagedTaskProgressMapper {
    List<ManagedTaskProgressRow> findPage(@Param("query") ManagedTaskProgressQuery query);

    long count(@Param("query") ManagedTaskProgressQuery query);
}
