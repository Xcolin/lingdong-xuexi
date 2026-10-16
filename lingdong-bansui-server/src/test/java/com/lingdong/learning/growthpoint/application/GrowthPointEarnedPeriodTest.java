package com.lingdong.learning.growthpoint.application;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;
class GrowthPointEarnedPeriodTest {
 @Test void shanghaiMondayAndMonthRolloverIgnoreHostZone() {
  var clock = Clock.fixed(Instant.parse("2026-02-01T16:01:00Z"), ZoneOffset.UTC);
  assertThat(GrowthPointQueryService.earnedPeriodStart(clock,true)).isEqualTo(LocalDateTime.of(2026,2,2,0,0));
  assertThat(GrowthPointQueryService.earnedPeriodStart(clock,false)).isEqualTo(LocalDateTime.of(2026,2,1,0,0));
  assertThat(GrowthPointQueryService.earnedPeriodEnd(clock)).isEqualTo(LocalDateTime.of(2026,2,3,0,0));
 }
 @Test void sundayStaysInPreviousWeekAndLegacyConstructorWorks() {
  var clock = Clock.fixed(Instant.parse("2026-02-01T15:59:00Z"), ZoneOffset.UTC);
  assertThat(GrowthPointQueryService.earnedPeriodStart(clock,true)).isEqualTo(LocalDateTime.of(2026,1,26,0,0));
  assertThat(new GrowthPointAccountView(1L,"student",10,5,LocalDateTime.now()).weekEarnedPoints()).isZero();
 }
}
