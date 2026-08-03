package com.lingdong.learning.student.domain;

import java.time.LocalDateTime;

/** 学生8位业务账号的年度数据库流水。 */
public record StudentAccountSequence(
        Long id,
        Integer sequenceYear,
        Integer currentValue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StudentAccountSequence first(Long id, int sequenceYear) {
        return new StudentAccountSequence(id, sequenceYear, 1, null, null);
    }
}
