package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.domain.StudentOrganizationChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 学生机构关系变更只追加、只查询的持久化入口。 */
@Mapper
public interface StudentOrganizationChangeMapper {
    int insert(
            @Param("id") Long id,
            @Param("studentId") Long studentId,
            @Param("changeType") String changeType,
            @Param("fromOrganizationId") Long fromOrganizationId,
            @Param("toOrganizationId") Long toOrganizationId,
            @Param("reason") String reason,
            @Param("operatorUserId") Long operatorUserId,
            @Param("occurredAt") LocalDateTime occurredAt
    );

    List<StudentOrganizationChange> findByStudentId(@Param("studentId") Long studentId);
}
