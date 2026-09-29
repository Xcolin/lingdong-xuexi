package com.lingdong.learning.dashboard.infrastructure.persistence;

import java.time.LocalDate;

/** 学员活跃度按日聚合行，仅含业务日与去重活跃学员数。 */
public record ActivityTrendRow(LocalDate activityDate, long activeStudents) {
}
