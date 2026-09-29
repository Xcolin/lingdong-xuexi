package com.lingdong.learning.student.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentAccountAllocatorTest {
    @Autowired private StudentAccountAllocator allocator;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void allocatesEightDigitAccountsFromAnnualDatabaseSequence() {
        int currentYear = Year.now().getValue();

        String first = allocator.allocate();
        String second = allocator.allocate();

        assertThat(first).isEqualTo("%02d000001".formatted(currentYear % 100));
        assertThat(second).isEqualTo("%02d000002".formatted(currentYear % 100));
        assertThat(first).matches("\\d{8}");
        Long sequenceId = jdbcTemplate.queryForObject(
                "select id from auth_student_account_sequence where sequence_year = ?", Long.class, currentYear);
        assertThat(sequenceId).isBetween(1_000_000_000_000_000_000L, Long.MAX_VALUE);
    }

    @Test
    void rejectsAllocationAfterAnnualSequenceIsExhausted() {
        int exhaustedYear = Year.now().getValue();
        jdbcTemplate.update("""
                insert into auth_student_account_sequence (id, sequence_year, current_value)
                values (?, ?, ?)
                """, 1_874_244_142_494_646_500L, exhaustedYear, 999_999);

        assertThatThrownBy(allocator::allocate)
                .isInstanceOf(StudentAccountSequenceExhaustedException.class)
                .hasMessageContaining(String.valueOf(exhaustedYear));
        assertThat(jdbcTemplate.queryForObject(
                "select current_value from auth_student_account_sequence where sequence_year = ?",
                Integer.class, exhaustedYear)).isEqualTo(999_999);
    }
}
