package com.lingdong.learning.teacher.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 教师目录和维护操作的持久化边界。 */
@Mapper
public interface TeacherManagementMapper {
    List<TeacherDirectoryRow> findPage(@Param("criteria") TeacherDirectoryCriteria criteria);

    long count(@Param("criteria") TeacherDirectoryCriteria criteria);

    List<TeacherClassIdRow> findActiveClassIds(@Param("teacherUserIds") List<Long> teacherUserIds);

    TeacherDirectoryRow findAccessibleById(
            @Param("teacherUserId") Long teacherUserId,
            @Param("allOrganizations") boolean allOrganizations,
            @Param("rootPaths") List<String> rootPaths
    );

    boolean existsMobileExcludingId(@Param("mobile") String mobile, @Param("teacherUserId") Long teacherUserId);

    int updateProfile(
            @Param("teacherUserId") Long teacherUserId,
            @Param("displayName") String displayName,
            @Param("mobile") String mobile
    );

    boolean hasPendingReviews(@Param("teacherUserId") Long teacherUserId);
}
