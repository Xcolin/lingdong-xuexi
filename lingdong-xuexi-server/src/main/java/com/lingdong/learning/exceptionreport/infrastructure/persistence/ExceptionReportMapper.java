package com.lingdong.learning.exceptionreport.infrastructure.persistence;

import com.lingdong.learning.exceptionreport.domain.ExceptionReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 异常报备、动作历史和本地事件的统一事务持久化边界。 */
@Mapper
public interface ExceptionReportMapper {
    List<com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption> findTeacherClassOptions(
            @Param("teacherUserId") Long teacherUserId);
    ExceptionReport findById(@Param("id") Long id);
    ExceptionReport findByReporterAndIdempotencyKey(
            @Param("reporterUserId") Long reporterUserId,
            @Param("idempotencyKey") String idempotencyKey);
    boolean existsActiveTeacherStudent(@Param("teacherUserId") Long teacherUserId,
                                       @Param("classOrganizationId") Long classOrganizationId,
                                       @Param("studentId") Long studentId);
    List<ExceptionReportStudentOptionRow> findTeacherStudentOptions(
            @Param("teacherUserId") Long teacherUserId,
            @Param("classOrganizationId") Long classOrganizationId);
    int insert(@Param("report") ExceptionReport report);
    int handle(@Param("id") Long id, @Param("handledBy") Long handledBy,
               @Param("handledAt") LocalDateTime handledAt, @Param("versionNo") long versionNo);
    ExceptionReportRow findVisibleById(@Param("query") ExceptionReportQuery query,
                                       @Param("id") Long id);
    List<ExceptionReportRow> findPage(@Param("query") ExceptionReportQuery query);
    long count(@Param("query") ExceptionReportQuery query);
    List<ExceptionReportActionRow> findActions(@Param("reportId") Long reportId);
    int insertAction(@Param("id") Long id, @Param("reportId") Long reportId,
                     @Param("actionType") String actionType, @Param("operatorUserId") Long operatorUserId,
                     @Param("beforeStatus") String beforeStatus, @Param("afterStatus") String afterStatus,
                     @Param("actionNote") String actionNote, @Param("createdAt") LocalDateTime createdAt);
    int insertLocalEvent(@Param("id") Long id, @Param("eventType") String eventType,
                         @Param("businessId") Long businessId, @Param("recipientType") String recipientType,
                         @Param("recipientId") Long recipientId, @Param("contentSummary") String contentSummary,
                         @Param("occurredAt") LocalDateTime occurredAt);
}
