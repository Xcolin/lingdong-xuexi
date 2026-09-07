package com.lingdong.learning.exceptionreport.infrastructure.persistence;

import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportType;

import java.util.List;

/** 已解析用户范围的异常报备数据库查询。 */
public record ExceptionReportQuery(
        Long userId, boolean teacher, boolean allOrganizations, List<String> rootPaths,
        Long classOrganizationId, Long studentId, ExceptionReportType exceptionType,
        ExceptionReportStatus status, int limit, int offset
) {
}
