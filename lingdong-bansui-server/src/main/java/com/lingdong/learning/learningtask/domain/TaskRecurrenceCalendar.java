package com.lingdong.learning.learningtask.domain;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
/** ISO weekday calendar shared by validation and recurring generation. */
public final class TaskRecurrenceCalendar {
 private TaskRecurrenceCalendar(){}
 public static String frequency(String value){String v=value==null?"DAILY":value;if(!Set.of("DAILY","WEEKLY","CUSTOM").contains(v))throw new IllegalArgumentException("不支持的重复频率");return v;}
 public static List<Integer> weekdays(String value,List<Integer> days){String v=frequency(value);if(days!=null&&days.stream().anyMatch(d->d==null||d<1||d>7))throw new IllegalArgumentException("请选择有效星期");if("DAILY".equals(v))return List.of();if(days==null||days.isEmpty()||days.stream().anyMatch(d->d==null||d<1||d>7))throw new IllegalArgumentException("请选择有效星期");var normalized=days.stream().distinct().sorted().toList();if("WEEKLY".equals(v)&&normalized.size()!=1)throw new IllegalArgumentException("每周任务请选择一个星期");return normalized;}
 public static LocalDate nextAfter(LocalDate date,String frequency,List<Integer> days){String f=frequency(frequency);var selected=weekdays(f,days);LocalDate next=date.plusDays(1);if("DAILY".equals(f))return next;while(!selected.contains(next.getDayOfWeek().getValue()))next=next.plusDays(1);return next;}
 public static boolean isDue(LocalDate generation,LocalDate businessDate,LocalDate end,LearningTaskRecurrenceStatus status){return status==LearningTaskRecurrenceStatus.ACTIVE&&!generation.isAfter(businessDate)&&(end==null||!generation.isAfter(end));}
}
