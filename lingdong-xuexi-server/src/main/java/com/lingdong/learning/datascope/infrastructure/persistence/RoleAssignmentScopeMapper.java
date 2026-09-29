package com.lingdong.learning.datascope.infrastructure.persistence;
import com.lingdong.learning.datascope.application.RoleAssignmentScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper public interface RoleAssignmentScopeMapper { List<RoleAssignmentScope> findByUserId(@Param("userId") Long userId); }
