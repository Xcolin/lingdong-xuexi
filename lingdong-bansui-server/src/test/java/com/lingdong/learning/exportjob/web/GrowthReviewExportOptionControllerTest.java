package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.GrowthReviewExportAccessService;
import com.lingdong.learning.exportjob.application.GrowthReviewPdfTemplateService;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class GrowthReviewExportOptionControllerTest {
    @Test void checksCreationScopeBeforeReadingTemplates() {
        var access = mock(GrowthReviewExportAccessService.class);
        var templates = mock(GrowthReviewPdfTemplateService.class);
        var controller = new GrowthReviewExportOptionController(access, templates);
        doThrow(new SystemOperationAccessDeniedException("无权访问")).when(access).requireCreate(null, 19L);
        assertThatThrownBy(() -> controller.options(null, 19L)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(templates);
    }
}
