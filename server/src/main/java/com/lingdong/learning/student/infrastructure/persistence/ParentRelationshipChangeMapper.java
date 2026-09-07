package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.domain.ParentRelationshipChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 家长关系不可变审计持久化操作。 */
@Mapper
public interface ParentRelationshipChangeMapper {
    int insert(@Param("change") ParentRelationshipChange change);

    List<ParentRelationshipChange> findByStudentId(@Param("studentId") Long studentId);
}
