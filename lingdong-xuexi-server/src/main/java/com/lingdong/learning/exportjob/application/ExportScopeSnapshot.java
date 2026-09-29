package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.audit.application.SystemTaskType;
import java.util.List;

/** 固化业务对象、主键上界及系统任务角色与领域集合；旧数据集无需任务范围。 */
public record ExportScopeSnapshot(Long studentId, long upperBound,
        Boolean systemTaskAuditor, List<SystemTaskType> systemTaskTypes, Boolean exceptionTeacherOnly, List<Long> exceptionClassIds, String studentTaskRole, List<Long> studentTaskAssignmentIds, String orgStatRole, List<Long> orgStatOrgIds, String attRole, List<Long> attClassIds) {
    public ExportScopeSnapshot(Long studentId, long upperBound, Boolean systemTaskAuditor, List<SystemTaskType> systemTaskTypes, Boolean exceptionTeacherOnly, List<Long> exceptionClassIds, String studentTaskRole, List<Long> studentTaskAssignmentIds, String orgStatRole, List<Long> orgStatOrgIds) { this(studentId, upperBound, systemTaskAuditor, systemTaskTypes, exceptionTeacherOnly, exceptionClassIds, studentTaskRole, studentTaskAssignmentIds, orgStatRole, orgStatOrgIds, null, null); }
    public ExportScopeSnapshot(Long studentId, long upperBound, Boolean systemTaskAuditor, List<SystemTaskType> systemTaskTypes, Boolean exceptionTeacherOnly, List<Long> exceptionClassIds) { this(studentId, upperBound, systemTaskAuditor, systemTaskTypes, exceptionTeacherOnly, exceptionClassIds, null, null, null, null); }
    public ExportScopeSnapshot(Long studentId, long upperBound, Boolean systemTaskAuditor, List<SystemTaskType> systemTaskTypes) { this(studentId, upperBound, systemTaskAuditor, systemTaskTypes, null, null); }
    public ExportScopeSnapshot(Long studentId, long upperBound) { this(studentId, upperBound, null, null); }
    public ExportScopeSnapshot { if (studentTaskAssignmentIds != null) studentTaskAssignmentIds = List.copyOf(studentTaskAssignmentIds); if (exceptionClassIds != null) exceptionClassIds = List.copyOf(exceptionClassIds); if (systemTaskTypes != null) systemTaskTypes = List.copyOf(systemTaskTypes); if (orgStatOrgIds != null) orgStatOrgIds = List.copyOf(orgStatOrgIds); if (attClassIds != null) attClassIds = List.copyOf(attClassIds); }
}
