package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttachmentRetentionCleanupServiceTest {
    private ManagedFileMapper fileMapper;
    private AttachmentContentStorage contentStorage;
    private AttachmentRetentionCleanupService service;

    @BeforeEach
    void setUp() {
        fileMapper = mock(ManagedFileMapper.class);
        contentStorage = mock(AttachmentContentStorage.class);
        service = new AttachmentRetentionCleanupService(fileMapper, contentStorage, 100);
    }

    @Test
    void deletesResidualContentOnlyForRetiredFiles() {
        when(fileMapper.findRetiredBatch(100)).thenReturn(List.of(
                retired(1874244142494647001L, "attachment/2026/a"),
                retired(1874244142494647002L, "attachment/2026/b")
        ));

        int cleaned = service.cleanupResidualContent();

        assertThat(cleaned).isEqualTo(2);
        verify(contentStorage).delete("attachment/2026/a");
        verify(contentStorage).delete("attachment/2026/b");
    }

    @Test
    void continuesWithRemainingFilesWhenSingleDeleteFails() {
        when(fileMapper.findRetiredBatch(100)).thenReturn(List.of(
                retired(1874244142494647001L, "attachment/2026/broken"),
                retired(1874244142494647002L, "attachment/2026/next")
        ));
        doThrow(new IllegalStateException("附件内容删除失败")).when(contentStorage).delete("attachment/2026/broken");

        int cleaned = service.cleanupResidualContent();

        assertThat(cleaned).isEqualTo(1);
        verify(contentStorage).delete("attachment/2026/next");
    }

    @Test
    void returnsZeroWithoutRetiredFiles() {
        when(fileMapper.findRetiredBatch(100)).thenReturn(List.of());

        assertThat(service.cleanupResidualContent()).isZero();
        verifyNoInteractions(contentStorage);
    }

    @Test
    void rejectsInvalidBatchSize() {
        assertThatThrownBy(() -> new AttachmentRetentionCleanupService(fileMapper, contentStorage, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("批大小必须为1至1000");
        assertThatThrownBy(() -> new AttachmentRetentionCleanupService(fileMapper, contentStorage, 1001))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(fileMapper, contentStorage);
    }

    private ManagedFileRecord retired(Long id, String storageKey) {
        return new ManagedFileRecord(id, storageKey, "proof.png", "png", "image/png", 1024L,
                1874244142494647101L, "LEARNING_TASK_CHECKIN", "IMAGE", null,
                FileStatus.RETIRED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }
}
