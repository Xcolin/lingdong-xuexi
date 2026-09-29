package com.lingdong.learning.exportjob.infrastructure.persistence;
import java.math.BigDecimal;
/** 机构任务统计聚合行，仅含组织名称与聚合计数，不含学生个人字段。 */
public record OrgTaskStatExportRow(Long orgId, Long repId, String schoolName, String className, Long taskCount, Long completedCount, BigDecimal completionRate, BigDecimal avgPoints) {}
