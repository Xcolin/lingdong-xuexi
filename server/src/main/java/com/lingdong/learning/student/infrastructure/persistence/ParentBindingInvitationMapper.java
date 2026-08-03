package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.domain.ParentBindingInvitation;
import com.lingdong.learning.student.domain.ParentBindingInvitationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 机构家长绑定邀请的持久化操作，原始令牌不会进入该边界。 */
@Mapper
public interface ParentBindingInvitationMapper {
    ParentBindingInvitation findById(@Param("id") Long id);

    boolean existsPendingByStudentId(@Param("studentId") Long studentId);

    int expirePendingByStudentId(@Param("studentId") Long studentId, @Param("occurredAt") LocalDateTime occurredAt);

    int insert(@Param("invitation") ParentBindingInvitation invitation);

    int respondIfPending(
            @Param("id") Long id,
            @Param("status") ParentBindingInvitationStatus status,
            @Param("pendingScopeKey") String pendingScopeKey,
            @Param("respondedByUserId") Long respondedByUserId,
            @Param("occurredAt") LocalDateTime occurredAt
    );
}
