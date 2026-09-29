package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.application.OrganizationOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 按关系和组织路径裁剪任务表单候选项。 */
@Mapper
public interface LearningTaskOptionMapper {
    List<OrganizationOption> findOrganizationOptionsForAdministrator(
            @Param("userId") Long userId,
            @Param("organizationType") String organizationType
    );

    List<OrganizationOption> findOrganizationOptionsForTeacher(
            @Param("userId") Long userId,
            @Param("organizationType") String organizationType
    );

    List<StudentOptionRow> findFamilyStudentOptions(
            @Param("userId") Long userId,
            @Param("keyword") String keyword
    );

    List<StudentOptionRow> findOrganizationStudentOptions(
            @Param("userId") Long userId,
            @Param("organizationId") Long organizationId,
            @Param("keyword") String keyword
    );

    List<StudentOptionRow> findTeacherStudentOptions(
            @Param("userId") Long userId,
            @Param("organizationId") Long organizationId,
            @Param("keyword") String keyword
    );

    List<TeacherOptionRow> findTeacherOptionsForAdministrator(
            @Param("userId") Long userId,
            @Param("classId") Long classId,
            @Param("keyword") String keyword
    );
}
