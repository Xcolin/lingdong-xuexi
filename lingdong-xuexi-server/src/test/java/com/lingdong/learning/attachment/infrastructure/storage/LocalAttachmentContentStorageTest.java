package com.lingdong.learning.attachment.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAttachmentContentStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void storesReadsAndDeletesOnlyInsideConfiguredRoot() {
        LocalAttachmentContentStorage storage =
                new LocalAttachmentContentStorage(tempDirectory.toString());
        byte[] content = {1, 2, 3};

        storage.store("attachment/2026/file", content);
        assertThat(storage.read("attachment/2026/file")).containsExactly(content);
        storage.delete("attachment/2026/file");
        assertThatThrownBy(() -> storage.read("attachment/2026/file"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> storage.store("../outside", content))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 持久化语义：存储根目录固定后，应用重启（重建实例）后写入内容仍可读。 */
    @Test
    void keepsContentReadableAcrossRestartWhenRootIsStable() {
        byte[] content = {(byte) 0x89, 'P', 'N', 'G'};
        LocalAttachmentContentStorage before = new LocalAttachmentContentStorage(tempDirectory.toString());
        before.store("attachment/2026/09/restart-proof.png", content);

        // 模拟进程重启：重新构造同一根目录的存储实例，不依赖内存状态。
        LocalAttachmentContentStorage after = new LocalAttachmentContentStorage(tempDirectory.toString());
        assertThat(after.read("attachment/2026/09/restart-proof.png")).containsExactly(content);
    }
}
