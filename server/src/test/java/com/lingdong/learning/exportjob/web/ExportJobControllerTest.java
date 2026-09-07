package com.lingdong.learning.exportjob.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExportJobControllerTest {
    @Test
    void serializesSnowflakeIdAsStringAndExcludesInternalSnapshots() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 15, 0);
        ExportJobRecord job = new ExportJobRecord(
                1874244142494646991L, "EXP-1874244142494646991",
                ExportJobType.GROWTH_POINT_LEDGER, 1874244142494646992L,
                "通用模板", "V1", 1874244142494646993L, 1874244142494646994L,
                null, "{\"secret\":true}", "[]", "{}", "{}", "原因", false,
                ExportJobStatus.QUEUED, 0L, null, 0L, 0L, null, null,
                "a".repeat(64), now, null, now, null, null, now, now);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        String json = objectMapper.writeValueAsString(ExportJobResponse.from(job));

        assertThat(json).contains("\"id\":\"1874244142494646991\"")
                .doesNotContain("filterSnapshot", "requestSourceHash", "secret", "storageKey");
    }
}
