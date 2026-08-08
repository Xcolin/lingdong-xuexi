package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.domain.StudentQrTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 学生扫码登录票据的持久化边界。 */
@Mapper
public interface StudentQrTicketMapper {
    int revokeActiveByStudentId(@Param("studentId") Long studentId);

    int insert(@Param("ticket") StudentQrTicket ticket);

    StudentQrTicket findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    StudentQrTicket findByTokenHash(@Param("tokenHash") String tokenHash);

    int markConsumed(@Param("id") Long id, @Param("consumedAt") LocalDateTime consumedAt);
}
