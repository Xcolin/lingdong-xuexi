package com.lingdong.learning.common.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Thread-safe 64-bit Snowflake identifier generator.
 *
 * <p>The bit layout is 41-bit timestamp delta, 5-bit data-center ID, 5-bit worker ID, and
 * 12-bit sequence. Database tables store the generated value as a signed {@code BIGINT}.</p>
 */
@Component
public final class SnowflakeIdGenerator implements IdGenerator {
    static final long EPOCH = 1_288_834_974_657L;

    private static final long MAX_NODE_ID = 31L;
    private static final long MAX_TIMESTAMP_DELTA = (1L << 41) - 1;
    private static final long SEQUENCE_MASK = 4_095L;
    private static final int TIMESTAMP_SHIFT = 22;
    private static final int DATACENTER_SHIFT = 17;
    private static final int WORKER_SHIFT = 12;

    private final long datacenterId;
    private final long workerId;
    private final LongSupplier timeSource;

    private long lastTimestamp = -1L;
    private long sequence;

    @Autowired
    public SnowflakeIdGenerator(
            @Value("${lingdong.id.snowflake.datacenter-id:0}") long datacenterId,
            @Value("${lingdong.id.snowflake.worker-id:0}") long workerId
    ) {
        this(datacenterId, workerId, System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long datacenterId, long workerId, LongSupplier timeSource) {
        validateNodeId("数据中心", datacenterId);
        validateNodeId("工作节点", workerId);
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        this.timeSource = Objects.requireNonNull(timeSource, "时间源不能为空");
    }

    @Override
    public synchronized long nextId() {
        long timestamp = timeSource.getAsLong();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("系统时钟回拨，拒绝生成雪花主键");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitForNextMillisecond();
            }
        } else {
            sequence = 0;
        }

        long timestampDelta = timestamp - EPOCH;
        if (timestampDelta < 0 || timestampDelta > MAX_TIMESTAMP_DELTA) {
            throw new IllegalStateException("当前时间不在雪花主键支持范围内");
        }

        lastTimestamp = timestamp;
        return (timestampDelta << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long waitForNextMillisecond() {
        long timestamp = timeSource.getAsLong();
        while (timestamp <= lastTimestamp) {
            Thread.onSpinWait();
            timestamp = timeSource.getAsLong();
        }
        return timestamp;
    }

    private static void validateNodeId(String nodeName, long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(nodeName + "标识必须在0至31之间");
        }
    }
}
