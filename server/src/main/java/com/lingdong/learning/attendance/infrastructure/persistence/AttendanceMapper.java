package com.lingdong.learning.attendance.infrastructure.persistence;

import com.lingdong.learning.attendance.domain.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

/** 考勤持久化边界，读取范围由 XML 统一执行。 */
@Mapper
public interface AttendanceMapper {
    List<AttendanceRow> findPage(@Param("query") AttendanceQuery query);
    long count(@Param("query") AttendanceQuery query);
    AttendanceRow findVisible(@Param("scope") AttendanceScope scope, @Param("id") Long id);
    List<AttendanceClassRow> findClasses(@Param("scope") AttendanceScope scope, @Param("operational") boolean operational);
    boolean canAccessClass(@Param("scope") AttendanceScope scope, @Param("classId") Long classId);
    List<AttendanceStudentRow> findRoster(@Param("classId") Long classId, @Param("date") LocalDate date);
    Long lockEligibleStudent(@Param("classId") Long classId, @Param("studentId") Long studentId, @Param("date") LocalDate date);
    AttendanceRecord findExisting(@Param("classId") Long classId, @Param("studentId") Long studentId, @Param("date") LocalDate date);
    int insert(@Param("record") AttendanceRecord record);
    int correct(@Param("record") AttendanceRecord record, @Param("expectedVersion") long expectedVersion);
    int insertAction(@Param("id") Long id, @Param("before") AttendanceRecord before, @Param("after") AttendanceRecord after);
    List<AttendanceActionRow> findActions(@Param("id") Long id);
}
