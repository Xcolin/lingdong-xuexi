package com.lingdong.learning.auth.infrastructure.security;

import com.lingdong.learning.auth.infrastructure.config.StudentLoginCodeProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentLoginCodeHasherTest {
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(
            "student-code-test-key-with-32-bytes-minimum".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void generatesFourDigitCodesIncludingLeadingZeroRange() {
        StudentLoginCodeGenerator generator = new StudentLoginCodeGenerator();

        for (int index = 0; index < 500; index++) {
            assertThat(generator.generate()).matches("\\d{4}");
        }
    }

    @Test
    void hashesSameCodeWithIndependentSaltsAndVerifiesInConstantTimePath() {
        StudentLoginCodeHasher hasher = new StudentLoginCodeHasher(validProperties());

        StudentLoginCodeDigest first = hasher.hash("0123");
        StudentLoginCodeDigest second = hasher.hash("0123");

        assertThat(first.keyVersion()).isEqualTo("v1");
        assertThat(first.salt()).isNotEqualTo(second.salt());
        assertThat(first.hash()).isNotEqualTo(second.hash());
        assertThat(first.hash()).doesNotContain("0123");
        assertThat(hasher.matches("0123", first.hash(), first.salt(), first.keyVersion())).isTrue();
        assertThat(hasher.matches("0124", first.hash(), first.salt(), first.keyVersion())).isFalse();
    }

    @Test
    void rejectsInvalidLoginCodeFormat() {
        StudentLoginCodeHasher hasher = new StudentLoginCodeHasher(validProperties());

        assertThatThrownBy(() -> hasher.hash("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("学生登录码必须为4位数字");
    }

    @Test
    void rejectsMissingOrWeakActiveKeyConfiguration() {
        StudentLoginCodeProperties missingKey = new StudentLoginCodeProperties();
        missingKey.setActiveKeyVersion("v1");
        missingKey.setKeys(Map.of());
        StudentLoginCodeProperties weakKey = new StudentLoginCodeProperties();
        weakKey.setActiveKeyVersion("v1");
        weakKey.setKeys(Map.of("v1", Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8))));

        assertThatThrownBy(missingKey::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("活动密钥");
        assertThatThrownBy(weakKey::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少32字节");
    }

    private StudentLoginCodeProperties validProperties() {
        StudentLoginCodeProperties properties = new StudentLoginCodeProperties();
        properties.setActiveKeyVersion("v1");
        properties.setKeys(Map.of("v1", TEST_KEY));
        properties.afterPropertiesSet();
        return properties;
    }
}
