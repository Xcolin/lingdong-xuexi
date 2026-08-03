package com.lingdong.learning.interfaceconfig.infrastructure.persistence;

import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence boundary for immutable interface-service change proposals. */
@Mapper
public interface InterfaceServiceChangeMapper {
    int insert(@Param("change") InterfaceServiceChange change);

    InterfaceServiceChange findByTaskId(@Param("taskId") Long taskId);
}
