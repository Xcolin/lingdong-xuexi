package com.lingdong.learning.attendance.domain;

/** 原始需求规定的人工考勤结果；没有记录不等于缺勤。 */
public enum AttendanceStatus { NORMAL, LATE, EARLY_LEAVE, ABSENT, LEAVE }
