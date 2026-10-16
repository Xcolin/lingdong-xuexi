package com.lingdong.learning.teacher.infrastructure.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface TeacherProfileMapper {
 @Select("SELECT EXISTS(SELECT 1 FROM edu_teacher_profile WHERE user_id=#{userId} AND status='ENABLED')")
 boolean enabled(@Param("userId")Long userId);
 @Insert("INSERT INTO edu_teacher_profile(user_id,status) SELECT #{userId},'ENABLED' WHERE NOT EXISTS(SELECT 1 FROM edu_teacher_profile WHERE user_id=#{userId})")
 int insert(@Param("userId")Long userId);
}
