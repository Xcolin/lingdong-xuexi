package com.lingdong.learning.interfaceconfig.infrastructure.persistence;

import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 不可变接口服务变更提案的持久化边界。 */
@Mapper
public interface InterfaceServiceChangeMapper {
    int insert(@Param("change") InterfaceServiceChange change);

    InterfaceServiceChange findByTaskId(@Param("taskId") Long taskId);

    List<com.lingdong.learning.interfaceconfig.application.InterfaceServiceChangeView> findRecent(
            @Param("pendingOnly") boolean pendingOnly,
            @Param("limit") int limit
    );
}
