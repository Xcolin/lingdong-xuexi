package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.exportjob.application.GrowthReviewExportApplicationService;
import com.lingdong.learning.exportjob.application.GrowthReviewExportHistoryService;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer.Template;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP 绑定与响应契约专项；身份解析使用测试替身，不代替认证安全测试。 */
class GrowthReviewExportControllerTest {
    private static final String BODY = """
            {"studentId":"1874244142494650102","reviewId":"1874244142494650151",
             "templateId":"1874244142494650105","mode":"DETAILED","reason":"导出日报"}
            """;

    @Test void createsWithServerPrincipalAndExposesOnlyPublicJobFields() throws Exception {
        var application = mock(GrowthReviewExportApplicationService.class);
        var user = new AuthenticatedUser(1874244142494650101L, 1874244142494650109L, "parent", "家长", AuthClientType.WEB, List.of("PARENT"));
        var job = mock(ExportJobRecord.class);
        when(job.id()).thenReturn(1874244142494650180L);
        when(job.exportType()).thenReturn(ExportJobType.GROWTH_REVIEW_PDF);
        when(job.status()).thenReturn(ExportJobStatus.QUEUED);
        when(job.filterSnapshot()).thenReturn("private-report");
        when(application.create(eq(user), any(), eq(1874244142494650105L), eq(Template.DETAILED), eq("导出日报"), anyString())).thenReturn(job);
        var mvc = MockMvcBuilders.standaloneSetup(new GrowthReviewExportController(application, mock(GrowthReviewExportHistoryService.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == AuthenticatedUser.class; }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) { return user; }
                }).build();
        mvc.perform(post("/api/v1/growth-review-export-jobs").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("1874244142494650180"))
                .andExpect(jsonPath("$.id").isString()).andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.filterSnapshot").doesNotExist()).andExpect(jsonPath("$.requestSourceHash").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist());
        verify(application).create(eq(user), any(), eq(1874244142494650105L), eq(Template.DETAILED), eq("导出日报"), eq("127.0.0.1"));
    }

    @Test void rejectsShortIdsMissingModeAndBlankReasonBeforeApplication() throws Exception {
        var application = mock(GrowthReviewExportApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new GrowthReviewExportController(application, mock(GrowthReviewExportHistoryService.class)))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        for (var body : List.of(BODY.replace("1874244142494650102", "123"), BODY.replace("\"DETAILED\"", "null"), BODY.replace("导出日报", ""))) {
            mvc.perform(post("/api/v1/growth-review-export-jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(application);
    }

    @Test void downloadsAttachmentWithSafeHeaders() throws Exception {
        var history = mock(GrowthReviewExportHistoryService.class);
        byte[] bytes = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(history.download(isNull(), eq(1874244142494650180L))).thenReturn(
                new com.lingdong.learning.attachment.application.AttachmentContentView("中文复盘.pdf", "application/pdf", bytes));
        var mvc = MockMvcBuilders.standaloneSetup(new GrowthReviewExportController(mock(GrowthReviewExportApplicationService.class), history))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/growth-review-export-jobs/1874244142494650180/download"))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        verify(history).download(null, 1874244142494650180L);
    }

    @Test void refusesMixedSingleAndRangeSelection() {
        var request = new CreateGrowthReviewExportRequest("1874244142494650102", "1874244142494650151",
                com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType.DAY,
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 2), "1874244142494650105", Template.SIMPLE, "导出");
        assertThatThrownBy(request::selection).isInstanceOf(IllegalArgumentException.class);
    }
}
