package com.lingdong.learning.user.infrastructure.persistence;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface UserRoleAssociationMapper {
 record Assignment(Long roleId,Long organizationId){}
 @Select("SELECT role_id,organization_id FROM sys_user_role WHERE user_id=#{userId} ORDER BY role_id,organization_scope_key")
 List<Assignment> findByUserId(@Param("userId") Long userId);
}
