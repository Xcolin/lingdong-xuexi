package com.lingdong.learning.interfaceconfig.infrastructure.persistence;

import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence boundary for the minimal interface-call audit trail. */
@Mapper
public interface InterfaceServiceCallLogMapper {
    int insert(@Param("callLog") InterfaceServiceCallLog callLog);

    InterfaceServiceCallLog findById(@Param("id") Long id);
}
