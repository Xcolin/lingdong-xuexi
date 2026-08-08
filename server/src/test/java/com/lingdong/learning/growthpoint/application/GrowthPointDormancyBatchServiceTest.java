package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthPointDormancyBatchServiceTest {
    @Test
    void continuesAfterOneStudentFailsAndAdvancesTheKeysetCursor() {
        GrowthPointLifecycleMapper mapper = mock(GrowthPointLifecycleMapper.class);
        GrowthPointDormancyService dormancyService = mock(GrowthPointDormancyService.class);
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        GrowthPointDormancyBatchService service = new GrowthPointDormancyBatchService(
                mapper, dormancyService, featureAccessService, clock, 2);
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 12, 0);
        when(featureAccessService.isEnabled("POINT_LIFECYCLE", null)).thenReturn(true);
        when(mapper.findDueDormancyStudentIdsAfter(0L, now, 2)).thenReturn(List.of(11L, 22L));
        when(mapper.findDueDormancyStudentIdsAfter(22L, now, 2)).thenReturn(List.of());
        when(dormancyService.processStudent(11L, now))
                .thenThrow(new IllegalStateException("模拟单学生失败"));
        when(dormancyService.processStudent(22L, now))
                .thenReturn(new GrowthPointDormancyResult(true, false, 0L, false));

        assertThat(service.processDueStudents()).isEqualTo(1);

        verify(dormancyService).processStudent(22L, now);
        verify(mapper).findDueDormancyStudentIdsAfter(22L, now, 2);
    }
}
