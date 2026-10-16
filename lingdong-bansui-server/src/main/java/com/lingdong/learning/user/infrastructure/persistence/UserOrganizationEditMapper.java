package com.lingdong.learning.user.infrastructure.persistence;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface UserOrganizationEditMapper {
 @Select("SELECT DISTINCT m.organization_id FROM sys_user_organization m JOIN sys_organization own ON own.id=m.organization_id JOIN sys_organization dependency ON dependency.organization_path LIKE CONCAT(own.organization_path,'%') WHERE m.user_id=#{id} AND dependency.id IN (SELECT organization_id FROM sys_user_role WHERE user_id=#{id} AND organization_id IS NOT NULL UNION SELECT organization_id FROM sys_organization_admin WHERE user_id=#{id} UNION SELECT r.organization_id FROM edu_student_organization r JOIN edu_student s ON s.id=r.student_id WHERE s.student_user_id=#{id} AND r.status='ACTIVE' UNION SELECT class_organization_id FROM edu_teacher_class WHERE teacher_user_id=#{id} AND status='ACTIVE')")
 List<Long> protectedOrganizationIds(Long id);
}
