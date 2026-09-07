package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentImportCredentialServiceTest {
    private static final long USER_ID = 1874244142494647071L;
    private static final long EXECUTION_ID = 1874244142494647072L;
    private static final long FILE_ID = 1874244142494647073L;
    private static final String KEY = Base64.getEncoder().encodeToString(
            "student-import-test-key-32-byte!".getBytes(StandardCharsets.UTF_8));

    @Test
    void createsEncryptedUnifiedAttachmentAndConsumesDownloadOnlyOnce() throws Exception {
        StudentImportExecutionMapper executionMapper = mock(StudentImportExecutionMapper.class);
        StudentImportRowMapper rowMapper = mock(StudentImportRowMapper.class);
        ManagedAttachmentContentService contentService = mock(ManagedAttachmentContentService.class);
        AttachmentFileApplicationService fileService = mock(AttachmentFileApplicationService.class);
        StudentImportAccessService accessService = mock(StudentImportAccessService.class);
        StudentCredentialCipher cipher = new StudentCredentialCipher("v1", KEY);
        StudentImportCredentialService service = new StudentImportCredentialService(
                executionMapper, rowMapper, contentService, fileService, accessService,
                cipher, new StudentCredentialWorkbookWriter(), Duration.ofHours(24));
        EncryptedStudentCredential rowCredential = cipher.encrypt(
                "2468", StudentImportRowProcessor.context(EXECUTION_ID, 2));
        when(rowMapper.countByExecutionIdAndStatus(EXECUTION_ID, StudentImportRowStatus.SUCCEEDED))
                .thenReturn(1L);
        when(rowMapper.countByExecutionIdAndStatus(EXECUTION_ID, StudentImportRowStatus.FAILED))
                .thenReturn(0L);
        when(rowMapper.findByExecutionIdAndStatus(
                EXECUTION_ID, StudentImportRowStatus.SUCCEEDED, 0, 1))
                .thenReturn(List.of(successRow(rowCredential)));
        ManagedFile managedFile = new ManagedFile(
                FILE_ID, "credential/storage", "credential.enc", "enc",
                "application/octet-stream", 100, USER_ID, "STUDENT_IMPORT",
                "INITIAL_CREDENTIAL", "0".repeat(64), FileStatus.AVAILABLE);
        when(contentService.store(any(), any(), any(), any(), any(), any())).thenReturn(managedFile);
        when(executionMapper.complete(any(), any(), any(), anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(1);

        service.finalizeExecution(runningExecution());

        ArgumentCaptor<byte[]> encryptedFile = ArgumentCaptor.forClass(byte[].class);
        verify(contentService).store(any(), any(), any(), any(), any(), encryptedFile.capture());
        assertThat(new String(encryptedFile.getValue(), StandardCharsets.UTF_8)).doesNotContain("2468");
        verify(rowMapper).clearCredentials(EXECUTION_ID);

        StudentImportExecutionRecord available = availableExecution();
        when(executionMapper.findById(EXECUTION_ID)).thenReturn(available);
        when(contentService.read(FILE_ID)).thenReturn(new AttachmentContentView(
                "credential.enc", "application/octet-stream", encryptedFile.getValue()));
        when(executionMapper.consumeCredential(any(), any(), any(), any())).thenReturn(1, 0);
        when(fileService.findFile(FILE_ID)).thenReturn(managedFile);

        AttachmentContentView downloaded = service.download(USER_ID, EXECUTION_ID);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(downloaded.content()))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue())
                    .isEqualTo("2468");
        }
        verify(contentService).discardContent("credential/storage");

        assertThatThrownBy(() -> service.download(USER_ID, EXECUTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能下载一次");
    }

    private StudentImportRowRecord successRow(EncryptedStudentCredential encrypted) {
        return new StudentImportRowRecord(
                1874244142494647074L, EXECUTION_ID, 2, StudentImportRowStatus.SUCCEEDED,
                1874244142494647075L, "12345678", null, null,
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), 1, null, null);
    }

    private StudentImportExecutionRecord runningExecution() {
        LocalDateTime now = LocalDateTime.now();
        return new StudentImportExecutionRecord(
                EXECUTION_ID, "SIM-1", 1L, USER_ID, 2L, null,
                StudentImportExecutionStatus.RUNNING, 1L, 1, 0, 0, 0,
                null, null, null, StudentImportCredentialStatus.NONE,
                null, null, now, now, null, now, now);
    }

    private StudentImportExecutionRecord availableExecution() {
        LocalDateTime now = LocalDateTime.now();
        return new StudentImportExecutionRecord(
                EXECUTION_ID, "SIM-1", 1L, USER_ID, 2L, null,
                StudentImportExecutionStatus.SUCCEEDED, 2L, 1, 1, 1, 0,
                null, null, FILE_ID, StudentImportCredentialStatus.AVAILABLE,
                now.plusHours(24), null, now, now, now, now, now);
    }
}
