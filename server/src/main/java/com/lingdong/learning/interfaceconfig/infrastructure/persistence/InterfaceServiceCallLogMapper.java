package com.lingdong.learning.interfaceconfig.infrastructure.persistence;

import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 最小化接口调用审计台账的持久化边界。 */
@Mapper
public interface InterfaceServiceCallLogMapper {
    int insert(@Param("callLog") InterfaceServiceCallLog callLog);

    InterfaceServiceCallLog findById(@Param("id") Long id);

    List<com.lingdong.learning.interfaceconfig.application.InterfaceServiceCallLogView> findRecent(
            @Param("serviceId") Long serviceId,
            @Param("result") com.lingdong.learning.interfaceconfig.domain.InterfaceCallResult result,
            @Param("limit") int limit
    );
}
