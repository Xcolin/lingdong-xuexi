package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.student.application.AssignStudentClassCommand;
import com.lingdong.learning.student.application.CreateStudentCommand;
import com.lingdong.learning.student.application.CreatedStudent;
import com.lingdong.learning.student.application.StudentApplicationService;
import com.lingdong.learning.student.application.StudentClassAssignmentService;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 在独立事务内完成单个学员开户、机构关系、可选班级关系和密文结果。 */
@Service
public class StudentImportRowProcessor {
    private final com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles;
    private final StudentImportAccessService access;
    private final StudentApplicationService studentService;
    private final StudentClassAssignmentService classAssignmentService;
    private final StudentCredentialCipher credentialCipher;
    private final StudentImportRowMapper rowMapper;

    public StudentImportRowProcessor(
            StudentApplicationService studentService,
            StudentClassAssignmentService classAssignmentService,
            StudentCredentialCipher credentialCipher,
            StudentImportRowMapper rowMapper,
            com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles, StudentImportAccessService access
    ) {
        this.userRoles = userRoles;
        this.access = access;
        this.studentService = studentService;
        this.classAssignmentService = classAssignmentService;
        this.credentialCipher = credentialCipher;
        this.rowMapper = rowMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(
            StudentImportExecutionRecord execution,
            StudentImportRowRecord row,
            StudentImportWorkbookRow source
    ) {
        validate(execution, row, source);
        access.requireExecute(execution.requesterId(), execution.organizationId(), execution.classOrganizationId());
        AuthenticatedUser operator = new AuthenticatedUser(
                execution.requesterId(), null, "student-import", "学员批量导入",
                AuthClientType.WEB, userRoles.findEnabledRoleCodesByUserId(execution.requesterId()));
        CreatedStudent created = studentService.createStudent(operator, new CreateStudentCommand(
                source.studentName(), source.gradeCode(), execution.organizationId()));
        if (execution.classOrganizationId() != null) {
            classAssignmentService.assign(operator, created.student().id(),
                    new AssignStudentClassCommand(execution.classOrganizationId()));
        }
        EncryptedStudentCredential encrypted = credentialCipher.encrypt(
                created.initialLoginCode(), context(execution.id(), row.rowNumber()));
        int updated = rowMapper.markSucceeded(
                row.id(), created.student().id(), created.studentAccount(),
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion());
        if (updated != 1) {
            throw new IllegalStateException("学员导入行状态已变化，不能重复开户");
        }
    }

    private void validate(
            StudentImportExecutionRecord execution,
            StudentImportRowRecord row,
            StudentImportWorkbookRow source
    ) {
        Objects.requireNonNull(execution, "学员导入执行不能为空");
        Objects.requireNonNull(row, "学员导入行不能为空");
        Objects.requireNonNull(source, "学员导入源行不能为空");
        if (execution.status() != StudentImportExecutionStatus.RUNNING
                || (row.status() != StudentImportRowStatus.PENDING
                    && row.status() != StudentImportRowStatus.FAILED)
                || !Objects.equals(execution.id(), row.executionId())
                || !Objects.equals(row.rowNumber(), source.rowNumber())) {
            throw new IllegalStateException("学员导入执行、结果行或源行状态不一致");
        }
    }

    static String context(long executionId, int rowNumber) {
        return "execution:" + executionId + ":row:" + rowNumber;
    }
}
