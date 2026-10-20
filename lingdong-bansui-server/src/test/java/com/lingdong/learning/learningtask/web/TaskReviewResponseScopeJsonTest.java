package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskReviewResponseScopeJsonTest {
    @Test void scopeIdsAreSerializedAsStringsForMiniappActivityFiltering() throws Exception {
        var response = new TaskReviewResponse(1874244142494692001L, 1874244142494692002L,
                "任务", 10, 1874244142494692003L, "孩子", LearningTaskSourceType.ORGANIZATION,
                1874244142494692004L, "机构", "PENDING_REVIEW", 1874244142494692005L,
                "审核人", java.time.LocalDate.of(2026, 10, 18), "三年级1班", "CAMPUS_LIFE", null);
        // 与产品链路对齐：注册 JSR310 模块支持 LocalDate（产品走 Spring Boot 自动配置的 ObjectMapper）
        var mapper = new ObjectMapper().findAndRegisterModules();
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertEquals("1874244142494692003", json.get("studentId").textValue());
        assertEquals("1874244142494692004", json.get("sourceOrganizationId").textValue());
        assertEquals("1874244142494692002", json.get("taskId").textValue());
    }
}
