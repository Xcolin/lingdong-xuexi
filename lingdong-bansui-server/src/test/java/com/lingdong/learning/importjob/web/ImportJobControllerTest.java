package com.lingdong.learning.importjob.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.importjob.application.ImportJobApplicationService;
import com.lingdong.learning.importjob.application.ImportJobQueryService;
import com.lingdong.learning.importjob.application.ImportJobOptionService;
import com.lingdong.learning.importjob.application.ImportJobView;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportJobControllerTest {
    @Test
    void createSerializesSnowflakeIdentifiersAsStrings() {
        ImportJobApplicationService applicationService = mock(ImportJobApplicationService.class);
        ImportJobQueryService queryService = mock(ImportJobQueryService.class);
        ImportJobOptionService optionService = mock(ImportJobOptionService.class);
        ImportJobController controller = new ImportJobController(
                applicationService, queryService, optionService);
        AuthenticatedUser user = new AuthenticatedUser(
                1874244142494646900L, 2L, "admin", "管理员", AuthClientType.WEB, List.of());
        ImportJobView view = view();
        when(applicationService.create(org.mockito.ArgumentMatchers.any())).thenReturn(view);
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

        ImportJobResponse response = controller.create(user, view.templateId(), null, file);

        assertThat(response.id()).isEqualTo("1874244142494646901");
        assertThat(response.templateId()).isEqualTo("1874244142494646902");
        assertThat(response.sourceFileId()).isEqualTo("1874244142494646903");
    }

    private ImportJobView view() {
        LocalDateTime now = LocalDateTime.now();
        return new ImportJobView(
                1874244142494646901L, "IMP-1874244142494646901",
                1874244142494646902L, "V1", "学生模板", "[]",
                1874244142494646903L, null, 1874244142494646900L, null,
                ImportJobStatus.QUEUED, 0L, null, null,
                0, 0, 0, 0, now, null, null, now, now
        );
    }
}
