package com.lingdong.learning.growthpoint.application;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSubscription;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDeliveryMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSubscriptionMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.stream.LongStream;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class GrowthReviewSubscriptionBatchTest {
    private final GrowthReviewSubscriptionMapper subscriptions = mock(GrowthReviewSubscriptionMapper.class);
    private final GrowthReviewDeliveryMapper deliveries = mock(GrowthReviewDeliveryMapper.class);
    private final GrowthReviewSubscriptionQueueService queue = mock(GrowthReviewSubscriptionQueueService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-06T23:00:00Z"), ZoneOffset.UTC);
    private final GrowthReviewSubscriptionBatchService batch = new GrowthReviewSubscriptionBatchService(subscriptions, deliveries, queue, clock);
    @Test void convertsUtcToShanghaiAndExpiresBeforeQueueing() {
        when(subscriptions.findAfter(0, 100)).thenReturn(List.of(row(1)));
        when(queue.prepare(anyLong(), anyLong(), any())).thenReturn(1);
        assertThat(batch.process()).isEqualTo(1);
        var ordered = inOrder(deliveries, queue);
        ordered.verify(deliveries).cancelExpired(LocalDateTime.of(2026,9,7,7,0));
        ordered.verify(queue).prepare(11L, 21L, LocalDateTime.of(2026,9,7,7,0));
    }
    @Test void pagesByLastIdAndIsolatesOneFailure() {
        when(subscriptions.findAfter(0,100)).thenReturn(LongStream.rangeClosed(1,100).mapToObj(this::row).toList());
        when(subscriptions.findAfter(100,100)).thenReturn(List.of(row(101)));
        when(queue.prepare(anyLong(), anyLong(), any())).thenReturn(1);
        when(queue.prepare(eq(11L), eq(21L), any())).thenThrow(new IllegalStateException("模拟单条失败"));
        assertThat(batch.process()).isEqualTo(100);
        verify(subscriptions).findAfter(100,100);
    }
    private GrowthReviewSubscription row(long id) { return new GrowthReviewSubscription(id,id+10,id+20,true,1,null); }
}
