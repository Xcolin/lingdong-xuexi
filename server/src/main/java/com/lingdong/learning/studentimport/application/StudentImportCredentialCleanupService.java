package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 使超期凭证不可访问并删除统一附件中的物理密文。 */
@Service
public class StudentImportCredentialCleanupService {
    private final StudentImportExecutionMapper executionMapper;
    private final AttachmentFileApplicationService fileService;
    private final ManagedAttachmentContentService contentService;
    private final int batchSize;

    public StudentImportCredentialCleanupService(
            StudentImportExecutionMapper executionMapper,
            AttachmentFileApplicationService fileService,
            ManagedAttachmentContentService contentService,
            @Value("${lingdong.student-import.batch-size:10}") int batchSize
    ) {
        this.executionMapper = executionMapper;
        this.fileService = fileService;
        this.contentService = contentService;
        this.batchSize = batchSize;
    }

    public int cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        int cleaned = 0;
        for (StudentImportExecutionRecord execution
                : executionMapper.findExpiredCredentials(now, batchSize)) {
            ManagedFile file = fileService.findFile(execution.credentialFileId());
            // 先删除幂等的物理密文；删除失败时保留状态，供下一轮清理继续重试。
            contentService.discardContent(file.storageKey());
            if (executionMapper.expireCredential(
                    execution.id(), execution.versionNo(), now) != 1) {
                continue;
            }
            cleaned++;
        }
        return cleaned;
    }
}
