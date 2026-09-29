package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.attachment.application.AttachFileToBusinessCommand;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 生成加密附件，并以所有权、有效期和版本号保证凭证只交付一次。 */
@Service
public class StudentImportCredentialService {
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StudentImportExecutionMapper executionMapper;
    private final StudentImportRowMapper rowMapper;
    private final ManagedAttachmentContentService contentService;
    private final AttachmentFileApplicationService fileService;
    private final StudentImportAccessService accessService;
    private final StudentCredentialCipher credentialCipher;
    private final StudentCredentialWorkbookWriter workbookWriter;
    private final Duration credentialTtl;

    public StudentImportCredentialService(
            StudentImportExecutionMapper executionMapper,
            StudentImportRowMapper rowMapper,
            ManagedAttachmentContentService contentService,
            AttachmentFileApplicationService fileService,
            StudentImportAccessService accessService,
            StudentCredentialCipher credentialCipher,
            StudentCredentialWorkbookWriter workbookWriter,
            @Value("${lingdong.student-import.credential-ttl:PT24H}") Duration credentialTtl
    ) {
        this.executionMapper = executionMapper;
        this.rowMapper = rowMapper;
        this.contentService = contentService;
        this.fileService = fileService;
        this.accessService = accessService;
        this.credentialCipher = credentialCipher;
        this.workbookWriter = workbookWriter;
        this.credentialTtl = credentialTtl;
    }

    @Transactional
    public void finalizeExecution(StudentImportExecutionRecord execution) {
        if (execution == null || execution.status() != StudentImportExecutionStatus.RUNNING) {
            throw new IllegalStateException("只有执行中的学员导入可以生成终态凭证");
        }
        int succeeded = Math.toIntExact(rowMapper.countByExecutionIdAndStatus(
                execution.id(), StudentImportRowStatus.SUCCEEDED));
        int failed = Math.toIntExact(rowMapper.countByExecutionIdAndStatus(
                execution.id(), StudentImportRowStatus.FAILED));
        int processed = Math.addExact(succeeded, failed);
        if (processed != execution.totalRows()) {
            throw new IllegalStateException("学员导入仍有未处理行，不能生成终态凭证");
        }

        ManagedFile credentialFile = null;
        LocalDateTime now = LocalDateTime.now();
        try {
            if (succeeded > 0) {
                List<StudentImportRowRecord> rows = rowMapper.findByExecutionIdAndStatus(
                        execution.id(), StudentImportRowStatus.SUCCEEDED, 0, succeeded);
                List<StudentCredentialLine> lines = toCredentialLines(execution.id(), rows);
                if (!lines.isEmpty()) {
                    byte[] workbook = workbookWriter.write(lines);
                    EncryptedStudentCredential encryptedFile = credentialCipher.encryptBytes(
                            workbook, fileContext(execution.id()));
                    credentialFile = contentService.store(
                            execution.requesterId(), "STUDENT_IMPORT", "INITIAL_CREDENTIAL",
                            "student-credentials-" + execution.executionCode() + ".enc",
                            "application/octet-stream", StudentCredentialEnvelope.pack(encryptedFile));
                    fileService.attachToBusiness(new AttachFileToBusinessCommand(
                            credentialFile.id(), "STUDENT_IMPORT", execution.id(),
                            "STUDENT_IMPORT_CREDENTIAL", "BUSINESS_AUTHORIZED"));
                }
            }
            StudentImportExecutionStatus status = status(succeeded, failed);
            LocalDateTime expiresAt = credentialFile == null ? null : now.plus(credentialTtl);
            if (executionMapper.complete(
                    execution.id(), execution.versionNo(), status, processed, succeeded, failed,
                    credentialFile == null ? null : credentialFile.id(), expiresAt, now) != 1) {
                throw new IllegalStateException("学员导入终态更新冲突");
            }
            if (succeeded > 0) {
                rowMapper.clearCredentials(execution.id());
            }
        } catch (RuntimeException exception) {
            if (credentialFile != null) {
                discardPreservingFailure(credentialFile.storageKey(), exception);
            }
            throw exception;
        }
    }

    @Transactional
    public AttachmentContentView download(long operatorId, long executionId) {
        StudentImportExecutionRecord execution = executionMapper.findById(executionId);
        if (execution == null) {
            throw new ResourceNotFoundException("学员导入执行不存在");
        }
        accessService.requireCredentialDownload(operatorId, execution);
        LocalDateTime now = LocalDateTime.now();
        if (execution.credentialStatus() != StudentImportCredentialStatus.AVAILABLE
                || execution.credentialFileId() == null
                || execution.credentialExpiresAt() == null
                || !execution.credentialExpiresAt().isAfter(now)) {
            throw new IllegalStateException("学员初始凭证只能下载一次且必须在有效期内完成");
        }
        AttachmentContentView encryptedContent = contentService.read(execution.credentialFileId());
        byte[] workbook = credentialCipher.decryptBytes(
                StudentCredentialEnvelope.unpack(encryptedContent.content()),
                fileContext(execution.id()));
        if (executionMapper.consumeCredential(
                execution.id(), execution.versionNo(), now, now) != 1) {
            throw new IllegalStateException("学员初始凭证只能下载一次且必须在有效期内完成");
        }
        ManagedFile file = fileService.findFile(execution.credentialFileId());
        contentService.discardContent(file.storageKey());
        return new AttachmentContentView(
                "student-credentials-" + execution.executionCode() + ".xlsx",
                XLSX_CONTENT_TYPE, workbook);
    }

    private List<StudentCredentialLine> toCredentialLines(
            long executionId,
            List<StudentImportRowRecord> rows
    ) {
        List<StudentCredentialLine> lines = new ArrayList<>(rows.size());
        for (StudentImportRowRecord row : rows) {
            if (row.credentialCiphertext() == null || row.credentialNonce() == null
                    || row.credentialKeyVersion() == null) {
                continue;
            }
            String code = credentialCipher.decrypt(new EncryptedStudentCredential(
                    row.credentialCiphertext(), row.credentialNonce(), row.credentialKeyVersion()),
                    StudentImportRowProcessor.context(executionId, row.rowNumber()));
            lines.add(new StudentCredentialLine(row.rowNumber(), row.studentAccount(), code));
        }
        return List.copyOf(lines);
    }

    private StudentImportExecutionStatus status(int succeeded, int failed) {
        if (succeeded > 0 && failed == 0) {
            return StudentImportExecutionStatus.SUCCEEDED;
        }
        if (succeeded > 0) {
            return StudentImportExecutionStatus.PARTIAL_SUCCEEDED;
        }
        return StudentImportExecutionStatus.FAILED;
    }

    private String fileContext(long executionId) {
        return "execution:" + executionId + ":file";
    }

    private void discardPreservingFailure(String storageKey, RuntimeException originalFailure) {
        try {
            contentService.discardContent(storageKey);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
