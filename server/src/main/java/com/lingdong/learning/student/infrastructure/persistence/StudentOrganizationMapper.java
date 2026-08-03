package com.lingdong.learning.student.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 学生和学校组织的直接关系持久化操作。 */
@Mapper
public interface StudentOrganizationMapper {
    boolean existsActiveByOrganizationAdministratorAndStudent(
            @Param("userId") Long userId,
            @Param("studentId") Long studentId
    );

    boolean existsActiveByStudentAndOrganization(
            @Param("studentId") Long studentId,
            @Param("organizationId") Long organizationId
    );

    int insertEnrollment(
            @Param("id") Long id,
            @Param("studentId") Long studentId,
            @Param("organizationId") Long organizationId
    );

    boolean existsActiveEnrollmentInClassAncestors(
            @Param("studentId") Long studentId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    boolean existsActiveStudentInOrganizationSubtree(
            @Param("studentId") Long studentId,
            @Param("organizationId") Long organizationId
    );

    boolean existsActiveClass(
            @Param("studentId") Long studentId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    boolean existsActiveStudentInTeacherClasses(
            @Param("studentId") Long studentId,
            @Param("teacherUserId") Long teacherUserId
    );

    List<Long> findEnabledStudentIdsByOrganizationTarget(@Param("organizationId") Long organizationId);

    List<Long> findActiveClassOrganizationIds(@Param("studentId") Long studentId);

    int deactivateActiveClasses(@Param("studentId") Long studentId);

    int activateExistingClass(
            @Param("studentId") Long studentId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    int insertClass(
            @Param("id") Long id,
            @Param("studentId") Long studentId,
            @Param("classOrganizationId") Long classOrganizationId
    );
}
