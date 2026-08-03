package com.lingdong.learning.student.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.student.domain.StudentAccountSequence;
import com.lingdong.learning.student.infrastructure.persistence.StudentAccountSequenceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Locale;

/** 以数据库年度流水原子分配不随家长或机构关系变化的8位学生账号。 */
@Service
public class StudentAccountAllocator {
    private static final int MAX_ANNUAL_SEQUENCE = 999_999;

    private final StudentAccountSequenceMapper sequenceMapper;
    private final IdGenerator idGenerator;

    public StudentAccountAllocator(StudentAccountSequenceMapper sequenceMapper, IdGenerator idGenerator) {
        this.sequenceMapper = sequenceMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public String allocate() {
        int sequenceYear = Year.now().getValue();
        StudentAccountSequence sequence = sequenceMapper.findByYearForUpdate(sequenceYear);
        if (sequence == null) {
            sequence = initializeSequence(sequenceYear);
            if (sequence.currentValue() == 1) {
                return formatAccount(sequenceYear, 1);
            }
        }
        return incrementAndFormat(sequence);
    }

    private StudentAccountSequence initializeSequence(int sequenceYear) {
        StudentAccountSequence firstSequence = StudentAccountSequence.first(idGenerator.nextId(), sequenceYear);
        try {
            if (sequenceMapper.insert(firstSequence) != 1) {
                throw new IllegalStateException("学生账号年度流水初始化失败");
            }
            return firstSequence;
        } catch (DuplicateKeyException exception) {
            StudentAccountSequence concurrentSequence = sequenceMapper.findByYearForUpdate(sequenceYear);
            if (concurrentSequence == null) {
                throw new IllegalStateException("学生账号年度流水并发初始化失败", exception);
            }
            return concurrentSequence;
        }
    }

    private String incrementAndFormat(StudentAccountSequence sequence) {
        if (sequence.currentValue() >= MAX_ANNUAL_SEQUENCE) {
            throw new StudentAccountSequenceExhaustedException(sequence.sequenceYear());
        }
        int nextValue = sequence.currentValue() + 1;
        if (sequenceMapper.updateCurrentValue(sequence.id(), sequence.currentValue(), nextValue) != 1) {
            throw new IllegalStateException("学生账号年度流水更新冲突");
        }
        return formatAccount(sequence.sequenceYear(), nextValue);
    }

    private String formatAccount(int sequenceYear, int sequenceValue) {
        return String.format(Locale.ROOT, "%02d%06d", sequenceYear % 100, sequenceValue);
    }
}
