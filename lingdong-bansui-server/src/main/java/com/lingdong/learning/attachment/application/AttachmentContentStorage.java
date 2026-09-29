package com.lingdong.learning.attachment.application;

/** 附件内容存储端口，业务层不依赖具体文件系统或对象存储供应商。 */
public interface AttachmentContentStorage {
    void store(String storageKey, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
