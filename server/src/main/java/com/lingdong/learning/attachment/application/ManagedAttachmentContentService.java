package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 统一编排附件元数据登记、内容存储、摘要确认、读取和失败补偿。 */
@Service
public class ManagedAttachmentContentService {
    private final AttachmentFileApplicationService fileService;
    private final AttachmentContentStorage contentStorage;

    public ManagedAttachmentContentService(
            AttachmentFileApplicationService fileService,
            AttachmentContentStorage contentStorage
    ) {
        this.fileService = fileService;
        this.contentStorage = contentStorage;
    }

    /** 按附件规则登记并保存一个文件，任一步骤失败都会删除已写入内容。 */
    @Transactional
    public ManagedFile store(
            Long uploaderId,
            String moduleCode,
            String fileCategory,
            String originalName,
            String contentType,
            byte[] content
    ) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        ManagedFile registered = fileService.registerUpload(new RegisterAttachmentFileCommand(
                uploaderId, moduleCode, fileCategory, originalName, contentType, content.length
        ));
        try {
            contentStorage.store(registered.storageKey(), content);
            return fileService.completeUpload(new CompleteAttachmentUploadCommand(
                    registered.id(), content.length, contentType, sha256(content)
            ));
        } catch (RuntimeException exception) {
            try {
                contentStorage.delete(registered.storageKey());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    /** 读取已完成文件；调用方仍需在进入本方法前完成业务对象鉴权。 */
    public AttachmentContentView read(Long fileId) {
        ManagedFile file = fileService.findFile(fileId);
        if (file.status() != FileStatus.AVAILABLE) {
            throw new IllegalStateException("附件未完成，不能读取：" + fileId);
        }
        return new AttachmentContentView(
                file.originalName(), file.contentType(), contentStorage.read(file.storageKey())
        );
    }

    /** 删除事务回滚或软删除后不再需要的物理内容。 */
    public void discardContent(String storageKey) {
        contentStorage.delete(storageKey);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }
}
