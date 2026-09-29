package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedAttachmentContentServiceTest {
    private AttachmentFileApplicationService fileService;
    private AttachmentContentStorage contentStorage;
    private ManagedAttachmentContentService service;

    @BeforeEach
    void setUp() {
        fileService = mock(AttachmentFileApplicationService.class);
        contentStorage = mock(AttachmentContentStorage.class);
        service = new ManagedAttachmentContentService(fileService, contentStorage);
    }

    @Test
    void storesContentAndCompletesMetadataWithSha256() {
        byte[] content = "template-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ManagedFile registered = file(FileStatus.UPLOADING, null);
        ManagedFile completed = file(
                FileStatus.AVAILABLE,
                "75c43d77aa45740de778d7b86d4c5ab01de45fb049cb89d31ab027eb1a74a5fb"
        );
        when(fileService.registerUpload(any())).thenReturn(registered);
        when(fileService.completeUpload(any())).thenReturn(completed);

        ManagedFile result = service.store(
                1874244142494647101L, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE",
                "students.xlsx", "application/octet-stream", content
        );

        assertThat(result).isEqualTo(completed);
        verify(contentStorage).store("attachment/test/template", content);
        ArgumentCaptor<CompleteAttachmentUploadCommand> captor =
                ArgumentCaptor.forClass(CompleteAttachmentUploadCommand.class);
        verify(fileService).completeUpload(captor.capture());
        assertThat(captor.getValue().contentSha256()).matches("[0-9a-f]{64}");
        assertThat(captor.getValue().sizeBytes()).isEqualTo(content.length);
    }

    @Test
    void removesStoredContentWhenCompletionFails() {
        byte[] content = {1, 2, 3};
        ManagedFile registered = file(FileStatus.UPLOADING, null);
        when(fileService.registerUpload(any())).thenReturn(registered);
        when(fileService.completeUpload(any())).thenThrow(new IllegalStateException("完成失败"));

        assertThatThrownBy(() -> service.store(
                1874244142494647101L, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE",
                "students.xlsx", "application/octet-stream", content
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("完成失败");

        verify(contentStorage).delete("attachment/test/template");
    }

    @Test
    void preservesOriginalFailureWhenContentCleanupAlsoFails() {
        byte[] content = {1, 2, 3};
        ManagedFile registered = file(FileStatus.UPLOADING, null);
        IllegalStateException completionFailure = new IllegalStateException("完成失败");
        IllegalStateException cleanupFailure = new IllegalStateException("清理失败");
        when(fileService.registerUpload(any())).thenReturn(registered);
        when(fileService.completeUpload(any())).thenThrow(completionFailure);
        doThrow(cleanupFailure).when(contentStorage).delete("attachment/test/template");

        assertThatThrownBy(() -> service.store(
                1874244142494647101L, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE",
                "students.xlsx", "application/octet-stream", content
        )).isSameAs(completionFailure)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .containsExactly(cleanupFailure));
    }

    @Test
    void readsOnlyAvailableManagedContent() {
        byte[] content = {4, 5, 6};
        when(fileService.findFile(1874244142494647102L))
                .thenReturn(file(FileStatus.AVAILABLE, "hash"));
        when(contentStorage.read("attachment/test/template")).thenReturn(content);

        AttachmentContentView result = service.read(1874244142494647102L);

        assertThat(result.originalName()).isEqualTo("students.xlsx");
        assertThat(result.content()).containsExactly(content);
    }

    @Test
    void rejectsEmptyContentBeforeRegisteringMetadata() {
        assertThatThrownBy(() -> service.store(
                1874244142494647101L, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE",
                "students.xlsx", "application/octet-stream", new byte[0]
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    private ManagedFile file(FileStatus status, String contentSha256) {
        return new ManagedFile(
                1874244142494647102L,
                "attachment/test/template",
                "students.xlsx",
                "xlsx",
                "application/octet-stream",
                16L,
                1874244142494647101L,
                "IMPORT_EXPORT_TEMPLATE",
                "TEMPLATE_FILE",
                contentSha256,
                status
        );
    }
}
