package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.application.StudentDirectoryQuery;
import com.lingdong.learning.student.domain.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 学生档案的持久化边界，不承担任何角色或组织授权判断。 */
@Mapper
public interface StudentMapper {
    Student findById(@Param("id") Long id);

    Student findByIdForUpdate(@Param("id") Long id);

    Student findByStudentUserId(@Param("studentUserId") Long studentUserId);

    List<Student> findPage(@Param("query") StudentDirectoryQuery query);

    long count(@Param("query") StudentDirectoryQuery query);

    int insert(@Param("student") Student student);

    int bindStudentUserIfAbsent(@Param("studentId") Long studentId, @Param("studentUserId") Long studentUserId);
}
