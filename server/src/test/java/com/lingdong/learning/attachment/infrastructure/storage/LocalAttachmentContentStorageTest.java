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
}
