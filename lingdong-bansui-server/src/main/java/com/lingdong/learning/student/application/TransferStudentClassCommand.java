package com.lingdong.learning.student.application;

/** 学生首次分班或校内转班命令。 */
public record TransferStudentClassCommand(Long classOrganizationId, String reason, boolean requireUnassigned) {
    public TransferStudentClassCommand(Long classOrganizationId,String reason){this(classOrganizationId,reason,false);}
}
