package com.lingdong.learning.organization.onboarding.application;
import org.apache.ibatis.annotations.*;import java.util.List;
@Mapper public interface OrganizationOwnerMapper {
 @Select("SELECT user_id FROM sys_organization_owner WHERE organization_id=#{id}") Long findOwner(Long id);
 @Select("SELECT user_id FROM sys_organization_owner WHERE organization_id=#{id} FOR UPDATE") Long lockOwner(Long id);
 @Select("SELECT id FROM sys_organization WHERE id=#{id} FOR UPDATE") Long lockOrganization(Long id);
 @Select("SELECT EXISTS(SELECT 1 FROM sys_organization_admin WHERE user_id=#{user} AND organization_id=#{org})") boolean isAdministrator(@Param("user")Long user,@Param("org")Long org);
 @Select("SELECT COUNT(DISTINCT a.user_id) FROM sys_organization_admin a JOIN sys_user u ON u.id=a.user_id AND u.status='ENABLED' JOIN sys_user_role ur ON ur.user_id=a.user_id AND ur.organization_id=a.organization_id JOIN sys_role r ON r.id=ur.role_id AND r.status='ENABLED' AND r.role_code IN ('ORG_ADMIN','PARENT_ADMIN') WHERE a.organization_id=#{id}") int activeAdministrators(Long id);
 @Select("SELECT organization_id FROM sys_organization_admin WHERE user_id=#{id} ORDER BY organization_id") List<Long> organizations(Long id);
 @Select("SELECT u.id,u.display_name AS name FROM sys_organization_admin a JOIN sys_user u ON u.id=a.user_id AND u.status='ENABLED' JOIN sys_user_role ur ON ur.user_id=a.user_id AND ur.organization_id=a.organization_id JOIN sys_role r ON r.id=ur.role_id AND r.status='ENABLED' AND r.role_code IN ('ORG_ADMIN','PARENT_ADMIN') WHERE a.organization_id=#{id} ORDER BY u.display_name,u.id") List<java.util.Map<String,Object>> administratorOptions(Long id);
 @Update("UPDATE sys_organization_owner SET user_id=#{target},version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=#{org} AND user_id=#{actor}") int transfer(@Param("org")Long org,@Param("actor")Long actor,@Param("target")Long target);
}

