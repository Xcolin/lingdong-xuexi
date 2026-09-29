package com.lingdong.learning.attachment.infrastructure.storage;

import com.lingdong.learning.attachment.application.AttachmentContentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地受控存储实现：根目录为持久化存储（默认工作目录 data/attachments，可由 ATTACHMENT_LOCAL_ROOT 指向持久卷），
 * 重启后内容必须可读；与导出临时目录（lingdong.export-job.temp-directory）明确区分。后续可在同一端口替换为对象存储。
 */
@Component
public class LocalAttachmentContentStorage implements AttachmentContentStorage {
    private final Path root;

    public LocalAttachmentContentStorage(
            @Value("${lingdong.attachment.local-root:${java.io.tmpdir}/lingdong-learning-attachments}") String rootPath
    ) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new IllegalStateException("附件内容保存失败", exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("附件内容读取失败", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("附件内容删除失败", exception);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("附件存储键不能为空");
        }
        Path target = root.resolve(storageKey.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("附件存储键不合法");
        }
        return target;
    }
}
