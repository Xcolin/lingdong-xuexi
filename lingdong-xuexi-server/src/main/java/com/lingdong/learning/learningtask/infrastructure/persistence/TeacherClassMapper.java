package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import com.lingdong.learning.learningtask.domain.TeacherClassChangeEvent;
import com.lingdong.learning.teacher.application.TeacherClassSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 教师班级关系持久化边界。 */
@Mapper
public interface TeacherClassMapper {
    TeacherClassRelation findByTeacherAndClass(
            @Param("teacherUserId") Long teacherUserId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    List<TeacherClassRelation> findActiveByTeacher(@Param("teacherUserId") Long teacherUserId);

    boolean existsActiveOperationalClassByTeacher(@Param("teacherUserId") Long teacherUserId);

    List<TeacherClassSummary> findActiveClassSummaries(@Param("teacherUserId") Long teacherUserId);

    boolean existsTeacherOrganizationInClassAncestors(
            @Param("teacherUserId") Long teacherUserId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    int insert(@Param("relation") TeacherClassRelation relation);

    int activate(@Param("teacherUserId") Long teacherUserId,
                 @Param("classOrganizationId") Long classOrganizationId);

    int deactivate(@Param("teacherUserId") Long teacherUserId,
                   @Param("classOrganizationId") Long classOrganizationId);

    int insertChangeLog(
            @Param("id") Long id,
            @Param("teacherUserId") Long teacherUserId,
            @Param("classOrganizationId") Long classOrganizationId,
            @Param("eventType") TeacherClassChangeEvent eventType,
            @Param("operatorUserId") Long operatorUserId
    );
}
