package com.lingdong.learning.interfaceconfig.infrastructure.persistence;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence boundary for effective interface-service metadata. */
@Mapper
public interface InterfaceServiceMapper {
    InterfaceService findById(@Param("id") Long id);

    InterfaceService findByNameAndCaller(
            @Param("serviceName") String serviceName,
            @Param("callerName") String callerName
    );

    int insert(@Param("service") InterfaceService service);

    int updateStatus(@Param("id") Long id, @Param("status") InterfaceServiceStatus status);

    int updateAuthorizationScope(
            @Param("id") Long id,
            @Param("authorizationScope") InterfaceAuthorizationScope authorizationScope,
            @Param("authorizationScopeValue") String authorizationScopeValue
    );
}
