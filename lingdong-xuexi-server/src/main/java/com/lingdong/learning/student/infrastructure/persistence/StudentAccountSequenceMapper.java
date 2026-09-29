package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.domain.StudentAccountSequence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 学生账号年度流水的行锁和条件更新边界。 */
@Mapper
public interface StudentAccountSequenceMapper {
    StudentAccountSequence findByYearForUpdate(@Param("sequenceYear") int sequenceYear);

    int insert(@Param("sequence") StudentAccountSequence sequence);

    int updateCurrentValue(
            @Param("id") Long id,
            @Param("expectedValue") int expectedValue,
            @Param("nextValue") int nextValue
    );
}
