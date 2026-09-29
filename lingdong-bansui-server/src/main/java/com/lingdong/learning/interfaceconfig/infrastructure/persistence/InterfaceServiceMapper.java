package com.lingdong.learning.interfaceconfig.infrastructure.persistence;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 生效接口服务元数据的持久化边界。 */
@Mapper
public interface InterfaceServiceMapper {
    InterfaceService findById(@Param("id") Long id);

    InterfaceService findByNameAndCaller(
            @Param("serviceName") String serviceName,
            @Param("callerName") String callerName
    );

    List<InterfaceService> findAll(
            @Param("serviceName") String serviceName,
            @Param("callerName") String callerName,
            @Param("status") InterfaceServiceStatus status,
            @Param("purpose") com.lingdong.learning.interfaceconfig.domain.InterfacePurpose purpose,
            @Param("ownerId") Long ownerId,
            @Param("limit") int limit
    );

    int insert(@Param("service") InterfaceService service);

    int updateStatus(@Param("id") Long id, @Param("status") InterfaceServiceStatus status);

    int enable(@Param("id") Long id);

    int updateAuthorizationScope(
            @Param("id") Long id,
            @Param("authorizationScope") InterfaceAuthorizationScope authorizationScope,
            @Param("authorizationScopeValue") String authorizationScopeValue
    );
}
