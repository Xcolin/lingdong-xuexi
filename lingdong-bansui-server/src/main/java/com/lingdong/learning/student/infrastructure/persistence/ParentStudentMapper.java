package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.application.ParentRelationshipStudentView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 家长与学生关系的最小持久化操作。 */
@Mapper
public interface ParentStudentMapper {
    boolean existsActiveByParentAndStudent(@Param("parentUserId") Long parentUserId, @Param("studentId") Long studentId);

    boolean existsActivePrimaryByParentAndStudent(
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId
    );

    boolean existsActiveByStudentId(@Param("studentId") Long studentId);

    long countActiveStudentsByParent(@Param("parentUserId") Long parentUserId);

    List<ParentRelationshipStudentView> findActiveStudentsByParent(
            @Param("parentUserId") Long parentUserId
    );

    int insertPrimary(
            @Param("id") Long id,
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId
    );

    int insertSecondary(
            @Param("id") Long id,
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId,
            @Param("boundAt") LocalDateTime boundAt
    );

    int insertPrimaryAt(
            @Param("id") Long id,
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId,
            @Param("boundAt") LocalDateTime boundAt
    );

    List<ParentRelationship> findActiveByStudentIdForUpdate(@Param("studentId") Long studentId);

    ParentRelationship findActivePrimaryByStudentIdForUpdate(@Param("studentId") Long studentId);

    List<ParentRelationship> findActiveByStudentId(@Param("studentId") Long studentId);

    ParentRelationship findEarliestActiveSecondaryForUpdate(@Param("studentId") Long studentId);

    ParentRelationship findByParentAndStudent(
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId
    );

    ParentRelationship findByParentAndStudentForUpdate(
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId
    );

    int unbind(
            @Param("relationshipId") Long relationshipId,
            @Param("closedScopeKey") String closedScopeKey,
            @Param("unboundAt") LocalDateTime unboundAt
    );

    int reactivateAsSecondary(
            @Param("relationshipId") Long relationshipId,
            @Param("boundAt") LocalDateTime boundAt
    );

    int promoteToPrimary(
            @Param("relationshipId") Long relationshipId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int moveToTransitionScope(
            @Param("relationshipId") Long relationshipId,
            @Param("transitionScopeKey") String transitionScopeKey,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int demoteToSecondary(
            @Param("relationshipId") Long relationshipId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int promoteTransitionToPrimary(
            @Param("relationshipId") Long relationshipId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int reactivateAsPrimary(
            @Param("relationshipId") Long relationshipId,
            @Param("boundAt") LocalDateTime boundAt
    );
}
