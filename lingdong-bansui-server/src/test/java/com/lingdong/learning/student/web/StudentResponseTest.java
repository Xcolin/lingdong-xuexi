package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.student.domain.Student;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StudentResponseTest {
    @Test void enrollmentReceivesTheRealAccountIdAsAStringWithoutCredentials() throws Exception {
        var response = StudentResponse.from(Student.create(12L, "child", "G3", 9007199254740999L));
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response));
        assertThat(json.get("id").asText()).isEqualTo("12");
        assertThat(json.get("studentUserId").asText()).isEqualTo("9007199254740999");
        assertThat(json.has("password")).isFalse();
        assertThat(json.has("initialLoginCode")).isFalse();
    }
}
