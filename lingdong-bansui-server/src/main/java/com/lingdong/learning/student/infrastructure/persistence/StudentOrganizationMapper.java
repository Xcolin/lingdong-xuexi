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

    List<StudentOrganizationSummaryRow> findActiveSummariesByOrganizationAdministrator(
            @Param("userId") Long userId
    );

    List<StudentOrganizationClassOptionRow> findEnabledClassOptionsByOrganizationAdministrator(
            @Param("userId") Long userId
    );

    List<Long> findActiveClassOrganizationIds(@Param("studentId") Long studentId);

    List<Long> findActiveEnrollmentOrganizationIds(@Param("studentId") Long studentId);

    List<Long> findActiveOrganizationIdsForUpdate(@Param("studentId") Long studentId);

    List<Long> findActiveEnrollmentOrganizationIdsForClass(
            @Param("studentId") Long studentId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    List<Long> findActiveClassOrganizationIdsInEnrollment(
            @Param("studentId") Long studentId,
            @Param("enrollmentOrganizationId") Long enrollmentOrganizationId
    );

    int deactivateActiveClasses(@Param("studentId") Long studentId);

    int deactivateActiveClassesInEnrollment(
            @Param("studentId") Long studentId,
            @Param("enrollmentOrganizationId") Long enrollmentOrganizationId
    );

    int deactivateActiveEnrollment(
            @Param("studentId") Long studentId,
            @Param("enrollmentOrganizationId") Long enrollmentOrganizationId
    );

    int activateExistingClass(
            @Param("studentId") Long studentId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    int insertClass(
            @Param("id") Long id,
            @Param("studentId") Long studentId,
            @Param("classOrganizationId") Long classOrganizationId
    );
    List<StudentOrganizationSummaryRow> findActiveSummariesByOrganizationAdministratorScope(
            @Param("scope") com.lingdong.learning.datascope.application.OrganizationDataScope scope);
    List<StudentOrganizationClassOptionRow> findEnabledClassOptionsByOrganizationAdministratorScope(
            @Param("scope") com.lingdong.learning.datascope.application.OrganizationDataScope scope);
}

