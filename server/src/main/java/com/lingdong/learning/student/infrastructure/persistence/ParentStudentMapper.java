package com.lingdong.learning.student.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 家长与学生关系的最小持久化操作。 */
@Mapper
public interface ParentStudentMapper {
    boolean existsActiveByParentAndStudent(@Param("parentUserId") Long parentUserId, @Param("studentId") Long studentId);

    boolean existsActivePrimaryByParentAndStudent(
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId
    );

    boolean existsActiveByStudentId(@Param("studentId") Long studentId);

    int insertPrimary(
            @Param("id") Long id,
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId
    );
}
