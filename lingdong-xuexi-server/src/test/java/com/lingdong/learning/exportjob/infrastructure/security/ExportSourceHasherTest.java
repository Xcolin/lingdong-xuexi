package com.lingdong.learning.exportjob.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportSourceHasherTest {
    @Test
    void createsStableLowercaseHmacWithoutRetainingSource() {
        ExportSourceHasher hasher = new ExportSourceHasher("0123456789abcdef0123456789abcdef");

        String first = hasher.hash(" 192.168.1.10 ");
        String second = hasher.hash("192.168.1.10");

        assertThat(first).isEqualTo(second).hasSize(64)
                .matches("[0-9a-f]{64}")
                .doesNotContain("192.168.1.10");
    }

    @Test
    void rejectsMissingOrShortSecret() {
        assertThatThrownBy(() -> new ExportSourceHasher("short"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
        ExportSourceHasher hasher = new ExportSourceHasher("0123456789abcdef0123456789abcdef");
        assertThatThrownBy(() -> hasher.hash(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("来源");
    }
}
