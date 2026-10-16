package com.lingdong.learning.learningtask.domain;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskRecurrenceCalendarTest {
 @Test void omittedFrequencyPreservesDaily(){assertEquals(LocalDate.of(2026,10,15),TaskRecurrenceCalendar.nextAfter(LocalDate.of(2026,10,14),null,List.of()));}
 @Test void weeklyAndCustomSkipUnselectedDays(){var monday=LocalDate.of(2026,10,12);assertEquals(monday.plusWeeks(1),TaskRecurrenceCalendar.nextAfter(monday,"WEEKLY",List.of(1)));assertEquals(monday.plusDays(2),TaskRecurrenceCalendar.nextAfter(monday,"CUSTOM",List.of(1,3,5)));}
 @Test void invalidScheduleIsRejected(){assertThrows(IllegalArgumentException.class,()->TaskRecurrenceCalendar.nextAfter(LocalDate.now(),"CUSTOM",List.of()));assertThrows(IllegalArgumentException.class,()->TaskRecurrenceCalendar.nextAfter(LocalDate.now(),"WEEKLY",List.of(1,2)));assertThrows(IllegalArgumentException.class,()->TaskRecurrenceCalendar.nextAfter(LocalDate.now(),"CUSTOM",List.of(8)));}
 @Test void dailyInvalidWeekdaysAreRejected(){assertThrows(IllegalArgumentException.class,()->TaskRecurrenceCalendar.weekdays("DAILY",List.of(0,8)));}
 @Test void stoppedOrExpiredScheduleIsNotDue(){var date=LocalDate.of(2026,10,14);assertFalse(TaskRecurrenceCalendar.isDue(date,date,date,LearningTaskRecurrenceStatus.STOPPED));assertFalse(TaskRecurrenceCalendar.isDue(date.plusDays(1),date,date,LearningTaskRecurrenceStatus.ACTIVE));assertTrue(TaskRecurrenceCalendar.isDue(date,date,date,LearningTaskRecurrenceStatus.ACTIVE));}
}
