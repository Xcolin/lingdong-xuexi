package com.lingdong.learning.studentimport.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StudentImportControllerTest {
    @Test
    void serializesSnowflakeIdsAsStringsWithoutCredentialInternals() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 19, 0);
        StudentImportExecutionRecord execution = new StudentImportExecutionRecord(
                1874244142494647091L, "SIM-1874244142494647091",
                1874244142494647092L, 1874244142494647093L,
                1874244142494647094L, 1874244142494647095L,
                StudentImportExecutionStatus.SUCCEEDED, 2L, 2, 2, 2, 0,
                null, null, 1874244142494647096L,
                StudentImportCredentialStatus.AVAILABLE, now.plusHours(24), null,
                now, now, now, now, now);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        String json = objectMapper.writeValueAsString(StudentImportResponse.from(execution));

        assertThat(json).contains(
                "\"id\":\"1874244142494647091\"",
                "\"validationJobId\":\"1874244142494647092\"",
                "\"organizationId\":\"1874244142494647094\"")
                .doesNotContain("credentialFileId", "ciphertext", "nonce", "keyVersion");
    }
}
