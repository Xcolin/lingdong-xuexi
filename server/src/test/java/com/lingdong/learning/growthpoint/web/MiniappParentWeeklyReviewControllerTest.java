package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthReviewQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 新入口注册的红灯契约；对象授权和真实数据由 H2 服务专项覆盖。 */
class MiniappParentWeeklyReviewControllerTest {
    private static final long STUDENT = 1874244142494708002L, REVIEW = 1874244142494708003L, SNAPSHOT = 1874244142494708004L;
    @Test void exposesSeparateMiniappParentStudentOptions() throws Exception {
        var service = mock(GrowthReviewQueryService.class);
        when(service.findMiniappChildren(null)).thenReturn(java.util.List.of(new GrowthReviewQueryService.ChildOption(Long.toString(STUDENT), "周报孩子")));
        var mvc = MockMvcBuilders.standaloneSetup(new GrowthReviewController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/v1/growth-reviews/miniapp/students")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(Long.toString(STUDENT)))
                .andExpect(jsonPath("$[0].studentId").isString())
                .andExpect(jsonPath("$[0].studentName").value("周报孩子"));
    }

    @Test void paginationCannotSelectAnotherPeriodAndUsesExistingDetailContract() throws Exception {
        var service = mock(GrowthReviewQueryService.class);
        when(service.findMiniappChildWeeklyReviews(null, STUDENT, 1, 20)).thenReturn(
                new com.lingdong.learning.growthpoint.application.GrowthReviewPage(java.util.List.of(), 1, 20, 0));
        var date = java.time.LocalDate.of(2026, 9, 7);
        when(service.findMiniappChildWeeklyReview(null, STUDENT, REVIEW)).thenReturn(
                new com.lingdong.learning.growthpoint.application.GrowthReviewDetailView(REVIEW, STUDENT, "周报孩子",
                        com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType.WEEK, date, date.plusDays(6),
                        SNAPSHOT, 1, 4, 2, 1, 1, 0, new java.math.BigDecimal("0.6667"), 12, 1,
                        date.atStartOfDay(), date.atStartOfDay(), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new GrowthReviewController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/v1/growth-reviews/miniapp/students/" + STUDENT).param("periodType", "DAY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.pageSize").value(20));
        verify(service).findMiniappChildWeeklyReviews(null, STUDENT, 1, 20);
        mvc.perform(get("/api/v1/growth-reviews/miniapp/students/" + STUDENT + "/" + REVIEW))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewId").value(Long.toString(REVIEW)))
                .andExpect(jsonPath("$.studentId").isString()).andExpect(jsonPath("$.snapshotId").isString())
                .andExpect(jsonPath("$.periodType").value("WEEK"))
                .andExpect(jsonPath("$.categories").isArray()).andExpect(jsonPath("$.dailyTrends").isArray());
    }
}
