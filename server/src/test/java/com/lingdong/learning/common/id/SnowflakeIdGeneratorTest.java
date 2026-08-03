package com.lingdong.learning.common.id;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesDistinctNineteenDigitIdsWithinOneMillisecond() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 2, () -> 1_753_840_000_000L);

        long first = generator.nextId();
        long second = generator.nextId();

        assertThat(Long.toString(first)).hasSize(19);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void rejectsOutOfRangeNodeConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SnowflakeIdGenerator(32, 0, () -> 1_753_840_000_000L)
        );
    }

    @Test
    void rejectsClockRollback() {
        AtomicLong time = new AtomicLong(1_753_840_000_000L);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, time::get);
        generator.nextId();
        time.decrementAndGet();

        assertThatIllegalStateException().isThrownBy(generator::nextId);
    }
}
