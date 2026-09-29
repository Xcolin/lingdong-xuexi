package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.domain.ParentRelationshipInvitation;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationStatus;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 家长关系邀请持久化操作。 */
@Mapper
public interface ParentRelationshipInvitationMapper {
    int insert(@Param("invitation") ParentRelationshipInvitation invitation);

    ParentRelationshipInvitation findByIdForUpdate(@Param("id") Long id);

    int respondIfPending(
            @Param("id") Long id,
            @Param("status") ParentRelationshipInvitationStatus status,
            @Param("closedScopeKey") String closedScopeKey,
            @Param("respondedByUserId") Long respondedByUserId,
            @Param("respondedAt") LocalDateTime respondedAt
    );

    int expirePendingByStudentAndType(
            @Param("studentId") Long studentId,
            @Param("invitationType") ParentRelationshipInvitationType invitationType,
            @Param("now") LocalDateTime now
    );
}
