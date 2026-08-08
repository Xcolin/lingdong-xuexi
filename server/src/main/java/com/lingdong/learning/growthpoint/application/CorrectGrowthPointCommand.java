package com.lingdong.learning.growthpoint.application;

/** 主家长积分纠错命令。 */
public record CorrectGrowthPointCommand(Long originalLedgerId, String reason) {
}
