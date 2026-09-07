package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAttachmentApplicationServiceTest {
    private AttachmentFileApplicationService fileService;
    private ManagedFileMapper fileMapper;
    private FileRelationMapper relationMapper;
    private ManagedAttachmentContentService contentService;
    private FeatureAccessService featureAccessService;
    private TaskAttachmentApplicationService service;
    private AuthenticatedUser student;

    @BeforeEach
    void setUp() {
        fileService = mock(AttachmentFileApplicationService.class);
        fileMapper = mock(ManagedFileMapper.class);
        relationMapper = mock(FileRelationMapper.class);
        contentService = mock(ManagedAttachmentContentService.class);
        featureAccessService = mock(FeatureAccessService.class);
        service = new TaskAttachmentApplicationService(
                fileService, fileMapper, relationMapper, contentService, featureAccessService);
        student = new AuthenticatedUser(
                1874244142494647001L, 1874244142494647002L,
                "student", "学生", AuthClientType.MINIAPP, List.of("STUDENT"));
    }

    @Test
    void uploadsRealJpegAndPersistsSha256WithoutExposingStorageKey() {
        byte[] content = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        ManagedFile completed = file(FileStatus.AVAILABLE,
                "829b21a0693c4e12098f545e2a1e4dd0078a834f1df32f850503edb8b055f37a");
        when(contentService.store(any(), any(), any(), any(), any(), any())).thenReturn(completed);

        TaskAttachmentView result = service.upload(student, new UploadTaskAttachmentCommand(
                "LEARNING_TASK_CHECKIN", "IMAGE", "reading.jpg", "image/jpeg", content));

        assertThat(result.id()).isEqualTo(1874244142494647003L);
        assertThat(result.contentUrl()).isEqualTo(
                "/api/v1/attachments/1874244142494647003/content");
        verify(contentService).store(
                student.userId(), "LEARNING_TASK_CHECKIN", "IMAGE", "reading.jpg", "image/jpeg", content);
        verify(featureAccessService).requireEnabled("ATTACHMENT_SERVICE", null);
        verify(featureAccessService).requireEnabled("LEARNING_TASK_MANAGEMENT", null);
    }

    @Test
    void rejectsDisguisedImageBeforeMetadataRegistration() {
        assertThatThrownBy(() -> service.upload(student, new UploadTaskAttachmentCommand(
                "LEARNING_TASK_CHECKIN", "IMAGE", "fake.jpg", "image/jpeg",
                "not-image".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("真实格式");

        verify(contentService, never()).store(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateFileIdsWhenAssociatingCheckIn() {
        assertThatThrownBy(() -> service.attachToCheckIn(
                student.userId(), List.of(1L, 1L), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void blocksAttachmentOperationsAndDegradesBusinessCollectionWhenServiceIsDisabled() {
        doThrow(new FeatureDisabledException("ATTACHMENT_SERVICE"))
                .when(featureAccessService).requireEnabled("ATTACHMENT_SERVICE", null);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(false);
        UploadTaskAttachmentCommand upload = new UploadTaskAttachmentCommand(
                "LEARNING_TASK_CHECKIN", "IMAGE", "reading.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        assertThatThrownBy(() -> service.upload(student, upload))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("ATTACHMENT_SERVICE");
        assertThatThrownBy(() -> service.attachToCheckIn(student.userId(), List.of(1L), 2L))
                .isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.findMetadata(student, 1L))
                .isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.readContent(student, 1L))
                .isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.deleteUnattached(student, 1L))
                .isInstanceOf(FeatureDisabledException.class);
        assertThat(service.findByCheckInId(2L)).isEmpty();

        verify(fileMapper, never()).findById(any());
        verify(relationMapper, never()).findActiveFilesByBusiness(any(), any(), any());
    }

    @Test
    void stillHonorsLearningTaskToggleAfterAttachmentServiceIsEnabled() {
        doThrow(new FeatureDisabledException("LEARNING_TASK_MANAGEMENT"))
                .when(featureAccessService).requireEnabled("LEARNING_TASK_MANAGEMENT", null);

        assertThatThrownBy(() -> service.upload(student, new UploadTaskAttachmentCommand(
                "LEARNING_TASK_CHECKIN", "IMAGE", "reading.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("LEARNING_TASK_MANAGEMENT");

        verify(featureAccessService).requireEnabled("ATTACHMENT_SERVICE", null);
    }

    private ManagedFile file(FileStatus status, String contentSha256) {
        return new ManagedFile(
                1874244142494647003L, "attachment/test/reading", "reading.jpg", "jpg",
                "image/jpeg", 4L, student.userId(), "LEARNING_TASK_CHECKIN", "IMAGE",
                contentSha256, status);
    }
}
